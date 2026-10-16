package tech.pain.client.hud;

import net.minecraft.client.util.math.MatrixStack;
import tech.pain.Pain;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.impl.render.Interface;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.Gradient;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Единая точка стиля HUD. Classic — старый вид 1:1 (серый блюр), Modern — новый дизайн:
 * тёмные панели с мягкой тенью и градиентной акцент-полосой цвета темы.
 */
public final class HudStyle {
   private HudStyle() {
   }

   public static boolean isModern() {
      return Interface.INSTANCE != null && Interface.INSTANCE.style.is("Modern");
   }

   /** Раскладка «Cool» — общая для стилей Cool и Liquid Glass, отличается только материал панелей. */
   public static boolean isRock() {
      return Interface.INSTANCE != null && (Interface.INSTANCE.style.is("Cool") || Interface.INSTANCE.style.is("Liquid Glass") || isNewLiquid() || isNewBlack());
   }

   public static boolean isNewLiquid() {
      return Interface.INSTANCE != null && Interface.INSTANCE.style.is("New Liquid");
   }

   public static boolean isNewBlack() {
      return Interface.INSTANCE != null && Interface.INSTANCE.style.is("New Black");
   }

   /**
    * New Liquid: капля жидкого стекла. Фон размыт, заливка холодная и прозрачная, внутри снизу
    * мягко светится цвет темы, сверху выпуклый блик дугой, обод ярче сверху и гаснет книзу.
    */
   public static void newLiquidPanel(MatrixStack matrices, float x, float y, float width, float height, float alpha) {
      BorderRadius radius = BorderRadius.all(Math.min(5.0F, height / 2.0F));
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      ColorRGBA clear = new ColorRGBA(255, 255, 255, 0);
      DrawUtil.drawShadow(matrices, x, y + 1.0F, width, height, 14.0F, radius, new ColorRGBA(0, 0, 0, 80.0F * alpha));
      DrawUtil.drawBlur(matrices, x, y, width, height, 16.0F, radius, new ColorRGBA(255, 255, 255, 255.0F * alpha));
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(18, 22, 32, 95.0F * alpha));
      // внутреннее свечение темы снизу
      ColorRGBA glow = theme.getColor().withAlpha(34.0F * alpha);
      ColorRGBA glow2 = theme.getSecondColor().withAlpha(26.0F * alpha);
      DrawUtil.drawRoundedRect(matrices, x, y + height * 0.35F, width, height * 0.65F, BorderRadius.bottom(radius.bottomLeftRadius(), radius.bottomRightRadius()), Gradient.of(clear, glow, clear, glow2));
      // выпуклый блик сверху
      ColorRGBA hi = new ColorRGBA(255, 255, 255, 58.0F * alpha);
      DrawUtil.drawRoundedRect(matrices, x + 1.0F, y + 0.75F, width - 2.0F, Math.min(height * 0.45F, 11.0F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(hi, clear, hi, clear));
      ColorRGBA edge = new ColorRGBA(255, 255, 255, 135.0F * alpha);
      DrawUtil.drawRoundedRect(matrices, x + radius.topLeftRadius() * 0.6F, y + 0.6F, width - radius.topLeftRadius() * 1.2F, 0.8F, BorderRadius.all(0.4F), Gradient.of(clear, clear, edge, edge));
      DrawUtil.drawRoundedBorder(matrices, x, y, width, height, -0.5F, radius, new ColorRGBA(255, 255, 255, 70.0F * alpha));
   }

   /**
    * New Black: глубокий чёрный без размытия (и дешевле), резкие углы, тонкий обод
    * и короткая светящаяся полоса цвета темы по верхней кромке.
    */
   public static void newBlackPanel(MatrixStack matrices, float x, float y, float width, float height, float alpha) {
      BorderRadius radius = BorderRadius.all(Math.min(3.0F, height / 2.0F));
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      DrawUtil.drawShadow(matrices, x, y + 1.0F, width, height, 10.0F, radius, new ColorRGBA(0, 0, 0, 130.0F * alpha));
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(6, 6, 8, 236.0F * alpha));
      ColorRGBA sheen = new ColorRGBA(255, 255, 255, 7.0F * alpha);
      DrawUtil.drawRoundedRect(matrices, x + 0.5F, y + 0.5F, width - 1.0F, Math.min(height / 2.0F, 6.0F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(sheen, new ColorRGBA(255, 255, 255, 0), sheen, new ColorRGBA(255, 255, 255, 0)));
      if (width > 16.0F && height > 6.0F) {
         ColorRGBA c1 = theme.getColor().withAlpha(255.0F * alpha);
         ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * alpha);
         float lw = Math.min(width * 0.4F, 28.0F);
         DrawUtil.drawShadow(matrices, x + radius.topLeftRadius() + 1.0F, y - 0.25F, lw, 1.0F, 5.0F, BorderRadius.all(0.5F), theme.getColor().withAlpha(150.0F * alpha));
         DrawUtil.drawRoundedRect(matrices, x + radius.topLeftRadius() + 1.0F, y - 0.25F, lw, 1.0F, BorderRadius.all(0.5F), Gradient.of(c1, c1, c2, c2));
      }

      DrawUtil.drawRoundedBorder(matrices, x, y, width, height, -0.5F, radius, new ColorRGBA(255, 255, 255, 16.0F * alpha));
   }

   public static boolean isGlass() {
      return Interface.INSTANCE != null && Interface.INSTANCE.style.is("Liquid Glass");
   }

   /** Liquid Glass для панелей HUD. */
   public static void glassPanel(MatrixStack matrices, float x, float y, float width, float height, float alpha) {
      glass(matrices, x, y, width, height, BorderRadius.all(Math.min(8.0F, height / 2.0F)), alpha, true);
   }

   /**
    * Стекло двух видов (модуль CoolInterface):
    * матовое — фон сильно размыт, плотная молочная заливка, мягкий блик;
    * глянцевое — почти прозрачное, фон едва размыт, резкий блик сверху, яркий обод и отсвет снизу.
    * @param blur размывать ли фон (для мелких кнопок меню игры выключено — иначе дорого)
    */
   public static void glass(MatrixStack matrices, float x, float y, float width, float height, BorderRadius radius, float alpha, boolean blur) {
      if (alpha <= 0.01F || width <= 0.0F || height <= 0.0F) {
         return;
      }

      tech.pain.client.modules.impl.render.CoolInterface cool = tech.pain.client.modules.impl.render.CoolInterface.INSTANCE;
      boolean glossy = cool.glossy();
      float d = cool.density();
      DrawUtil.drawShadow(matrices, x, y, width, height, glossy ? 10.0F : 14.0F, radius, new ColorRGBA(0, 0, 0, (glossy ? 45.0F : 70.0F) * alpha));
      if (blur) {
         DrawUtil.drawBlur(matrices, x, y, width, height, glossy ? 3.0F : 20.0F, radius, new ColorRGBA(255, 255, 255, 255.0F * alpha));
      }

      ColorRGBA clear = new ColorRGBA(255, 255, 255, 0);
      if (glossy) {
         // прозрачное стекло: лёгкая тень для читаемости текста и едва заметный холодный тон
         DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(16, 20, 30, (18.0F + 90.0F * d) * alpha));
         DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(210, 225, 255, 9.0F * alpha));
         // резкий блик: яркая полоса у верхней кромки и широкий мягкий отсвет под ней
         ColorRGBA edge = new ColorRGBA(255, 255, 255, 120.0F * alpha);
         DrawUtil.drawRoundedRect(matrices, x + 1.0F, y + 0.5F, width - 2.0F, Math.min(height * 0.18F, 2.5F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(edge, clear, edge, clear));
         ColorRGBA sheen = new ColorRGBA(255, 255, 255, 30.0F * alpha);
         DrawUtil.drawRoundedRect(matrices, x + 0.75F, y + 0.5F, width - 1.5F, Math.min(height * 0.55F, 14.0F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(sheen, clear, sheen, clear));
         ColorRGBA low = new ColorRGBA(255, 255, 255, 34.0F * alpha);
         float lowH = Math.min(height * 0.3F, 6.0F);
         DrawUtil.drawRoundedRect(matrices, x + 0.75F, y + height - lowH - 0.5F, width - 1.5F, lowH, BorderRadius.bottom(radius.bottomLeftRadius(), radius.bottomRightRadius()), Gradient.of(clear, low, clear, low));
         DrawUtil.drawRoundedBorder(matrices, x, y, width, height, -0.5F, radius, new ColorRGBA(255, 255, 255, 150.0F * alpha));
      } else {
         // матовое стекло: плотнее и «молочнее», блик мягкий
         DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(24, 28, 38, (35.0F + 110.0F * d) * alpha));
         DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(255, 255, 255, (16.0F + 16.0F * d) * alpha));
         ColorRGBA hi = new ColorRGBA(255, 255, 255, 42.0F * alpha);
         DrawUtil.drawRoundedRect(matrices, x + 0.75F, y + 0.5F, width - 1.5F, Math.min(height * 0.5F, 9.0F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(hi, clear, hi, clear));
         ColorRGBA low = new ColorRGBA(255, 255, 255, 14.0F * alpha);
         float lowH = Math.min(height * 0.3F, 5.0F);
         DrawUtil.drawRoundedRect(matrices, x + 0.75F, y + height - lowH - 0.5F, width - 1.5F, lowH, BorderRadius.bottom(radius.bottomLeftRadius(), radius.bottomRightRadius()), Gradient.of(clear, low, clear, low));
         DrawUtil.drawRoundedBorder(matrices, x, y, width, height, -0.5F, radius, new ColorRGBA(255, 255, 255, 90.0F * alpha));
      }
   }

   /** Панель Cool: блюр + тёмная заливка, крупное скругление, тонкий светлый обод. */
   public static void rockPanel(MatrixStack matrices, float x, float y, float width, float height, float alpha) {
      if (alpha <= 0.01F || width <= 0.0F || height <= 0.0F) {
         return;
      }

      if (isGlass()) {
         glassPanel(matrices, x, y, width, height, alpha);
         return;
      }

      if (isNewLiquid()) {
         newLiquidPanel(matrices, x, y, width, height, alpha);
         return;
      }

      if (isNewBlack()) {
         newBlackPanel(matrices, x, y, width, height, alpha);
         return;
      }

      BorderRadius radius = BorderRadius.all(Math.min(6.0F, height / 2.0F));
      DrawUtil.drawShadow(matrices, x, y, width, height, 10.0F, radius, new ColorRGBA(0, 0, 0, 110.0F * alpha));
      DrawUtil.drawBlur(matrices, x, y, width, height, 12.0F, radius, new ColorRGBA(255, 255, 255, 255.0F * alpha));
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(12, 12, 16, 200.0F * alpha));
      DrawUtil.drawRoundedRect(matrices, x + 1.0F, y + 0.5F, width - 2.0F, Math.min(height / 2.0F, 7.0F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(new ColorRGBA(255, 255, 255, 9.0F * alpha), new ColorRGBA(255, 255, 255, 0), new ColorRGBA(255, 255, 255, 9.0F * alpha), new ColorRGBA(255, 255, 255, 0)));
      DrawUtil.drawRoundedBorder(matrices, x, y, width, height, -0.5F, radius, new ColorRGBA(255, 255, 255, 24.0F * alpha));
   }

   public static void panel(MatrixStack matrices, float x, float y, float width, float height, float blurRadius, BorderRadius radius, ColorRGBA color) {
      if (isRock()) {
         rockPanel(matrices, x, y, width, height, color.getAlpha() / 255.0F);
         return;
      }

      if (!isModern()) {
         DrawUtil.drawBlur(matrices, x, y, width, height, blurRadius, radius, color);
         return;
      }

      float a = color.getAlpha() / 255.0F;
      if (a <= 0.01F || width <= 0.0F || height <= 0.0F) {
         return;
      }

      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      DrawUtil.drawShadow(matrices, x, y, width, height, 8.0F, radius, new ColorRGBA(0, 0, 0, 95.0F * a));
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(15, 15, 19, 222.0F * a));
      if (height >= 6.0F && width >= 12.0F) {
         ColorRGBA top = theme.getColor().withAlpha(255.0F * a);
         ColorRGBA bottom = theme.getSecondColor().withAlpha(255.0F * a);
         DrawUtil.drawRoundedRect(matrices, x + 1.0F, y + 2.5F, 1.5F, height - 5.0F, BorderRadius.all(0.75F), Gradient.of(top, bottom, top, bottom));
      }
   }

   public static void separator(MatrixStack matrices, float x, float y, float width, float height, BorderRadius radius, ColorRGBA color) {
      if (isRock()) {
         DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(255, 255, 255, 20.0F * (color.getAlpha() / 255.0F)));
         return;
      }

      if (!isModern()) {
         DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, color);
         return;
      }

      float a = color.getAlpha() / 255.0F;
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, theme.getColor().withAlpha(150.0F * a));
   }

   public static ColorRGBA title(ColorRGBA classic) {
      return isModern() || isRock() ? Pain.getInstance().getThemeManager().getCurrentTheme().getColor().withAlpha((float)classic.getAlpha()) : classic;
   }
}
