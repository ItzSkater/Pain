package tech.pain.base.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Живое распознавание песни: внешний python-хелпер слушает системный звук
 * (монитор дефолтного выхода) и присылает распознанные фразы JSON-строками.
 * Название трека берётся из MPRIS через playerctl. Ничего не сохраняется.
 */
public final class LyricsService {
   private static final Path DIR = Path.of(System.getProperty("user.home"), ".config", "pain", "lyrics");
   private static final long LINE_TTL = 7000L;

   private static Process process;
   private static volatile Process bootProcess;
   private static volatile long generation;
   private static Thread readerThread;
   private static Thread trackThread;
   private static volatile boolean running;
   private static volatile String line = "";
   private static volatile boolean lineFinal = true;
   private static volatile long lineTime;
   private static volatile String track = "";
   private static volatile boolean playing;
   private static volatile boolean playerKnown;
   private static volatile String status = "";
   private static volatile String error;
   private static volatile String trackKey = "";
   private static volatile String trackArtist = "";
   private static volatile String trackTitle = "";
   private static volatile long trackChangedAt;
   private static volatile long positionMs;
   private static volatile long positionAt;
   private static volatile long lengthMs;
   private static volatile List<SyncedLyrics.Line> synced = List.of();
   private static volatile int syncedState;
   private static volatile boolean useSynced = true;
   private static volatile long offsetMs;
   private static volatile String lastCommand = "";
   private static volatile float windowSec = 5.0F;

   private static final java.util.concurrent.ConcurrentLinkedQueue<String> DEBUG = new java.util.concurrent.ConcurrentLinkedQueue<>();
   private static volatile String lastStatus = "";

   private static volatile boolean crashed;

   static {
      Runtime.getRuntime().addShutdownHook(new Thread(LyricsService::stop, "Pain-Lyrics-Shutdown"));
   }

   private LyricsService() {
   }

   /** Хелпер упал сам (не мы остановили) — модуль перезапустит. */
   public static boolean takeCrashed() {
      boolean c = crashed;
      crashed = false;
      return c;
   }

   /** Убиваем хелперы, оставшиеся от прошлых запусков игры. */
   private static void killStale() {
      ProcessHandle.allProcesses().forEach(ph -> {
         String cmd = ph.info().commandLine().orElse("");
         if (cmd.contains("pain/lyrics/transcribe.py") && ph.pid() != ProcessHandle.current().pid()) {
            ph.descendants().forEach(ProcessHandle::destroy);
            ph.destroy();
            debug("убит старый хелпер pid=" + ph.pid());
         }
      });
   }

   static void debug(String msg) {
      if (DEBUG.size() < 200) {
         DEBUG.add(msg);
      }
   }

   /** Сообщения для дебага в чат (забирает модуль на главном потоке). */
   public static String pollDebug() {
      return DEBUG.poll();
   }

   private static void setStatus(String st) {
      status = st;
      if (!st.equals(lastStatus)) {
         lastStatus = st;
         debug("статус: " + st);
      }
   }

   public static synchronized void start(String engine, String lang, String model, float window) {
      stop();
      running = true;
      error = null;
      setStatus("запуск");
      line = "";
      long session = ++generation;
      windowSec = window;
      trackKey = "";
      lastCommand = "";
      Thread boot = new Thread(() -> boot(session, engine, lang, model, window), "Pain-Lyrics-Boot");
      boot.setDaemon(true);
      boot.start();
      trackThread = new Thread(LyricsService::trackLoop, "Pain-Lyrics-Track");
      trackThread.setDaemon(true);
      trackThread.start();
   }

   private static void boot(long session, String engine, String lang, String model, float window) {
      try {
         Files.createDirectories(DIR);
         Path script = DIR.resolve("transcribe.py");

         try (InputStream in = LyricsService.class.getResourceAsStream("/assets/pain/lyrics/transcribe.py")) {
            if (in == null) {
               throw new IllegalStateException("transcribe.py не найден в jar");
            }

            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
         }

         killStale();
         if (!ensureEnvironment(session, engine)) {
            return;
         }

         if (!running || session != generation) {
            return;
         }

         List<String> cmd = new ArrayList<>();
         cmd.add(python());
         cmd.add("-u");
         cmd.add(script.toString());
         cmd.add("--engine");
         cmd.add(engine);
         cmd.add("--lang");
         cmd.add(lang);
         cmd.add("--model");
         cmd.add(model);
         cmd.add("--window");
         cmd.add(String.valueOf(window));
         ProcessBuilder pb = new ProcessBuilder(cmd);
         pb.environment().put("HF_HUB_DISABLE_PROGRESS_BARS", "1");
         pb.environment().put("HF_HUB_ETAG_TIMEOUT", "60");
         pb.environment().put("HF_HUB_DOWNLOAD_TIMEOUT", "120");
         pb.redirectError(ProcessBuilder.Redirect.appendTo(DIR.resolve("helper.log").toFile()));
         setStatus("загрузка модели " + model);
         Process proc = pb.start();
         synchronized (LyricsService.class) {
            if (!running || session != generation) {
               proc.destroy();
               return;
            }

            process = proc;
         }

         debug("хелпер запущен pid=" + proc.pid() + " " + String.join(" ", cmd.subList(2, cmd.size())));
         readerThread = new Thread(() -> readLoop(proc), "Pain-Lyrics-Reader");
         readerThread.setDaemon(true);
         readerThread.start();
      } catch (Exception e) {
         error = "не удалось запустить: " + e.getMessage();
         setStatus("ошибка");
      }
   }

   /** Создаёт venv и ставит библиотеки сам — «из коробки». Повторяет при обрывах сети. */
   private static boolean ensureEnvironment(long session, String engine) throws Exception {
      Path venv = DIR.resolve("venv");
      Path py = venv.resolve("bin").resolve("python");
      String module = engine.equals("vosk") ? "vosk" : engine.equals("whispercpp") ? "numpy" : "faster_whisper";
      String[] packages = engine.equals("vosk") ? new String[]{"vosk", "numpy"} : engine.equals("whispercpp") ? new String[]{"numpy"} : new String[]{"faster-whisper", "numpy"};
      if (!Files.isExecutable(py)) {
         setStatus("создаю окружение python");
         if (exec(List.of("python3", "-m", "venv", venv.toString())) != 0) {
            error = "нужен python3 (sudo pacman -S python)";
            setStatus("ошибка");
            return false;
         }
      }

      if (exec(List.of(py.toString(), "-c", "import " + module + ", numpy")) == 0) {
         return true;
      }

      for (int attempt = 1; attempt <= 5 && running && session == generation; ++attempt) {
         setStatus("установка " + packages[0] + " (" + attempt + "/5)...");
         List<String> cmd = new ArrayList<>(List.of(py.toString(), "-m", "pip", "install", "--disable-pip-version-check", "--retries", "15", "--timeout", "60"));
         cmd.addAll(List.of(packages));
         exec(cmd);
         if (exec(List.of(py.toString(), "-c", "import " + module + ", numpy")) == 0) {
            return true;
         }

         Thread.sleep(3000L);
      }

      if (running && session == generation) {
         error = "не удалось установить " + packages[0] + " — проверь интернет, лог: ~/.config/pain/lyrics/helper.log";
         setStatus("ошибка");
      }

      return false;
   }

   private static int exec(List<String> cmd) throws Exception {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      pb.redirectOutput(ProcessBuilder.Redirect.appendTo(DIR.resolve("helper.log").toFile()));
      Process p = pb.start();
      bootProcess = p;

      try {
         return p.waitFor();
      } finally {
         bootProcess = null;
      }
   }

   public static synchronized void stop() {
      running = false;
      ++generation;
      Process boot = bootProcess;
      if (boot != null) {
         boot.descendants().forEach(ProcessHandle::destroy);
         boot.destroy();
      }

      if (process != null) {
         Process proc = process;
         proc.descendants().forEach(ProcessHandle::destroy);
         proc.destroy();

         try {
            if (!proc.waitFor(1L, TimeUnit.SECONDS)) {
               proc.descendants().forEach(ProcessHandle::destroyForcibly);
               proc.destroyForcibly();
            }
         } catch (InterruptedException ignored) {
         }

         process = null;
      }

      if (trackThread != null) {
         trackThread.interrupt();
         trackThread = null;
      }

      line = "";
      track = "";
      trackKey = "";
      synced = List.of();
      playing = false;
   }

   private static String python() {
      File venv = DIR.resolve("venv").resolve("bin").resolve("python").toFile();
      return venv.canExecute() ? venv.getAbsolutePath() : "python3";
   }

   private static void readLoop(Process proc) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
         String raw;
         while ((raw = reader.readLine()) != null) {
            raw = raw.trim();
            if (raw.isEmpty() || raw.charAt(0) != '{') {
               continue;
            }

            JsonObject obj;
            try {
               obj = JsonParser.parseString(raw).getAsJsonObject();
            } catch (Exception e) {
               continue;
            }

            if (obj.has("text")) {
               String text = obj.get("text").getAsString().trim();
               long sinceChange = System.currentTimeMillis() - trackChangedAt;
               if (!text.isEmpty() && sinceChange <= (long)(windowSec * 1000.0F) + 1200L) {
                  debug("AI отброшено (смена трека " + sinceChange + "мс назад): " + text);
               }

               if (!text.isEmpty() && sinceChange > (long)(windowSec * 1000.0F) + 1200L) {
                  debug("AI: " + text);
                  line = text;
                  lineFinal = !obj.has("final") || obj.get("final").getAsBoolean();
                  lineTime = System.currentTimeMillis();
               }
            } else if (obj.has("status")) {
               String st = obj.get("status").getAsString();
               setStatus(st.startsWith("ready") ? "слушаю..." : (st.startsWith("loading") ? "загрузка модели..." : (st.startsWith("downloading") ? "скачиваю модель..." : st)));
            } else if (obj.has("debug")) {
               debug("хелпер: " + obj.get("debug").getAsString());
            } else if (obj.has("error")) {
               debug("ОШИБКА хелпера: " + obj.get("error").getAsString());
               error = obj.get("error").getAsString();
               setStatus("ошибка");
            }
         }
      } catch (Exception ignored) {
      }

      try {
         debug("хелпер завершился, код " + proc.waitFor());
      } catch (Exception ignored) {
      }

      if (running && proc == process) {
         crashed = true;
      }

      if (running && error == null && proc == process) {
         error = "хелпер завершился, см. ~/.config/pain/lyrics/helper.log";
      }
   }

   private static void trackLoop() {
      while (running) {
         try {
            pollPlayer();
            Thread.sleep(500L);
         } catch (InterruptedException e) {
            return;
         } catch (Exception e) {
            playerKnown = false;

            try {
               Thread.sleep(3000L);
            } catch (InterruptedException ie) {
               return;
            }
         }
      }
   }

   /** Берём играющий плеер (Zen/Firefox, Spotify, mpv...), а не первый попавшийся. */
   private static void pollPlayer() throws Exception {
      Process p = new ProcessBuilder("playerctl", "-a", "metadata", "--format", "{{status}}\t{{artist}}\t{{title}}\t{{position}}\t{{mpris:length}}").redirectErrorStream(true).start();
      List<String[]> players = new ArrayList<>();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
         String out;
         while ((out = r.readLine()) != null) {
            if (out.contains("\t")) {
               players.add(out.split("\t", -1));
            }
         }
      }

      p.waitFor(2L, TimeUnit.SECONDS);
      String[] chosen = null;

      for (String[] parts : players) {
         if (parts.length >= 3 && "Playing".equalsIgnoreCase(parts[0].trim())) {
            chosen = parts;
            break;
         }
      }

      if (chosen == null && !players.isEmpty()) {
         chosen = players.get(0);
      }

      if (chosen == null) {
         playerKnown = false;
         playing = false;
         track = "";
         return;
      }

      playerKnown = true;
      playing = "Playing".equalsIgnoreCase(chosen[0].trim());
      String artist = chosen.length > 1 ? chosen[1].trim() : "";
      String title = chosen.length > 2 ? chosen[2].trim() : "";
      long pos = chosen.length > 3 ? parseLong(chosen[3]) / 1000L : 0L;
      long len = chosen.length > 4 ? parseLong(chosen[4]) / 1000L : 0L;
      String key = artist + "\u0000" + title;
      if (!key.equals(trackKey)) {
         trackKey = key;
         trackArtist = artist;
         trackTitle = title;
         trackChangedAt = System.currentTimeMillis();
         line = "";
         lineTime = 0L;
         synced = List.of();
         syncedState = 1;
         debug("трек: " + (artist.isEmpty() ? "" : artist + " — ") + title + " (" + len / 1000L + "с)");
         sendHelper("reset");
         loadSynced(key, artist, title, len);
      }

      track = artist.isEmpty() ? title : (title.isEmpty() ? artist : artist + " — " + title);
      positionMs = pos;
      lengthMs = len;
      positionAt = System.currentTimeMillis();
      sendHelper(playing && (synced.isEmpty() || !useSynced) ? "resume" : "pause");
   }

   private static void loadSynced(String key, String artist, String title, long len) {
      if (!useSynced || title.isEmpty()) {
         debug("LRCLIB выключен или нет названия → распознавание (AI)");
         syncedState = 3;
         return;
      }

      Thread t = new Thread(() -> {
         for (int attempt = 0; attempt < 4 && key.equals(trackKey); ++attempt) {
            List<SyncedLyrics.Line> found = SyncedLyrics.find(artist, title, len);
            if (!key.equals(trackKey)) {
               return;
            }

            if (found != null) {
               synced = found;
               syncedState = found.isEmpty() ? 3 : 2;
               debug(found.isEmpty() ? "LRCLIB: текста нет в базе → распознавание (AI)" : "LRCLIB: найдено " + found.size() + " строк → синхр. текст (LRC)");
               return;
            }

            debug("LRCLIB: сеть недоступна, попытка " + (attempt + 1) + "/4");

            try {
               Thread.sleep(5000L);
            } catch (InterruptedException e) {
               return;
            }
         }

         if (key.equals(trackKey)) {
            syncedState = 3;
         }
      }, "Pain-Lyrics-Lrc");
      t.setDaemon(true);
      t.start();
   }

   private static long parseLong(String s) {
      try {
         return (long)Double.parseDouble(s.trim());
      } catch (Exception e) {
         return 0L;
      }
   }

   private static void sendHelper(String command) {
      Process proc = process;
      if (proc == null || command.equals(lastCommand) && !command.equals("reset")) {
         return;
      }

      try {
         proc.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
         proc.getOutputStream().flush();
         lastCommand = command;
         debug("-> хелпер: " + command);
      } catch (Exception ignored) {
      }
   }

   private static volatile String translateTarget;
   private static volatile List<SyncedLyrics.Line> prefetched = List.of();

   /** Смена языка перевода — сразу переводим весь синхронный текст пачкой. */
   public static void setTranslateTarget(String target) {
      List<SyncedLyrics.Line> lines = synced;
      boolean changed = target == null ? translateTarget != null : !target.equals(translateTarget);
      translateTarget = target;
      if (target != null && !lines.isEmpty() && (changed || prefetched != lines)) {
         prefetched = lines;
         List<String> texts = new ArrayList<>();
         lines.forEach(l -> texts.add(l.text()));
         Translator.prefetch(texts, target);
      }
   }

   public static String getTranslateTarget() {
      return translateTarget;
   }

   public static void configure(boolean synced, long offset) {
      useSynced = synced;
      offsetMs = offset;
   }

   /** Позиция трека сейчас (мс) с интерполяцией между опросами плеера. */
   public static long getPositionMs() {
      long pos = positionMs + (playing ? System.currentTimeMillis() - positionAt : 0L);
      return lengthMs > 0L ? Math.min(pos, lengthMs) : pos;
   }

   public static long getLengthMs() {
      return lengthMs;
   }

   public static boolean hasSynced() {
      return useSynced && !synced.isEmpty();
   }

   /** 1 — ищем текст, 2 — найден, 3 — нет в базе. */
   public static int getSyncedState() {
      return syncedState;
   }

   /** Индекс текущей строки синхронного текста или -1. */
   public static int getSyncedIndex() {
      List<SyncedLyrics.Line> lines = synced;
      long pos = getPositionMs() + offsetMs;
      int idx = -1;

      for (int i = 0; i < lines.size(); ++i) {
         if (lines.get(i).timeMs() <= pos) {
            idx = i;
         } else {
            break;
         }
      }

      return idx;
   }

   public static List<SyncedLyrics.Line> getSynced() {
      return synced;
   }

   public static boolean isRunning() {
      return running;
   }

   /** Текущая распознанная фраза или пустая строка, если давно тишина. */
   public static String getLine() {
      return System.currentTimeMillis() - lineTime > LINE_TTL ? "" : line;
   }

   public static boolean isLineFinal() {
      return lineFinal;
   }

   public static long getLineTime() {
      return lineTime;
   }

   public static String getTrack() {
      return track;
   }

   public static boolean isPlaying() {
      return playing;
   }

   public static boolean isPlayerKnown() {
      return playerKnown;
   }

   public static String getStatus() {
      return status;
   }

   /** Возвращает ошибку один раз (для уведомления) и сбрасывает её. */
   public static String pollError() {
      String e = error;
      error = null;
      return e;
   }
}
