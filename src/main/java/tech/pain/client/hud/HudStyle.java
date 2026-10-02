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
      return Interface.INSTANCE != null && (Interface.INSTANCE.style.is("Cool") || Interface.INSTANCE.style.is("Liquid Glass"));
   }

   public static boolean isGlass() {
      return Interface.INSTANCE != null && Interface.INSTANCE.style.is("Liquid Glass");
   }

   /** Liquid Glass: сильный блюр, полупрозрачное «стекло», блик сверху и яркий светлый обод. */
   public static void glassPanel(MatrixStack matrices, float x, float y, float width, float height, float alpha) {
      BorderRadius radius = BorderRadius.all(Math.min(8.0F, height / 2.0F));
      DrawUtil.drawShadow(matrices, x, y, width, height, 14.0F, radius, new ColorRGBA(0, 0, 0, 70.0F * alpha));
      DrawUtil.drawBlur(matrices, x, y, width, height, 18.0F, radius, new ColorRGBA(255, 255, 255, 255.0F * alpha));
      // лёгкое затемнение, чтобы белый текст читался на светлом фоне, и белёсый тон стекла
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(24, 28, 38, 72.0F * alpha));
      DrawUtil.drawRoundedRect(matrices, x, y, width, height, radius, new ColorRGBA(255, 255, 255, 22.0F * alpha));
      ColorRGBA hi = new ColorRGBA(255, 255, 255, 50.0F * alpha);
      ColorRGBA clear = new ColorRGBA(255, 255, 255, 0);
      DrawUtil.drawRoundedRect(matrices, x + 0.75F, y + 0.5F, width - 1.5F, Math.min(height * 0.5F, 9.0F), BorderRadius.top(radius.topLeftRadius(), radius.topRightRadius()), Gradient.of(hi, clear, hi, clear));
      ColorRGBA low = new ColorRGBA(255, 255, 255, 16.0F * alpha);
      float lowH = Math.min(height * 0.3F, 5.0F);
      DrawUtil.drawRoundedRect(matrices, x + 0.75F, y + height - lowH - 0.5F, width - 1.5F, lowH, BorderRadius.bottom(radius.bottomLeftRadius(), radius.bottomRightRadius()), Gradient.of(clear, low, clear, low));
      DrawUtil.drawRoundedBorder(matrices, x, y, width, height, -0.5F, radius, new ColorRGBA(255, 255, 255, 100.0F * alpha));
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
