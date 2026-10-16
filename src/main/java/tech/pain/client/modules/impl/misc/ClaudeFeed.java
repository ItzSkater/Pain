package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.MathHelper;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender2D;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Панель «что делает Claude Code». Хук Claude Code (~/.config/pain/claude-feed.py) дописывает строку
 * на каждый шаг в ~/.config/pain/claude-feed.log, а панель только читает этот файл и показывает
 * последние шаги. Из игры в Claude Code ничего не уходит.
 */
@ModuleAnnotation(
   name = "ClaudeFeed",
   category = Category.MISC,
   description = "Панель на экране: что сейчас делает Claude Code (какой файл правит, что запускает, готово ли)"
)
public final class ClaudeFeed extends Module {
   public static final ClaudeFeed INSTANCE = new ClaudeFeed();
   private static final Path LOG = Path.of(System.getProperty("user.home"), ".config", "pain", "claude-feed.log");
   private final NumberSetting lines = new NumberSetting("Строк", 5.0F, 1.0F, 10.0F, 1.0F);
   private final NumberSetting width = new NumberSetting("Ширина", 190.0F, 120.0F, 320.0F, 2.0F);
   private final NumberSetting posX = new NumberSetting("Позиция X (%)", 74.0F, 0.0F, 100.0F, 0.5F);
   private final NumberSetting posY = new NumberSetting("Позиция Y (%)", 38.0F, 0.0F, 100.0F, 0.5F);
   private final BooleanSetting autoHide = new BooleanSetting("Прятать, когда Claude молчит", true);
   private final NumberSetting hideAfter = new NumberSetting("Прятать через (сек)", 90.0F, 10.0F, 600.0F, 5.0F, (java.util.function.Supplier<Boolean>)() -> this.autoHide.isEnabled());
   // строки лога: время (сек), тип, текст; читает фоновый поток
   private volatile List<String[]> events = new ArrayList<>();
   private volatile boolean reading;
   private float shown;
   private long lastFrame;

   private ClaudeFeed() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      if (this.reading) {
         return;
      }

      this.reading = true;
      Thread thread = new Thread(() -> {
         long size = -1L;
         long modified = -1L;

         while (this.reading) {
            try {
               if (Files.exists(LOG)) {
                  long s = Files.size(LOG);
                  long m = Files.getLastModifiedTime(LOG).toMillis();
                  if (s != size || m != modified) {
                     size = s;
                     modified = m;
                     this.events = tail(s);
                  }
               }

               Thread.sleep(400L);
            } catch (Exception ignored) {
               try {
                  Thread.sleep(1000L);
               } catch (InterruptedException e) {
                  return;
               }
            }
         }
      }, "Pain-ClaudeFeed");
      thread.setDaemon(true);
      thread.start();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.reading = false;
   }

   /** Последние строки файла: читаем только хвост, файл может быть большим. */
   private static List<String[]> tail(long size) throws Exception {
      List<String[]> out = new ArrayList<>();

      try (RandomAccessFile file = new RandomAccessFile(LOG.toFile(), "r")) {
         long from = Math.max(0L, size - 6000L);
         byte[] data = new byte[(int)(size - from)];
         file.seek(from);
         file.readFully(data);
         String[] rows = new String(data, StandardCharsets.UTF_8).split("\n");

         for (int i = from > 0L ? 1 : 0; i < rows.length; ++i) {
            String[] parts = rows[i].split("\t", 3);
            if (parts.length == 3) {
               out.add(parts);
            }
         }
      }

      return out;
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      long nowMs = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (float)(nowMs - this.lastFrame) / 1000.0F);
      this.lastFrame = nowMs;
      if (mc.options.hudHidden) {
         return;
      }

      List<String[]> list = this.events;
      long now = nowMs / 1000L;
      long last = list.isEmpty() ? 0L : parse(list.get(list.size() - 1)[0]);
      String lastKind = list.isEmpty() ? "" : list.get(list.size() - 1)[1];
      boolean visible = !this.autoHide.isEnabled() || !list.isEmpty() && now - last < (long)this.hideAfter.getCurrent();
      this.shown += ((visible ? 1.0F : 0.0F) - this.shown) * Math.min(1.0F, dt * 8.0F);
      if (this.shown < 0.02F) {
         return;
      }

      CustomDrawContext ctx = event.getContext();
      float a = this.shown;
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      int count = Math.min((int)this.lines.getCurrent(), list.size());
      float w = this.width.getCurrent();
      float rowH = 11.0F;
      float h = 20.0F + Math.max(1, count) * rowH + 4.0F;
      float x = MathHelper.clamp(sw * this.posX.getCurrent() / 100.0F, 2.0F, sw - w - 2.0F);
      float y = MathHelper.clamp(sh * this.posY.getCurrent() / 100.0F, 2.0F, sh - h - 2.0F) + (1.0F - a) * 8.0F;
      ColorRGBA accent = Pain.getInstance().getThemeManager().getCurrentTheme().getColor();
      DrawUtil.drawShadow(ctx.getMatrices(), x, y, w, h, 12.0F, BorderRadius.all(7.0F), new ColorRGBA(0, 0, 0, 110.0F * a));
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x, y, w, h, BorderRadius.all(7.0F), new ColorRGBA(14, 14, 20, 200.0F * a));
      DrawUtil.drawRoundedBorder(ctx.getMatrices(), x, y, w, h, -0.5F, BorderRadius.all(7.0F), new ColorRGBA(255, 255, 255, 26.0F * a));
      // статус: работает (точка дышит), ждёт тебя, готово
      String status;
      ColorRGBA dot;
      boolean busy = now - last < 20L && !lastKind.equals("done") && !lastKind.equals("wait");
      if (list.isEmpty()) {
         status = "нет данных";
         dot = new ColorRGBA(120, 120, 130);
      } else if (lastKind.equals("wait")) {
         status = "ждёт тебя";
         dot = new ColorRGBA(255, 190, 70);
      } else if (lastKind.equals("done")) {
         status = "готово";
         dot = new ColorRGBA(110, 230, 140);
      } else if (busy) {
         status = "работает";
         dot = accent;
      } else {
         status = "тишина " + ago(now - last);
         dot = new ColorRGBA(120, 120, 130);
      }

      float pulse = busy ? 0.55F + 0.45F * (float)Math.sin((double)nowMs / 220.0) : 1.0F;
      DrawUtil.drawShadow(ctx.getMatrices(), x + 8.0F, y + 7.5F, 5.0F, 5.0F, 5.0F, BorderRadius.all(2.5F), dot.withAlpha(140.0F * a * pulse));
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x + 8.0F, y + 7.5F, 5.0F, 5.0F, BorderRadius.all(2.5F), dot.withAlpha(255.0F * a * pulse));
      Font head = Fonts.MEDIUM.getFont(7.5F);
      Font small = Fonts.REGULAR.getFont(6.5F);
      ctx.drawText(head, "Claude Code", x + 17.0F, y + 6.5F, new ColorRGBA(245, 245, 250, 255.0F * a));
      float sw2 = small.width(status);
      ctx.drawText(small, status, x + w - 8.0F - sw2, y + 7.5F, dot.mix(new ColorRGBA(255, 255, 255), 0.3F).withAlpha(230.0F * a));
      DrawUtil.drawRect(ctx.getMatrices(), x + 6.0F, y + 18.5F, w - 12.0F, 0.5F, new ColorRGBA(255, 255, 255, 18.0F * a));
      float ry = y + 22.0F;

      // новые сверху; чем старше строка, тем бледнее
      for (int i = 0; i < count; ++i) {
         String[] e = list.get(list.size() - 1 - i);
         float fade = a * (1.0F - 0.13F * (float)i);
         ColorRGBA kind = kindColor(e[1], accent);
         DrawUtil.drawRoundedRect(ctx.getMatrices(), x + 8.0F, ry + 2.5F, 3.0F, 3.0F, BorderRadius.all(1.5F), kind.withAlpha(255.0F * fade));
         String time = ago(now - parse(e[0]));
         float tw = small.width(time);
         String text = fit(small, e[2], w - 26.0F - tw);
         ctx.drawText(small, text, x + 15.0F, ry, new ColorRGBA(225, 225, 232, 255.0F * fade));
         ctx.drawText(small, time, x + w - 8.0F - tw, ry, new ColorRGBA(140, 140, 152, 220.0F * fade));
         ry += rowH;
      }
   }

   private static ColorRGBA kindColor(String kind, ColorRGBA accent) {
      return switch (kind) {
         case "edit" -> accent;
         case "read" -> new ColorRGBA(120, 170, 255);
         case "run" -> new ColorRGBA(255, 170, 80);
         case "web" -> new ColorRGBA(150, 120, 255);
         case "wait" -> new ColorRGBA(255, 190, 70);
         case "done" -> new ColorRGBA(110, 230, 140);
         case "task" -> new ColorRGBA(255, 255, 255);
         default -> new ColorRGBA(170, 170, 180);
      };
   }

   private static long parse(String s) {
      try {
         return Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
         return 0L;
      }
   }

   private static String ago(long seconds) {
      if (seconds < 60L) {
         return Math.max(0L, seconds) + "с";
      } else {
         return seconds < 3600L ? seconds / 60L + "м" : seconds / 3600L + "ч";
      }
   }

   /** Обрезает текст по ширине с многоточием. */
   private static String fit(Font font, String text, float max) {
      if (font.width(text) <= max) {
         return text;
      }

      String t = text;

      while (t.length() > 1 && font.width(t + "...") > max) {
         t = t.substring(0, t.length() - 1);
      }

      return t + "...";
   }
}
