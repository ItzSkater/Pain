package tech.pain.base.browser;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Трансляция окна уже открытого браузера (niri + PipeWire) через python-хелпер.
 * Хелпер пишет в stdout кадры RGBA, сюда попадает только последний.
 */
public final class BrowserCast {
   public record Frame(int width, int height, byte[] rgba, long id) {
   }

   private static final Path DIR = Path.of(System.getProperty("user.home"), ".config", "pain", "browser");
   private static Process process;
   private static volatile Frame frame;
   private static volatile String status = "";
   private static volatile String error;
   private static volatile boolean running;
   private static long counter;

   static {
      Runtime.getRuntime().addShutdownHook(new Thread(BrowserCast::stop, "Pain-Browser-Shutdown"));
   }

   private BrowserCast() {
   }

   public static synchronized void start(String app, int maxWidth, int fps) {
      stop();
      running = true;
      error = null;
      frame = null;
      status = "запуск";

      try {
         Files.createDirectories(DIR);
         Path script = DIR.resolve("browsercast.py");

         try (InputStream in = BrowserCast.class.getResourceAsStream("/assets/pain/browser/browsercast.py")) {
            if (in == null) {
               throw new IllegalStateException("browsercast.py не найден в jar");
            }

            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
         }

         // системный python: там есть PyGObject и GStreamer
         ProcessBuilder pb = new ProcessBuilder("/usr/bin/python3", "-u", script.toString(), "--app", app, "--max-width", String.valueOf(maxWidth), "--fps", String.valueOf(fps));
         Process proc = pb.start();
         process = proc;
         Thread frames = new Thread(() -> readFrames(proc), "Pain-Browser-Frames");
         frames.setDaemon(true);
         frames.start();
         Thread log = new Thread(() -> readLog(proc), "Pain-Browser-Log");
         log.setDaemon(true);
         log.start();
      } catch (Exception e) {
         error = "не удалось запустить захват: " + e.getMessage();
      }
   }

   public static synchronized void stop() {
      running = false;
      if (process != null) {
         Process proc = process;
         process = null;

         try {
            proc.getOutputStream().close();
         } catch (Exception ignored) {
         }

         proc.descendants().forEach(ProcessHandle::destroy);
         proc.destroy();
      }
   }

   private static void readFrames(Process proc) {
      try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(proc.getInputStream(), 1 << 20))) {
         while (true) {
            int w = in.readInt();
            int h = in.readInt();
            int len = in.readInt();
            if (w <= 0 || h <= 0 || w > 8192 || h > 8192 || len != w * h * 4) {
               break;
            }

            byte[] data = new byte[len];
            in.readFully(data);
            frame = new Frame(w, h, data, ++counter);
         }
      } catch (Exception ignored) {
      }

      if (running && proc == process && error == null) {
         error = "трансляция остановилась";
      }
   }

   private static void readLog(Process proc) {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
         String line;
         while ((line = r.readLine()) != null) {
            if (line.startsWith("STATUS ")) {
               status = line.substring(7);
            } else if (line.startsWith("ERROR ")) {
               error = line.substring(6);
            }
         }
      } catch (Exception ignored) {
      }
   }

   public static Frame getFrame() {
      return frame;
   }

   public static String getStatus() {
      return status;
   }

   public static String pollError() {
      String e = error;
      error = null;
      return e;
   }
}
