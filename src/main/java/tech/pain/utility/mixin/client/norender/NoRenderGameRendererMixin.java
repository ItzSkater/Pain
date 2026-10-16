package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.render.GameRenderer.class)
public abstract class NoRenderGameRendererMixin {
   @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$hurtTilt(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Тряска от урона")) {
         ci.cancel();
      }
   }

   @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$totem(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Анимация тотема")) {
         ci.cancel();
      }
   }

}
