package tech.pain.base.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Перевод строк лирикса (Google Translate, публичный endpoint без ключа).
 * Всё асинхронно и с кэшем: get() сразу возвращает перевод или null, пока он грузится.
 */
public final class Translator {
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
   private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Pain-Lyrics-Translate");
      t.setDaemon(true);
      return t;
   });
   private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
   private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

   private Translator() {
   }

   /** Перевод из кэша или null (тогда запрос ставится в очередь). */
   public static String get(String text, String target) {
      if (text == null || text.isBlank() || target == null) {
         return null;
      }

      String key = target + "|" + text;
      String cached = CACHE.get(key);
      if (cached != null) {
         return cached;
      }

      if (PENDING.add(key)) {
         POOL.submit(() -> {
            try {
               List<String> out = request(List.of(text), target);
               if (out != null && !out.isEmpty()) {
                  CACHE.put(key, out.get(0));
               }
            } finally {
               PENDING.remove(key);
            }
         });
      }

      return null;
   }

   /** Переводит весь синхронный текст пачками, чтобы строки появлялись сразу. */
   public static void prefetch(List<String> lines, String target) {
      List<String> todo = new ArrayList<>();

      for (String line : lines) {
         if (!line.isBlank() && !CACHE.containsKey(target + "|" + line) && !todo.contains(line)) {
            todo.add(line);
         }
      }

      if (todo.isEmpty()) {
         return;
      }

      POOL.submit(() -> {
         List<String> batch = new ArrayList<>();
         int size = 0;

         for (String line : todo) {
            if (size + line.length() > 1500 && !batch.isEmpty()) {
               storeBatch(batch, target);
               batch = new ArrayList<>();
               size = 0;
            }

            batch.add(line);
            size += line.length() + 1;
         }

         if (!batch.isEmpty()) {
            storeBatch(batch, target);
         }
      });
   }

   private static void storeBatch(List<String> batch, String target) {
      List<String> out = request(batch, target);
      if (out != null && out.size() == batch.size()) {
         for (int i = 0; i < batch.size(); ++i) {
            CACHE.put(target + "|" + batch.get(i), out.get(i));
         }
      }
   }

   /** Строки склеиваются через \n, ответ режется обратно по строкам. null — ошибка сети. */
   private static List<String> request(List<String> lines, String target) {
      String joined = String.join("\n", lines);

      for (int attempt = 0; attempt < 3; ++attempt) {
         try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + target + "&dt=t&q=" + URLEncoder.encode(joined, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
               JsonArray root = JsonParser.parseString(resp.body()).getAsJsonArray();
               StringBuilder sb = new StringBuilder();

               for (JsonElement part : root.get(0).getAsJsonArray()) {
                  JsonArray arr = part.getAsJsonArray();
                  if (!arr.get(0).isJsonNull()) {
                     sb.append(arr.get(0).getAsString());
                  }
               }

               List<String> out = new ArrayList<>();

               for (String s : sb.toString().split("\n", -1)) {
                  out.add(s.trim());
               }

               while (out.size() > lines.size() && out.get(out.size() - 1).isEmpty()) {
                  out.remove(out.size() - 1);
               }

               LyricsService.debug("перевод: " + lines.size() + " строк → " + target);
               return out;
            }
         } catch (Exception e) {
            LyricsService.debug("перевод: ошибка сети (" + e.getClass().getSimpleName() + "), попытка " + (attempt + 1) + "/3");
         }

         try {
            Thread.sleep(1500L * (long)(attempt + 1));
         } catch (InterruptedException e) {
            return null;
         }
      }

      return null;
   }
}
