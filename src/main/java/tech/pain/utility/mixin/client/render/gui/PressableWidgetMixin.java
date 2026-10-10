package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.screens.title.MenuStyle;

/** Стеклянные кнопки вместо серых текстур. Текст рисует сама игра — шрифт и переводы прежние. */
@Mixin({PressableWidget.class})
public abstract class PressableWidgetMixin {
   @Shadow
   public abstract void drawMessage(DrawContext context, TextRenderer textRenderer, int color);

   @Inject(method = {"renderWidget"}, at = {@At("HEAD")}, cancellable = true)
   private void painButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!MenuStyle.enabled()) {
         return;
      }

      PressableWidget self = (PressableWidget)(Object)this;
      float alpha = ((ClickableWidgetAccessor)self).pain$getAlpha();
      if (MenuStyle.button(context, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.isSelected(), self.active, alpha)) {
         int color = self.active ? 0xFFFFFF : 0xA0A0A0;
         this.drawMessage(context, MinecraftClient.getInstance().textRenderer, color | (int)(alpha * 255.0F) << 24);
         ci.cancel();
      }
   }
}
