package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.screens.title.MenuStyle;

/** Подсказки при наведении — тёмная плашка «Pain» вместо фиолетовой рамки. */
@Mixin(TooltipBackgroundRenderer.class)
public abstract class PainTooltipMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
   private static void pain$tooltip(DrawContext context, int x, int y, int width, int height, int z, Identifier texture, CallbackInfo ci) {
      if (MenuStyle.painLook()) {
         context.getMatrices().push();
         context.getMatrices().translate(0.0F, 0.0F, (float)z);
         boolean done = MenuStyle.tooltip(context, x, y, width, height);
         context.getMatrices().pop();
         if (done) {
            ci.cancel();
         }
      }
   }
}
