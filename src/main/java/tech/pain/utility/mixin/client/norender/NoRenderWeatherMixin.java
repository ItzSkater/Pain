package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.render.WeatherRendering.class)
public abstract class NoRenderWeatherMixin {
   @Inject(method = "renderPrecipitation(Lnet/minecraft/world/World;Lnet/minecraft/client/render/VertexConsumerProvider;IFLnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$rain(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Погода")) {
         ci.cancel();
      }
   }

   @Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$rainSplash(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Погода")) {
         ci.cancel();
      }
   }

}
