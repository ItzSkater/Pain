package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.text.Text;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.music.LyricsService;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(
   name = "MusicLyrics",
   category = Category.MISC,
   description = "Распознаёт текст играющей песни и выводит в ватермарку"
)
public final class MusicLyrics extends Module {
   public static final MusicLyrics INSTANCE = new MusicLyrics();
   private final ModeSetting engine = new ModeSetting("Движок", "Whisper", "Vosk", "Whisper.cpp");
   private final ModeSetting language = new ModeSetting("Язык", "auto", "ru", "en", "de", "es", "fr");
   private final ModeSetting model = new ModeSetting("Модель", () -> this.engine.is("Whisper"), "base", "tiny", "small");
   private final NumberSetting window = new NumberSetting("Окно (сек)", 5.0F, 3.0F, 10.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> !this.engine.is("Vosk"));
   public final BooleanSetting synced = new BooleanSetting("Синхр. текст (LRCLIB)", true);
   private final NumberSetting offset = new NumberSetting("Сдвиг (мс)", 0.0F, -3000.0F, 3000.0F, 50.0F);
   public final ModeSetting translate = new ModeSetting("Перевод", "Выкл", "Русский", "English", "Deutsch", "Español", "Français", "Українська");
   public final ModeSetting translateView = new ModeSetting("Показ перевода", () -> !this.translate.is("Выкл"), "Под строкой", "Вместо");
   public final ModeSetting lyricsStyle = new ModeSetting("Стиль лирикса", "Плашка", "Island");
   public final BooleanSetting showNext = new BooleanSetting("Следующая строка", true);
   public final BooleanSetting showTrack = new BooleanSetting("Показывать трек", true);
   public final BooleanSetting onlyPlaying = new BooleanSetting("Только когда играет", true);
   private final BooleanSetting restart = new BooleanSetting("Перезапуск при смене", true);
   private final BooleanSetting debug = new BooleanSetting("Дебаг в чат", false);
   private final BooleanSetting sky = new BooleanSetting("3D субтитры в небе", true);
   private final NumberSetting skySize = new NumberSetting("Размер 3D", 3.0F, 1.0F, 8.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.sky.isEnabled());
   private final NumberSetting skyDistance = new NumberSetting("Дистанция 3D", 40.0F, 15.0F, 120.0F, 5.0F, (java.util.function.Supplier<Boolean>)() -> this.sky.isEnabled());
   private final NumberSetting skyHeight = new NumberSetting("Высота 3D", 14.0F, 0.0F, 60.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.sky.isEnabled());
   private final ModeSetting skyFollow = new ModeSetting("Привязка 3D", () -> this.sky.isEnabled(), "На месте", "За взглядом");
   private final BooleanSetting skyThroughWalls = new BooleanSetting("3D сквозь стены", false, (java.util.function.Supplier<Boolean>)() -> this.sky.isEnabled());
   public final ModeSetting textColorMode = new ModeSetting("Цвет текста", "Тема", "Свой");
   public final tech.pain.client.modules.api.setting.impl.ColorSetting textColor = new tech.pain.client.modules.api.setting.impl.ColorSetting("Цвет букв", new tech.pain.utility.render.display.base.color.ColorRGBA(255, 255, 255), (java.util.function.Supplier<Boolean>)() -> this.textColorMode.is("Свой"));
   public final tech.pain.client.modules.api.setting.impl.ColorSetting sideColor = new tech.pain.client.modules.api.setting.impl.ColorSetting("Цвет объёма / перевода", new tech.pain.utility.render.display.base.color.ColorRGBA(255, 90, 160), (java.util.function.Supplier<Boolean>)() -> this.textColorMode.is("Свой"));
   private float skyYaw = Float.NaN;
   private net.minecraft.util.math.Vec3d skyAnchor;
   private net.minecraft.util.math.Vec3d skyTarget;
   private String skyLine = "";
   private long skyChanged;
   private long lastLaunch;
   private String lastConfig = "";
   private String pendingConfig = "";
   private long pendingSince;

   private MusicLyrics() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.launch();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      LyricsService.stop();
   }

   private String config() {
      return this.engine.get() + "|" + this.language.get() + "|" + this.model.get() + "|" + this.window.getCurrent();
   }

   private void launch() {
      this.lastLaunch = System.currentTimeMillis();
      this.lastConfig = this.config();
      LyricsService.configure(this.synced.isEnabled(), (long)this.offset.getCurrent());
      LyricsService.setTranslateTarget(this.translateTarget());
      boolean cpp = this.engine.is("Whisper.cpp");
      LyricsService.start(this.engine.is("Vosk") ? "vosk" : cpp ? "whispercpp" : "whisper", this.language.get(), cpp ? "large-v3-turbo" : this.model.get(), this.window.getCurrent());
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      LyricsService.configure(this.synced.isEnabled(), (long)this.offset.getCurrent());
      LyricsService.setTranslateTarget(this.translateTarget());
      String cfg = this.config();
      if (!cfg.equals(this.pendingConfig)) {
         this.pendingConfig = cfg;
         this.pendingSince = System.currentTimeMillis();
      }

      // перезапуск только когда настройки перестали меняться 1.5с (загрузка конфига, слайдер)
      if (this.restart.isEnabled() && !cfg.equals(this.lastConfig) && System.currentTimeMillis() - this.pendingSince > 1500L) {
         if (this.debug.isEnabled()) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§d[Lyrics] §7настройки изменены, перезапуск"));
         }

         this.launch();
      }

      String msg;
      int shown = 0;
      while (shown < 6 && (msg = LyricsService.pollDebug()) != null) {
         if (this.debug.isEnabled() && mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§d[Lyrics] §7" + msg));
            ++shown;
         }
      }

      if (LyricsService.takeCrashed() && System.currentTimeMillis() - this.lastLaunch > 5000L) {
         if (this.debug.isEnabled()) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§d[Lyrics] §7хелпер упал — перезапуск"));
         }

         this.launch();
      }

      String error = LyricsService.pollError();
      if (error != null) {
         Pain.getInstance().getNotifyManager().addNotification("", Text.of("MusicLyrics: " + error));
         if (error.contains("not installed") || error.contains("python")) {
            Pain.getInstance().getNotifyManager().addNotification("", Text.of("python3 -m venv ~/.config/pain/lyrics/venv && ~/.config/pain/lyrics/venv/bin/pip install faster-whisper vosk numpy"));
         }
      }
   }

   /** Текущая строка и перевод (или null) — общая логика для HUD и 3D. */
   public String[] displayLines() {
      if (!this.shouldShow()) {
         return null;
      }

      String line = "";
      if (LyricsService.hasSynced()) {
         java.util.List<tech.pain.base.music.SyncedLyrics.Line> lines = LyricsService.getSynced();
         int idx = LyricsService.getSyncedIndex();
         line = idx >= 0 ? lines.get(idx).text() : "";
      } else {
         line = LyricsService.getLine();
      }

      if (line.isEmpty()) {
         return null;
      }

      String target = this.translateTarget();
      String tr = target == null ? null : tech.pain.base.music.Translator.get(line, target);
      if (tr != null && tr.equalsIgnoreCase(line)) {
         tr = null;
      }

      if (tr != null && this.translateView.is("Вместо")) {
         return new String[]{tr, null};
      }

      return new String[]{line, tr};
   }

   @EventTarget
   public void onRender3D(tech.pain.base.events.impl.render.EventRender3D event) {
      if (!this.sky.isEnabled() || mc.player == null) {
         return;
      }

      String[] lines = this.displayLines();
      if (lines == null) {
         this.skyLine = "";
         return;
      }

      long now = System.currentTimeMillis();
      if (!lines[0].equals(this.skyLine)) {
         this.skyLine = lines[0];
         this.skyChanged = now;
      }

      net.minecraft.client.render.Camera cam = mc.gameRenderer.getCamera();
      net.minecraft.util.math.Vec3d camPos = cam.getPos();
      double dist = this.skyDistance.getCurrent();
      double yawRad = Math.toRadians(cam.getYaw());
      net.minecraft.util.math.Vec3d wanted = camPos.add(-Math.sin(yawRad) * dist, this.skyHeight.getCurrent(), Math.cos(yawRad) * dist);
      if (this.skyAnchor == null || this.skyAnchor.distanceTo(camPos) > dist * 4.0) {
         this.skyAnchor = wanted;
         this.skyTarget = wanted;
      }

      if (this.skyFollow.is("За взглядом")) {
         this.skyTarget = wanted;
      } else {
         // стоит на месте в мире; перелетает, только если ушёл из поля зрения или игрок далеко ушёл
         net.minecraft.util.math.Vec3d to = this.skyTarget.subtract(camPos);
         double flat = Math.sqrt(to.x * to.x + to.z * to.z);
         float anchorYaw = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
         float diff = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(cam.getYaw() - anchorYaw));
         if (diff > 70.0F || flat > dist * 1.8 || flat < dist * 0.35) {
            this.skyTarget = wanted;
         }
      }

      this.skyAnchor = this.skyAnchor.lerp(this.skyTarget, 0.035);
      net.minecraft.util.math.Vec3d pos = this.skyAnchor.add(0.0, Math.sin((double)now / 1400.0) * 0.35, 0.0);
      // плоскость текста медленно доворачивается к игроку — при обходе видно сбоку, с перспективой
      float face = (float)Math.atan2(camPos.x - pos.x, camPos.z - pos.z);
      if (Float.isNaN(this.skyYaw)) {
         this.skyYaw = face;
      }

      float d = face - this.skyYaw;
      d = (float)Math.atan2(Math.sin(d), Math.cos(d));
      this.skyYaw += d * 0.02F;
      float unit = this.skySize.getCurrent() * 0.085F * (float)(dist / 40.0);
      net.minecraft.client.util.math.MatrixStack ms = event.getMatrix();
      ms.push();
      ms.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
      ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation(this.skyYaw + (float)Math.sin((double)now / 2600.0) * 0.12F));
      ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(12.0F + (float)Math.sin((double)now / 1900.0) * 3.0F));
      ms.scale(unit, -unit, unit);
      // с тестом глубины текст закрывают сущности и блоки; запись глубины выключена, чтобы слои не мерцали
      if (this.skyThroughWalls.isEnabled()) {
         com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
      } else {
         com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
      }

      com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
      tech.pain.base.theme.Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      this.drawSkyRow(ms, lines[0], 10.0F, 0.0F, now - this.skyChanged, theme, true);
      if (lines[1] != null) {
         this.drawSkyRow(ms, lines[1], 6.5F, this.skyRowsHeight + 3.0F, now - this.skyChanged - 150L, theme, false);
      }

      com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
      com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
      ms.pop();
   }

   private float skyRowsHeight;

   /** Объёмный текст: слова появляются по одному, у букв есть толщина (слои в глубину). */
   private void drawSkyRow(net.minecraft.client.util.math.MatrixStack ms, String text, float size, float top, long age, tech.pain.base.theme.Theme theme, boolean main) {
      tech.pain.base.font.MsdfFont font = main ? tech.pain.base.font.Fonts.BOLD : tech.pain.base.font.Fonts.MEDIUM;
      String[] words = plain(text).trim().split("\\s+");
      float space = size * 0.32F;
      float maxRow = size * 16.0F;
      java.util.List<java.util.List<Integer>> rows = new java.util.ArrayList<>();
      java.util.List<Float> rowWidths = new java.util.ArrayList<>();
      java.util.List<Integer> row = new java.util.ArrayList<>();
      float rw = 0.0F;

      for (int i = 0; i < words.length; ++i) {
         float ww = font.getWidth(words[i], size);
         if (!row.isEmpty() && rw + space + ww > maxRow) {
            rows.add(row);
            rowWidths.add(rw);
            row = new java.util.ArrayList<>();
            rw = 0.0F;
         }

         rw += (row.isEmpty() ? 0.0F : space) + ww;
         row.add(i);
      }

      rows.add(row);
      rowWidths.add(rw);
      float per = Math.min(130.0F, 1500.0F / (float)Math.max(1, words.length));
      int layers = main ? 9 : 4;
      float step = size * 0.035F;
      tech.pain.utility.render.display.base.color.ColorRGBA c1 = theme.getColor();
      tech.pain.utility.render.display.base.color.ColorRGBA c2 = theme.getSecondColor();
      tech.pain.utility.render.display.base.color.ColorRGBA white = new tech.pain.utility.render.display.base.color.ColorRGBA(255, 255, 255);
      boolean customColors = this.textColorMode.is("Свой");
      if (customColors) {
         c1 = this.sideColor.getColor();
         c2 = c1;
      }
      float y = top;

      for (int r = 0; r < rows.size(); ++r) {
         float x = -rowWidths.get(r) / 2.0F;

         for (int idx : rows.get(r)) {
            String word = words[idx];
            float ww = font.getWidth(word, size);
            float p = net.minecraft.util.math.MathHelper.clamp(((float)age - (float)idx * per) / 260.0F, 0.0F, 1.0F);
            if (p > 0.0F) {
               float back = 1.0F + 2.2F * (float)Math.pow((double)(p - 1.0F), 3.0) + 1.2F * (float)Math.pow((double)(p - 1.0F), 2.0);
               float sc = 0.55F + 0.45F * back;
               float ratio = words.length <= 1 ? 0.0F : (float)idx / (float)(words.length - 1);
               tech.pain.utility.render.display.base.color.ColorRGBA side = c1.mix(c2, ratio);
               tech.pain.utility.render.display.base.color.ColorRGBA front = customColors ? (main ? this.textColor.getColor() : side) : (main ? white.mix(side, 0.18F) : side.mix(white, 0.35F));
               ms.push();
               ms.translate(x + ww / 2.0F, y + size / 2.0F + (1.0F - p) * size * 0.4F, 0.0F);
               ms.scale(sc, sc, sc);
               ms.translate(-ww / 2.0F, -size / 2.0F, 0.0F);
               org.joml.Matrix4f mat = ms.peek().getPositionMatrix();

               // от дальнего слоя к ближнему: боковина темнее, лицевая сторона светлая
               for (int l = layers; l >= 1; --l) {
                  float shade = 0.35F + 0.4F * (1.0F - (float)l / (float)layers);
                  tech.pain.base.font.MsdfRenderer.renderText(font, word, size, side.darker(1.0F - shade).withAlpha(235.0F * p).getRGB(), mat, 0.0F, 0.0F, -(float)l * step);
               }

               tech.pain.base.font.MsdfRenderer.renderText(font, word, size, front.withAlpha(255.0F * p).getRGB(), mat, 0.0F, 0.0F, 0.0F);
               ms.pop();
            }

            x += ww + space;
         }

         y += size * 1.15F;
      }

      this.skyRowsHeight = y;
   }

   /** Шрифт клиента знает латиницу без диакритики и кириллицу — é/ñ/ü приводим к e/n/u. */
   public static String plain(String text) {
      StringBuilder sb = new StringBuilder(text.length());

      for (char c : java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace("ß", "ss").toCharArray()) {
         if (Character.getType(c) == Character.NON_SPACING_MARK && c != '\u0306' && c != '\u0308') {
            continue;
         }

         sb.append(c);
      }

      // й/ё и ü/ö/ä разложились на букву + знак: кириллицу собираем обратно, у латиницы знак убираем
      String out = java.text.Normalizer.normalize(sb.toString(), java.text.Normalizer.Form.NFC);
      StringBuilder res = new StringBuilder(out.length());

      for (char c : out.toCharArray()) {
         if (c >= 0x00C0 && c <= 0x024F) {
            String base = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFD);
            res.append(base.charAt(0));
         } else {
            res.append(c);
         }
      }

      return res.toString().replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"').replace('¿', ' ').replace('¡', ' ').replace("…", "...");
   }

   /** Код языка перевода или null, если перевод выключен. */
   public String translateTarget() {
      switch (this.translate.get()) {
         case "Русский": return "ru";
         case "English": return "en";
         case "Deutsch": return "de";
         case "Español": return "es";
         case "Français": return "fr";
         case "Українська": return "uk";
         default: return null;
      }
   }

   /** Первый запуск клиента: включаем модуль сами, чтобы лирикс работал из коробки. */
   public void autoEnableOnce() {
      try {
         java.nio.file.Path marker = java.nio.file.Path.of(System.getProperty("user.home"), ".config", "pain", "lyrics", ".autostarted");
         if (!java.nio.file.Files.exists(marker)) {
            java.nio.file.Files.createDirectories(marker.getParent());
            java.nio.file.Files.writeString(marker, "1");
            if (!this.isEnabled()) {
               this.toggle();
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /** Идёт установка/загрузка — показываем прогресс в ватермарке. */
   public boolean isSettingUp() {
      String st = LyricsService.getStatus();
      return LyricsService.isRunning() && !st.equals("слушаю...") && !st.equals("ошибка");
   }

   /** Можно ли сейчас показывать музыку в HUD. */
   public boolean shouldShow() {
      if (!this.isEnabled() || !LyricsService.isRunning()) {
         return false;
      }

      if (this.isSettingUp()) {
         return true;
      }

      if (this.onlyPlaying.isEnabled() && LyricsService.isPlayerKnown()) {
         return LyricsService.isPlaying();
      }

      return !LyricsService.getLine().isEmpty() || LyricsService.isPlaying();
   }
}
