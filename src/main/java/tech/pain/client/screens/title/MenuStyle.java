package tech.pain.client.screens.title;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import tech.pain.Pain;
import tech.pain.base.theme.Theme;
import tech.pain.client.hud.HudStyle;
import tech.pain.client.modules.impl.render.CoolInterface;
import tech.pain.utility.interfaces.IMinecraft;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.Gradient;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Общий вид ванильных экранов (настройки, одиночная и сетевая игра): те же обои, что в главном меню,
 * и стеклянные кнопки, слайдеры и списки вместо серых текстур.
 */
public final class MenuStyle implements IMinecraft {
   private static int failures;

   private MenuStyle() {
   }

   public static boolean enabled() {
      return failures < 5 && CoolInterface.INSTANCE.gameMenusEnabled();
   }

   /** Обои рисуем только вне мира: в игре за меню должен быть виден сам мир. */
   public static boolean wallpaperEnabled() {
      return enabled() && CoolInterface.INSTANCE.titleEnabled() && mc.world == null;
   }

   private static void fail(Throwable e) {
      // что-то пошло не так — после нескольких ошибок возвращаем ванильный вид
      if (++failures <= 5) {
         e.printStackTrace();
      }
   }

   public static boolean wallpaper(DrawContext context, int width, int height) {
      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         float mx = (float)(mc.mouse.getX() * (double)width / (double)Math.max(1, mc.getWindow().getWidth()));
         float my = (float)(mc.mouse.getY() * (double)height / (double)Math.max(1, mc.getWindow().getHeight()));
         PainTitleScreen.drawWallpaper(m, (float)width, (float)height, mx, my);
         DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, (float)width, (float)height, BorderRadius.ZERO, new ColorRGBA(6, 7, 12, 150));
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   private static boolean pain() {
      return CoolInterface.INSTANCE.painLook();
   }

   private static final float R = 3.0F;

   /** Вид «Pain»: плотная тёмная плашка, тонкий обод; при наведении обод и полоска снизу цвета темы. */
   private static void painBox(MatrixStack m, float x, float y, float w, float h, boolean hovered, float a) {
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      BorderRadius r = BorderRadius.all(Math.min(R, h / 2.0F));
      DrawUtil.drawShadow(m, x, y + 1.0F, w, h, 6.0F, r, new ColorRGBA(0, 0, 0, 80.0F * a));
      ColorRGBA top = hovered ? new ColorRGBA(30, 30, 40, 225.0F * a) : new ColorRGBA(18, 18, 24, 215.0F * a);
      ColorRGBA bottom = hovered ? new ColorRGBA(20, 20, 28, 225.0F * a) : new ColorRGBA(11, 11, 15, 215.0F * a);
      DrawUtil.drawRoundedRect(m, x, y, w, h, r, Gradient.of(top, bottom, top, bottom));
      DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, r, hovered ? theme.getColor().withAlpha(150.0F * a) : new ColorRGBA(255, 255, 255, 22.0F * a));
      if (hovered) {
         ColorRGBA c1 = theme.getColor().withAlpha(255.0F * a);
         ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
         DrawUtil.drawRoundedRect(m, x + 2.0F, y + h - 1.5F, w - 4.0F, 1.0F, BorderRadius.all(0.5F), Gradient.of(c1, c1, c2, c2));
      }
   }

   public static boolean button(DrawContext context, int x, int y, int w, int h, boolean hovered, boolean active, float alpha) {
      if (pain()) {
         try {
            context.draw();
            painBox(context.getMatrices(), (float)x, (float)y, (float)w, (float)h, hovered && active, alpha * (active ? 1.0F : 0.45F));
            return true;
         } catch (Throwable e) {
            fail(e);
            return false;
         }
      }

      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
         BorderRadius r = BorderRadius.all(Math.min(7.0F, (float)h / 2.0F));
         float a = alpha * (active ? 1.0F : 0.5F);
         HudStyle.glass(m, (float)x, (float)y, (float)w, (float)h, r, a, false);
         if (hovered && active) {
            ColorRGBA g1 = theme.getColor().withAlpha(90.0F * a);
            ColorRGBA g2 = theme.getSecondColor().withAlpha(55.0F * a);
            DrawUtil.drawRoundedRect(m, (float)x, (float)y, (float)w, (float)h, r, Gradient.of(g1, g1, g2, g2));
            DrawUtil.drawRoundedBorder(m, (float)x, (float)y, (float)w, (float)h, -0.5F, r, new ColorRGBA(255, 255, 255, 90.0F * a));
         }

         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   /** Слайдер: стеклянная дорожка, заполненная часть цветом темы. */
   public static boolean slider(DrawContext context, int x, int y, int w, int h, double value, boolean hovered, float alpha) {
      if (pain()) {
         try {
            context.draw();
            MatrixStack m = context.getMatrices();
            Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
            painBox(m, (float)x, (float)y, (float)w, (float)h, false, alpha);
            BorderRadius r = BorderRadius.all(Math.min(R, (float)h / 2.0F));
            float fill = Math.max(4.0F, (float)((double)w * Math.max(0.0, Math.min(1.0, value))));
            ColorRGBA g1 = theme.getColor().withAlpha((hovered ? 150.0F : 110.0F) * alpha);
            ColorRGBA g2 = theme.getSecondColor().withAlpha((hovered ? 110.0F : 70.0F) * alpha);
            DrawUtil.drawRoundedRect(m, (float)x, (float)y, fill, (float)h, r, Gradient.of(g2, g2, g1, g1));
            DrawUtil.drawRoundedRect(m, (float)x + fill - 3.0F, (float)y + 2.0F, 2.0F, (float)h - 4.0F, BorderRadius.all(1.0F), new ColorRGBA(255, 255, 255, 240.0F * alpha));
            if (hovered) {
               DrawUtil.drawRoundedBorder(m, (float)x, (float)y, (float)w, (float)h, -0.5F, r, theme.getColor().withAlpha(150.0F * alpha));
            }

            return true;
         } catch (Throwable e) {
            fail(e);
            return false;
         }
      }

      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
         BorderRadius r = BorderRadius.all(Math.min(7.0F, (float)h / 2.0F));
         DrawUtil.drawRoundedRect(m, (float)x, (float)y, (float)w, (float)h, r, new ColorRGBA(20, 22, 32, 130.0F * alpha));
         DrawUtil.drawRoundedRect(m, (float)x, (float)y, (float)w, (float)h, r, new ColorRGBA(255, 255, 255, 14.0F * alpha));
         float fill = Math.max((float)h, (float)((double)w * Math.max(0.0, Math.min(1.0, value))));
         ColorRGBA g1 = theme.getColor().withAlpha((hovered ? 170.0F : 130.0F) * alpha);
         ColorRGBA g2 = theme.getSecondColor().withAlpha((hovered ? 150.0F : 110.0F) * alpha);
         DrawUtil.drawRoundedRect(m, (float)x, (float)y, fill, (float)h, r, Gradient.of(g2, g2, g1, g1));
         DrawUtil.drawRoundedRect(m, (float)x + fill - 4.0F, (float)y + 2.0F, 2.5F, (float)h - 4.0F, BorderRadius.all(1.25F), new ColorRGBA(255, 255, 255, 230.0F * alpha));
         DrawUtil.drawRoundedBorder(m, (float)x, (float)y, (float)w, (float)h, -0.5F, r, new ColorRGBA(255, 255, 255, (hovered ? 140.0F : 70.0F) * alpha));
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   /** Подложка списка (миры, серверы, настройки): тёмное стекло со скруглением. */
   public static boolean listPanel(DrawContext context, int x, int y, int w, int h) {
      if (pain()) {
         try {
            context.draw();
            MatrixStack m = context.getMatrices();
            BorderRadius r = BorderRadius.all(4.0F);
            DrawUtil.drawRoundedRect(m, (float)x + 6.0F, (float)y, (float)w - 12.0F, (float)h, r, new ColorRGBA(9, 9, 13, 175));
            DrawUtil.drawRoundedBorder(m, (float)x + 6.0F, (float)y, (float)w - 12.0F, (float)h, -0.5F, r, new ColorRGBA(255, 255, 255, 18));
            return true;
         } catch (Throwable e) {
            fail(e);
            return false;
         }
      }

      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         BorderRadius r = BorderRadius.all(8.0F);
         HudStyle.glass(m, (float)x + 6.0F, (float)y, (float)w - 12.0F, (float)h, r, 1.0F, mc.world != null);
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   /** Поле ввода: тёмная плашка, в фокусе — обод цвета темы. */
   public static boolean textField(DrawContext context, int x, int y, int w, int h, boolean focused) {
      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
         BorderRadius r = BorderRadius.all(Math.min(R, (float)h / 2.0F));
         DrawUtil.drawRoundedRect(m, (float)x, (float)y, (float)w, (float)h, r, new ColorRGBA(7, 7, 10, 230));
         DrawUtil.drawRoundedBorder(m, (float)x, (float)y, (float)w, (float)h, -0.5F, r, focused ? theme.getColor().withAlpha(200.0F) : new ColorRGBA(255, 255, 255, 30));
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   /** Галочка: квадрат со скруглением 3, отмеченная — заливка цвета темы и белая точка. */
   public static boolean checkbox(DrawContext context, int x, int y, int size, boolean checked, boolean hovered, float alpha) {
      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
         BorderRadius r = BorderRadius.all(R);
         float s = (float)size;
         if (checked) {
            ColorRGBA c1 = theme.getColor().withAlpha(230.0F * alpha);
            ColorRGBA c2 = theme.getSecondColor().withAlpha(230.0F * alpha);
            DrawUtil.drawRoundedRect(m, (float)x, (float)y, s, s, r, Gradient.of(c1, c2, c1, c2));
            DrawUtil.drawRoundedRect(m, (float)x + s * 0.32F, (float)y + s * 0.32F, s * 0.36F, s * 0.36F, BorderRadius.all(1.5F), new ColorRGBA(255, 255, 255, 245.0F * alpha));
         } else {
            DrawUtil.drawRoundedRect(m, (float)x, (float)y, s, s, r, new ColorRGBA(10, 10, 14, 220.0F * alpha));
         }

         DrawUtil.drawRoundedBorder(m, (float)x, (float)y, s, s, -0.5F, r, hovered ? theme.getColor().withAlpha(170.0F * alpha) : new ColorRGBA(255, 255, 255, 40.0F * alpha));
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   /** Подсказка (тултип): тёмная плашка с обводом цвета темы. */
   public static boolean tooltip(DrawContext context, int x, int y, int w, int h) {
      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
         BorderRadius r = BorderRadius.all(R);
         float px = (float)x - 3.0F;
         float py = (float)y - 3.0F;
         float pw = (float)w + 6.0F;
         float ph = (float)h + 6.0F;
         DrawUtil.drawShadow(m, px, py, pw, ph, 8.0F, r, new ColorRGBA(0, 0, 0, 120));
         DrawUtil.drawRoundedRect(m, px, py, pw, ph, r, new ColorRGBA(10, 10, 14, 240));
         DrawUtil.drawRoundedBorder(m, px, py, pw, ph, -0.5F, r, theme.getColor().withAlpha(110.0F));
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   /** Затемнение за меню в игре: ровный тёмный градиент и лёгкий отсвет цвета темы снизу. */
   public static boolean inGameBackground(DrawContext context, int width, int height) {
      try {
         context.draw();
         MatrixStack m = context.getMatrices();
         Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
         ColorRGBA top = new ColorRGBA(8, 8, 12, 150);
         ColorRGBA bottom = new ColorRGBA(8, 8, 12, 205);
         DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, (float)width, (float)height, BorderRadius.ZERO, Gradient.of(top, bottom, top, bottom));
         ColorRGBA clear = theme.getColor().withAlpha(0);
         ColorRGBA glow = theme.getColor().withAlpha(26);
         DrawUtil.drawRoundedRect(m, 0.0F, (float)height * 0.6F, (float)width, (float)height * 0.4F, BorderRadius.ZERO, Gradient.of(clear, glow, clear, glow));
         return true;
      } catch (Throwable e) {
         fail(e);
         return false;
      }
   }

   public static boolean painLook() {
      return enabled() && pain();
   }
}
