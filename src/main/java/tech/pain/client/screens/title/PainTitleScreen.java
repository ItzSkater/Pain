package tech.pain.client.screens.title;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import tech.pain.Pain;
import tech.pain.base.font.Fonts;
import tech.pain.base.font.MsdfRenderer;
import tech.pain.base.theme.Theme;
import tech.pain.utility.interfaces.IMinecraft;
import tech.pain.utility.render.display.ImageTexture;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.Gradient;
import tech.pain.utility.render.display.base.GuiUtil;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Главное меню Pain: фон — панорама космоса, которая медленно плывёт и чуть сдвигается за мышью,
 * по центру лого и стеклянные кнопки.
 */
public class PainTitleScreen extends Screen implements IMinecraft {
   private static final float SCALE = 2.0F;
   // встроенные обои: название в настройке → картинка в ресурсах
   public static final String[][] WALLPAPERS = {
      {"Deep Space", "/assets/pain/textures/sky/deep_space.jpg"},
      {"Inside Galaxy", "/assets/pain/textures/sky/inside_galaxy.jpg"},
      {"Спираль", "/assets/pain/textures/wallpaper/spiral.jpg"},
      {"Чёрная дыра", "/assets/pain/textures/wallpaper/black_hole.jpg"},
      {"Линии", "/assets/pain/textures/wallpaper/lines.jpg"},
      {"Калифорния", "/assets/pain/textures/wallpaper/california.jpg"},
      {"Туманность Киля", "/assets/pain/textures/wallpaper/carina.jpg"},
      {"Тарантул", "/assets/pain/textures/wallpaper/tarantula.jpg"},
      {"Трифид и Лагуна", "/assets/pain/textures/wallpaper/trifid.jpg"},
      {"Лагуна", "/assets/pain/textures/wallpaper/lagoon.jpg"},
      {"Звёздный след", "/assets/pain/textures/wallpaper/star_trails.jpg"},
      {"Чжанцзяцзе", "/assets/pain/textures/wallpaper/zhangjiajie.jpg"},
      {"Над облаками", "/assets/pain/textures/wallpaper/above_clouds.jpg"},
      {"Глен-Коу", "/assets/pain/textures/wallpaper/glen_coe.jpg"},
      {"Уюни", "/assets/pain/textures/wallpaper/uyuni.jpg"},
      {"Остров Скай", "/assets/pain/textures/wallpaper/skye.jpg"},
      {"Лейк-Луиз", "/assets/pain/textures/wallpaper/lake_louise.jpg"},
      {"Глен-Этив", "/assets/pain/textures/wallpaper/etive.jpg"},
      {"Дедвлей", "/assets/pain/textures/wallpaper/deadvlei.jpg"},
      {"Тейде", "/assets/pain/textures/wallpaper/teide.jpg"}
   };
   private static final java.util.Map<String, ImageTexture> LOADED = new java.util.HashMap<>();
   // слайд-шоу и «случайные»: порядок перемешан один раз за запуск
   private static final java.util.List<String> SHUFFLED = shuffled();
   private static int slide;
   private static long slideAt = System.currentTimeMillis();
   private static String previousSlide;
   private static ImageTexture custom;
   private static String customStamp = "";
   private static long customChecked;

   /** Обои: встроенная панорама или первая картинка (png/jpg) из папки Pain/wallpaper. */
   private static java.util.List<String> shuffled() {
      java.util.List<String> names = new java.util.ArrayList<>();

      for (String[] w : WALLPAPERS) {
         names.add(w[0]);
      }

      java.util.Collections.shuffle(names);
      return names;
   }

   private static ImageTexture texture(String name) {
      return LOADED.computeIfAbsent(name, n -> {
         for (String[] w : WALLPAPERS) {
            if (w[0].equals(n)) {
               return new ImageTexture(w[1]);
            }
         }

         return new ImageTexture(WALLPAPERS[0][1]);
      });
   }

   /** Освобождает видеопамять обоев, которые сейчас не показываются. */
   private static void release(String... keep) {
      java.util.Iterator<java.util.Map.Entry<String, ImageTexture>> it = LOADED.entrySet().iterator();

      while (it.hasNext()) {
         java.util.Map.Entry<String, ImageTexture> e = it.next();
         if (!java.util.Arrays.asList(keep).contains(e.getKey())) {
            e.getValue().dispose();
            it.remove();
         }
      }
   }

   /** Обои: встроенная картинка, слайд-шоу, случайные или первая картинка (png/jpg) из папки Pain/wallpaper. */
   private static ImageTexture background() {
      tech.pain.client.modules.api.setting.impl.ModeSetting mode = tech.pain.client.modules.impl.render.CoolInterface.INSTANCE.wallpaper;
      if (mode.is("Случайные")) {
         return texture(SHUFFLED.get(0));
      }

      if (mode.is("Слайд-шоу")) {
         return texture(SHUFFLED.get(slide % SHUFFLED.size()));
      }

      if (mode.is("Свой файл")) {
         long now = System.currentTimeMillis();
         if (now - customChecked > 3000L) {
            customChecked = now;

            try {
               java.nio.file.Path dir = mc.runDirectory.toPath().resolve("Pain").resolve("wallpaper");
               java.nio.file.Files.createDirectories(dir);
               java.nio.file.Path file;

               try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                  file = files.filter(f -> {
                     String n = f.getFileName().toString().toLowerCase();
                     return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                  }).sorted().findFirst().orElse(null);
               }

               String stamp = file == null ? "" : file + "|" + java.nio.file.Files.getLastModifiedTime(file);
               if (!stamp.equals(customStamp)) {
                  customStamp = stamp;
                  if (custom != null) {
                     custom.dispose();
                  }

                  custom = file == null ? null : new ImageTexture(file);
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }

         if (custom != null && !custom.failed()) {
            return custom;
         }
      }

      for (String[] w : WALLPAPERS) {
         if (mode.is(w[0])) {
            return texture(w[0]);
         }
      }

      return texture(WALLPAPERS[0][0]);
   }
   private final List<Button> buttons = new ArrayList<>();
   private final long openedAt = System.currentTimeMillis();
   private float mouseX;
   private float mouseY;
   private static float parallaxX;
   private static float parallaxY;
   private static final long WALLPAPER_START = System.currentTimeMillis();
   private int failures;

   public PainTitleScreen() {
      super(Text.of("Pain"));
      this.buttons.add(new Button("Одиночная игра", "1", () -> mc.setScreen(new SelectWorldScreen(this))));
      this.buttons.add(new Button("Сетевая игра", "5", () -> mc.setScreen(new MultiplayerScreen(this))));
      this.buttons.add(new Button("Аккаунты", "5", () -> mc.setScreen(new tech.pain.client.screens.alt.AltScreen(this))));
      this.buttons.add(new Button("Настройки", "G", () -> mc.setScreen(new OptionsScreen(this, mc.options))));
      this.buttons.add(new Button("Выход", "M", mc::scheduleStop));
   }

   private static final class Button {
      final String label;
      final String icon;
      final Runnable action;
      float hover;
      float x;
      float y;
      float w;
      float h;

      Button(String label, String icon, Runnable action) {
         this.label = label;
         this.icon = icon;
         this.action = action;
      }

      boolean contains(float mx, float my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   @Override
   public void render(DrawContext context, int mx, int my, float delta) {
      double savedScale = mc.getWindow().getScaleFactor();
      setScale(SCALE);
      Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
      RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float)mc.getWindow().getScaledWidth(), (float)mc.getWindow().getScaledHeight(), 0.0F, 1000.0F, 21000.0F), ProjectionType.ORTHOGRAPHIC);

      try {
         this.renderMenu(new CustomDrawContext(mc.getBufferBuilders().getEntityVertexConsumers()));
      } catch (Throwable e) {
         e.printStackTrace();
         ++this.failures;
      } finally {
         context.draw();
         setScale(savedScale);
         RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
      }

      // меню не рисуется — возвращаем ванильное, чтобы игрок не остался без кнопок
      if (this.failures > 3) {
         tech.pain.client.modules.impl.render.CoolInterface.INSTANCE.customTitle.setEnabled(false);
         mc.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
      }
   }

   private static void setScale(double scale) {
      mc.getWindow().scaleFactor = scale;
      int i = (int)((double)mc.getWindow().framebufferWidth / scale);
      mc.getWindow().scaledWidth = (double)mc.getWindow().framebufferWidth / scale > (double)i ? i + 1 : i;
      int j = (int)((double)mc.getWindow().framebufferHeight / scale);
      mc.getWindow().scaledHeight = (double)mc.getWindow().framebufferHeight / scale > (double)j ? j + 1 : j;
   }

   private void renderMenu(CustomDrawContext ctx) {
      MatrixStack m = ctx.getMatrices();
      Vector2f mouse = GuiUtil.getMouse(SCALE);
      this.mouseX = mouse.getX();
      this.mouseY = mouse.getY();
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      ColorRGBA c1 = theme.getColor();
      ColorRGBA c2 = theme.getSecondColor();
      double time = (double)(System.currentTimeMillis() - this.openedAt) / 1000.0;
      float appear = (float)Math.min(1.0, time / 0.7);
      appear = 1.0F - (1.0F - appear) * (1.0F - appear) * (1.0F - appear);
      drawWallpaper(m, sw, sh, this.mouseX, this.mouseY);
      // затемнение к краям и снизу, чтобы текст читался на любом месте панорамы
      ColorRGBA dark = new ColorRGBA(0, 0, 0, 170);
      ColorRGBA none = new ColorRGBA(0, 0, 0, 0);
      DrawUtil.drawRoundedRect(m, 0.0F, sh * 0.45F, sw, sh * 0.55F, BorderRadius.ZERO, Gradient.of(none, dark, none, dark));
      DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, sw, sh * 0.3F, BorderRadius.ZERO, Gradient.of(new ColorRGBA(0, 0, 0, 110), none, new ColorRGBA(0, 0, 0, 110), none));
      DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, sw, sh, BorderRadius.ZERO, new ColorRGBA(8, 8, 14, 60));

      // лого и название
      float cx = sw / 2.0F;
      float top = sh * 0.2F + (1.0F - appear) * 12.0F;
      int alpha = (int)(255.0F * appear);
      float logoSize = 34.0F;
      float logoW = Fonts.ICONS.getWidth("B", logoSize);
      DrawUtil.drawShadow(m, cx - 26.0F, top - 2.0F, 52.0F, 44.0F, 40.0F, BorderRadius.all(22.0F), c1.withAlpha(70.0F * appear));
      MsdfRenderer.renderText(Fonts.ICONS, "B", logoSize, Gradient.of(c1.withAlpha(alpha), c2.withAlpha(alpha), c1.withAlpha(alpha), c2.withAlpha(alpha)), m.peek().getPositionMatrix(), cx - logoW / 2.0F, top, 0.0F, true, cx - logoW / 2.0F, cx + logoW / 2.0F);
      String title = "Pain";
      String tail = " Client";
      float titleSize = 30.0F;
      float tw = Fonts.BOLD.getWidth(title, titleSize);
      float tailW = Fonts.BOLD.getWidth(tail, titleSize);
      float tx = cx - (tw + tailW) / 2.0F;
      tech.pain.client.modules.impl.render.CoolInterface menuModule = tech.pain.client.modules.impl.render.CoolInterface.INSTANCE;
      ColorRGBA titleColor = menuModule.titleColor.is("Тема") ? c1 : (menuModule.titleColor.is("Чёрный") ? new ColorRGBA(0, 0, 0) : new ColorRGBA(255, 255, 255));
      ctx.drawText(Fonts.BOLD.getFont(titleSize), title + tail, tx, top + 48.0F, titleColor.withAlpha(alpha));
      String sub = tech.pain.Edition.NAME + "  ·  v1.3  ·  Minecraft 1.21.4";
      float subW = Fonts.REGULAR.getWidth(sub, 8.0F);
      ctx.drawText(Fonts.REGULAR.getFont(8.0F), sub, cx - subW / 2.0F, top + 82.0F, new ColorRGBA(220, 220, 232, (int)(170.0F * appear)));

      // кнопки
      float bw = 190.0F;
      float bh = 24.0F;
      float gap = 7.0F;
      float by = top + 108.0F;

      for (int i = 0; i < this.buttons.size(); ++i) {
         Button b = this.buttons.get(i);
         float delay = Math.max(0.0F, Math.min(1.0F, (float)(time - 0.15 - (double)i * 0.07) / 0.45F));
         float in = 1.0F - (1.0F - delay) * (1.0F - delay) * (1.0F - delay);
         b.w = bw;
         b.h = bh;
         b.x = cx - bw / 2.0F;
         b.y = by + (float)i * (bh + gap) + (1.0F - in) * 10.0F;
         b.hover += ((b.contains(this.mouseX, this.mouseY) ? 1.0F : 0.0F) - b.hover) * 0.2F;
         this.drawButton(ctx, m, b, in, c1, c2);
      }

      this.chips.clear();
      this.chip(ctx, m, 10.0F, sh - 36.0F, "Обои: " + menuModule.wallpaper.get(), appear, () -> cycle(menuModule.wallpaper));
      float chipX = 10.0F + this.lastChipWidth + 5.0F;
      this.chip(ctx, m, chipX, sh - 36.0F, "Заголовок: " + menuModule.titleColor.get(), appear, () -> cycle(menuModule.titleColor));
      chipX += this.lastChipWidth + 5.0F;
      this.chip(ctx, m, chipX, sh - 36.0F, "Стекло: " + menuModule.glass.get(), appear, () -> cycle(menuModule.glass));
      // подписи по углам
      String name = mc.getSession().getUsername();
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), "Pain " + tech.pain.Edition.NAME + " v1.3", 10.0F, sh - 14.0F, new ColorRGBA(220, 220, 232, (int)(150.0F * appear)));
      float nameW = Fonts.MEDIUM.getWidth(name, 7.5F);
      DrawUtil.drawRoundedRect(m, sw - nameW - 32.0F, sh - 20.0F, 6.0F, 6.0F, BorderRadius.all(3.0F), new ColorRGBA(60, 220, 110, alpha));
      ctx.drawText(Fonts.MEDIUM.getFont(7.5F), name, sw - nameW - 22.0F, sh - 21.0F, new ColorRGBA(240, 240, 248, alpha));
      String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
      float clockW = Fonts.MEDIUM.getWidth(clock, 9.0F);
      ctx.drawText(Fonts.MEDIUM.getFont(9.0F), clock, sw - clockW - 12.0F, 10.0F, new ColorRGBA(240, 240, 248, (int)(200.0F * appear)));
      // плавное появление из чёрного
      if (appear < 1.0F) {
         DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, sw, sh, BorderRadius.ZERO, new ColorRGBA(0, 0, 0, (int)(255.0F * (1.0F - appear))));
      }

      ctx.draw();
   }

   /** Стеклянная кнопка: блюр фона, светлая заливка, блик сверху; при наведении подсвечивается цветом темы. */
   private void drawButton(CustomDrawContext ctx, MatrixStack m, Button b, float in, ColorRGBA c1, ColorRGBA c2) {
      float a = in;
      float grow = b.hover * 3.0F;
      float x = b.x - grow;
      float w = b.w + grow * 2.0F;
      BorderRadius r = BorderRadius.all(b.h / 2.0F);
      tech.pain.client.hud.HudStyle.glass(m, x, b.y, w, b.h, r, a, true);
      if (b.hover > 0.02F) {
         ColorRGBA g1 = c1.withAlpha(95.0F * b.hover * a);
         ColorRGBA g2 = c2.withAlpha(60.0F * b.hover * a);
         DrawUtil.drawRoundedRect(m, x, b.y, w, b.h, r, Gradient.of(g1, g1, g2, g2));
         DrawUtil.drawRoundedBorder(m, x, b.y, w, b.h, -0.5F, r, new ColorRGBA(255, 255, 255, 80.0F * b.hover * a));
      }

      float labelW = Fonts.MEDIUM.getWidth(b.label, 8.5F);
      float iconW = 12.0F;
      float lx = b.x + (b.w - labelW - iconW) / 2.0F;
      ctx.drawText(Fonts.ICONS.getFont(8.0F), b.icon, lx, b.y + b.h / 2.0F - 4.0F, new ColorRGBA(200, 200, 215).mix(c1, b.hover).withAlpha(255.0F * a));
      ctx.drawText(Fonts.MEDIUM.getFont(8.5F), b.label, lx + iconW, b.y + b.h / 2.0F - 4.25F, new ColorRGBA(255, 255, 255, (int)((215.0F + 40.0F * b.hover) * a)));
   }

   /** Панорама закрывает весь экран, медленно плывёт вбок и слегка сдвигается за мышью. */
   public static void drawWallpaper(MatrixStack m, float sw, float sh, float mouseX, float mouseY) {
      DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, sw, sh, BorderRadius.ZERO, new ColorRGBA(6, 6, 12, 255));
      parallaxX += ((mouseX / sw - 0.5F) - parallaxX) * 0.05F;
      parallaxY += ((mouseY / sh - 0.5F) - parallaxY) * 0.05F;
      tech.pain.client.modules.impl.render.CoolInterface ui = tech.pain.client.modules.impl.render.CoolInterface.INSTANCE;
      if (!ui.wallpaper.is("Слайд-шоу")) {
         drawImage(m, background(), sw, sh, 1.0F, 0.0F);
         return;
      }

      long now = System.currentTimeMillis();
      long hold = (long)(ui.slideSeconds.getCurrent() * 1000.0F);
      String current = SHUFFLED.get(slide % SHUFFLED.size());
      ImageTexture next = texture(SHUFFLED.get((slide + 1) % SHUFFLED.size()));
      // следующий кадр начинаем грузить заранее; переключаемся, только когда он готов
      if (now - slideAt > hold && next.id() != -1) {
         previousSlide = current;
         slide++;
         slideAt = now;
         current = SHUFFLED.get(slide % SHUFFLED.size());
      }

      float fade = Math.min(1.0F, (float)(now - slideAt) / 1400.0F);
      // медленный наезд камеры за время показа кадра
      float zoom = Math.min(1.0F, (float)(now - slideAt) / (float)(hold + 1400L));
      if (previousSlide != null && fade < 1.0F) {
         drawImage(m, texture(previousSlide), sw, sh, 1.0F, 1.0F);
      }

      drawImage(m, texture(current), sw, sh, fade * fade * (3.0F - 2.0F * fade), zoom);
      if (fade >= 1.0F) {
         release(current, SHUFFLED.get((slide + 1) % SHUFFLED.size()));
         previousSlide = null;
      }
   }

   /** Картинка на весь экран: панорама плывёт вбок, обычное фото заполняет экран и чуть следует за мышью. */
   private static void drawImage(MatrixStack m, ImageTexture image, float sw, float sh, float alpha, float zoomIn) {
      int texture = image.id();
      if (texture == -1 || image.width() == 0 || alpha <= 0.0F) {
         return;
      }

      double time = (double)(System.currentTimeMillis() - WALLPAPER_START) / 1000.0;
      float aspect = (float)image.width() / (float)image.height();
      float uSpan;
      float vSpan;
      float u0;
      float v0;
      if (aspect > 1.9F) {
         // панорама: показываем полосу шириной в половину оборота, она медленно плывёт
         uSpan = 0.5F;
         vSpan = Math.min(uSpan * aspect * sh / sw, 0.9F);
         u0 = (float)(time * 0.004) + parallaxX * 0.02F + 0.3F;
         v0 = (1.0F - vSpan) / 2.0F + parallaxY * 0.03F;
      } else {
         // обычная картинка: заполняем экран с обрезкой краёв, с небольшим запасом под сдвиг за мышью
         float screen = sw / sh;
         float zoom = 0.94F - 0.06F * zoomIn;
         if (aspect > screen) {
            vSpan = zoom;
            uSpan = zoom * screen / aspect;
         } else {
            uSpan = zoom;
            vSpan = zoom * aspect / screen;
         }

         u0 = (1.0F - uSpan) / 2.0F + parallaxX * (1.0F - zoom) * 0.8F;
         v0 = (1.0F - vSpan) / 2.0F + parallaxY * (1.0F - zoom) * 0.8F;
      }

      Matrix4f mat = m.peek().getPositionMatrix();
      int color = Math.min(255, (int)(alpha * 255.0F)) << 24 | 0xFFFFFF;
      if (alpha < 1.0F) {
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
      } else {
         RenderSystem.disableBlend();
      }

      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
      RenderSystem.setShaderTexture(0, texture);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      bb.vertex(mat, 0.0F, 0.0F, 0.0F).texture(u0, v0).color(color);
      bb.vertex(mat, 0.0F, sh, 0.0F).texture(u0, v0 + vSpan).color(color);
      bb.vertex(mat, sw, sh, 0.0F).texture(u0 + uSpan, v0 + vSpan).color(color);
      bb.vertex(mat, sw, 0.0F, 0.0F).texture(u0 + uSpan, v0).color(color);
      BufferRenderer.drawWithGlobalProgram(bb.end());
      RenderSystem.setShaderTexture(0, 0);
      RenderSystem.enableBlend();
   }

   private final List<Button> chips = new ArrayList<>();
   private final java.util.Map<String, Float> chipHover = new java.util.HashMap<>();
   private float lastChipWidth;

   private static void cycle(tech.pain.client.modules.api.setting.impl.ModeSetting setting) {
      List<tech.pain.client.modules.api.setting.impl.ModeSetting.Value> values = setting.getValues();
      int index = values.indexOf(setting.getValue());
      setting.setValue(values.get((index + 1) % values.size()));
   }

   /** Маленькая стеклянная плашка-переключатель в углу. */
   private void chip(CustomDrawContext ctx, MatrixStack m, float x, float y, String text, float a, Runnable action) {
      float w = Fonts.REGULAR.getWidth(text, 7.0F) + 14.0F;
      float h = 15.0F;
      Button b = new Button(text, "", action);
      b.x = x;
      b.y = y;
      b.w = w;
      b.h = h;
      String key = text.substring(0, text.indexOf(':'));
      float hover = this.chipHover.getOrDefault(key, 0.0F);
      hover += ((b.contains(this.mouseX, this.mouseY) ? 1.0F : 0.0F) - hover) * 0.2F;
      this.chipHover.put(key, hover);
      BorderRadius r = BorderRadius.all(h / 2.0F);
      DrawUtil.drawRoundedRect(m, x, y, w, h, r, new ColorRGBA(20, 22, 32, 120.0F * a));
      DrawUtil.drawRoundedRect(m, x, y, w, h, r, new ColorRGBA(255, 255, 255, (16.0F + 18.0F * hover) * a));
      DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, r, new ColorRGBA(255, 255, 255, (55.0F + 70.0F * hover) * a));
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), text, x + 7.0F, y + 4.5F, new ColorRGBA(240, 240, 248, (int)((200.0F + 55.0F * hover) * a)));
      this.chips.add(b);
      this.lastChipWidth = w;
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         for (Button b : this.chips) {
            if (b.contains(this.mouseX, this.mouseY)) {
               b.action.run();
               return true;
            }
         }

         for (Button b : this.buttons) {
            if (b.contains(this.mouseX, this.mouseY)) {
               b.action.run();
               return true;
            }
         }
      }

      return true;
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }
}
