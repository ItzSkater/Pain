package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.screens.title.MenuStyle;

/** Стеклянные слайдеры в настройках. */
@Mixin({SliderWidget.class})
public abstract class SliderWidgetMixin {
   @Shadow
   protected double value;

   @Inject(method = {"renderWidget"}, at = {@At("HEAD")}, cancellable = true)
   private void painSlider(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!MenuStyle.enabled()) {
         return;
      }

      SliderWidget self = (SliderWidget)(Object)this;
      float alpha = ((ClickableWidgetAccessor)self).pain$getAlpha();
      if (MenuStyle.slider(context, self.getX(), self.getY(), self.getWidth(), self.getHeight(), this.value, self.isHovered() || self.isFocused(), alpha)) {
         int color = self.active ? 0xFFFFFF : 0xA0A0A0;
         context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, self.getMessage(), self.getX() + self.getWidth() / 2, self.getY() + (self.getHeight() - 8) / 2, color | (int)(alpha * 255.0F) << 24);
         ci.cancel();
      }
   }
}
