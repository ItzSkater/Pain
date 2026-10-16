package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer.class)
public abstract class NoRenderBeaconMixin {
   @Inject(method = "render(Lnet/minecraft/block/entity/BeaconBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$beam(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Луч маяка")) {
         ci.cancel();
      }
   }

}
