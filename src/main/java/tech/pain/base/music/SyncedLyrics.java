package tech.pain.base.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Синхронизированный текст трека из открытой базы LRCLIB (lrclib.net) по названию из MPRIS.
 * Результат кэшируется на диск, чтобы при плохом интернете качать один раз.
 */
public final class SyncedLyrics {
   public record Line(long timeMs, String text) {
   }

   private static final Path CACHE = Path.of(System.getProperty("user.home"), ".config", "pain", "lyrics", "cache");
   private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();

   private SyncedLyrics() {
   }

   /** Возвращает строки или пустой список, если текста нет. null — сеть недоступна, стоит повторить позже. */
   public static List<Line> find(String artist, String title, long lengthMs) {
      String[] clean = clean(artist, title);
      String key = clean[0].toLowerCase(Locale.ROOT) + "|" + clean[1].toLowerCase(Locale.ROOT);
      Path file = CACHE.resolve(sha1("v2|" + key) + ".lrc");

      try {
         if (Files.exists(file)) {
            String cached = Files.readString(file);
            return cached.equals("NONE") ? List.of() : parse(cached);
         }
      } catch (Exception ignored) {
      }

      String lrc;
      try {
         lrc = fetch(clean[0], clean[1], lengthMs);
      } catch (Exception e) {
         return null;
      }

      try {
         Files.createDirectories(CACHE);
         Files.writeString(file, lrc == null ? "NONE" : lrc);
      } catch (Exception ignored) {
      }

      return lrc == null ? List.of() : parse(lrc);
   }

   private static String fetch(String artist, String title, long lengthMs) throws Exception {
      long durSec = lengthMs > 0L ? lengthMs / 1000L : -1L;
      if (!artist.isEmpty()) {
         String url = "https://lrclib.net/api/get?artist_name=" + enc(artist) + "&track_name=" + enc(title) + (durSec > 0L ? "&duration=" + durSec : "");
         JsonElement got = request(url);
         if (got != null && got.isJsonObject() && matches(got.getAsJsonObject(), lengthMs, artist, title)) {
            String synced = synced(got.getAsJsonObject());
            if (synced != null) {
               return synced;
            }
         }

         String byArtist = pick(request("https://lrclib.net/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist)), lengthMs, artist, title);
         if (byArtist != null) {
            return byArtist;
         }
      }

      String best = pick(request("https://lrclib.net/api/search?q=" + enc((artist + " " + title).trim())), lengthMs, artist, title);
      if (best != null || artist.isEmpty()) {
         return best;
      }

      // SoundCloud/YouTube: «артист» часто просто загрузчик — ищем только по названию
      return pick(request("https://lrclib.net/api/search?track_name=" + enc(title)), lengthMs, artist, title);
   }

   private static String pick(JsonElement results, long lengthMs, String artist, String title) {
      if (results == null || !results.isJsonArray()) {
         return null;
      }

      String best = null;
      double bestDiff = Double.MAX_VALUE;

      for (JsonElement el : results.getAsJsonArray()) {
         if (!el.isJsonObject()) {
            continue;
         }

         JsonObject obj = el.getAsJsonObject();
         String synced = synced(obj);
         if (synced == null || !matches(obj, lengthMs, artist, title)) {
            continue;
         }

         double diff = durationDiff(obj, lengthMs);
         if (diff < bestDiff) {
            bestDiff = diff;
            best = synced;
         }
      }

      return best;
   }

   /** Строгая проверка: то же название, и совпадает артист или длина почти точная. */
   private static boolean matches(JsonObject obj, long lengthMs, String artist, String title) {
      String name = norm(str(obj, "trackName"));
      String want = norm(title);
      if (name.isEmpty() || want.isEmpty()) {
         return false;
      }

      boolean titleOk = name.equals(want) || want.length() >= 4 && name.length() >= 4 && (name.startsWith(want) || want.startsWith(name));
      if (!titleOk) {
         return false;
      }

      String gotArtist = norm(str(obj, "artistName"));
      String wantArtist = norm(artist);
      boolean artistOk = !wantArtist.isEmpty() && !gotArtist.isEmpty() && (gotArtist.contains(wantArtist) || wantArtist.contains(gotArtist));
      double diff = durationDiff(obj, lengthMs);
      if (lengthMs <= 0L) {
         return artistOk;
      }

      return artistOk ? diff <= 10.0 : diff <= 2.5;
   }

   private static double durationDiff(JsonObject obj, long lengthMs) {
      if (lengthMs <= 0L || !obj.has("duration") || obj.get("duration").isJsonNull()) {
         return 0.0;
      }

      return Math.abs(obj.get("duration").getAsDouble() - (double)lengthMs / 1000.0);
   }

   private static String str(JsonObject obj, String key) {
      return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
   }

   private static String norm(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
   }

   private static String synced(JsonObject obj) {
      if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
         String s = obj.get("syncedLyrics").getAsString();
         return s.isBlank() ? null : s;
      }

      return null;
   }

   private static JsonElement request(String url) throws Exception {
      Exception last = null;

      for (int attempt = 0; attempt < 3; ++attempt) {
         try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "Pain-Client MusicLyrics (https://github.com)").GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 404) {
               return null;
            }

            if (resp.statusCode() == 200) {
               return JsonParser.parseString(resp.body());
            }
         } catch (Exception e) {
            last = e;
         }

         Thread.sleep(1500L * (long)(attempt + 1));
      }

      throw last != null ? last : new IllegalStateException("lrclib unavailable");
   }

   public static List<Line> parse(String lrc) {
      List<Line> out = new ArrayList<>();

      for (String raw : lrc.split("\\r?\\n")) {
         Matcher m = STAMP.matcher(raw);
         List<Long> times = new ArrayList<>();
         int end = 0;

         while (m.find() && m.start() == end) {
            long min = Long.parseLong(m.group(1));
            long sec = Long.parseLong(m.group(2));
            String frac = m.group(3) == null ? "0" : m.group(3);
            long ms = Long.parseLong(frac) * (frac.length() == 1 ? 100L : (frac.length() == 2 ? 10L : 1L));
            times.add(min * 60000L + sec * 1000L + ms);
            end = m.end();
         }

         if (!times.isEmpty()) {
            String text = raw.substring(end).trim();

            for (long t : times) {
               out.add(new Line(t, text));
            }
         }
      }

      out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
      return Collections.unmodifiableList(out);
   }

   /** Чистит названия из браузера/YouTube: «Artist - Title (Official Video) [4K]», «Channel - Topic». */
   static String[] clean(String artist, String title) {
      String a = artist == null ? "" : artist.trim();
      String t = title == null ? "" : title.trim();
      a = a.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").trim();
      t = t.replaceAll("\\s*[(\\[【][^)\\]】]*[)\\]】]", " ");
      t = t.replaceAll("(?i)\\b(official|music|lyric|lyrics|video|audio|visualizer|hd|4k|mv|clip|клип|премьера|extended|remix|edit|version|slowed|reverb|sped up|speed up|nightcore|bass boosted|hardtekk|phonk|mix)\\b", " ");
      t = t.replaceAll("(?i)\\s(ft\\.?|feat\\.?)\\s.*$", "");
      t = t.replaceAll("\\s*\\|.*$", "");
      int dash = indexOfDash(t);
      if (dash > 0) {
         String left = t.substring(0, dash).trim();
         String right = t.substring(dash + 1).replaceFirst("^[-–—]\\s*", "").trim();
         if (!right.isEmpty()) {
            if (a.isEmpty() || !left.toLowerCase(Locale.ROOT).contains(a.toLowerCase(Locale.ROOT)) && !a.toLowerCase(Locale.ROOT).contains(left.toLowerCase(Locale.ROOT))) {
               // в YouTube «артист» — это канал, настоящий артист слева от тире
               a = left;
            }

            t = right;
         }
      }

      t = t.replaceAll("\\s{2,}", " ").replaceAll("[-–—\\s]+$", "").trim();
      return new String[]{a, t};
   }

   private static int indexOfDash(String t) {
      for (String d : new String[]{" - ", " – ", " — "}) {
         int i = t.indexOf(d);
         if (i > 0) {
            return i + 1;
         }
      }

      return -1;
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

   private static String sha1(String s) {
      try {
         byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
         StringBuilder sb = new StringBuilder();

         for (byte b : d) {
            sb.append(String.format("%02x", b));
         }

         return sb.toString();
      } catch (Exception e) {
         return Integer.toHexString(s.hashCode());
      }
   }
}
