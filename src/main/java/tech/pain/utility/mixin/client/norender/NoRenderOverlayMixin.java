package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.gui.hud.InGameOverlayRenderer.class)
public abstract class NoRenderOverlayMixin {
   @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private static void pain$water(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Вода на экране")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private static void pain$inWall(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Блок в голове")) {
         ci.cancel();
      }
   }

}
