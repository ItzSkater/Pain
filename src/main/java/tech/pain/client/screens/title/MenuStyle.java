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

   public static boolean button(DrawContext context, int x, int y, int w, int h, boolean hovered, boolean active, float alpha) {
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
}
