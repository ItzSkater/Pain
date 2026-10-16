package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.render.entity.EntityRenderDispatcher.class)
public abstract class NoRenderEntityMixin {
   @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$entityFire(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Огонь на сущностях")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
   private static void pain$shadow(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Тени сущностей")) {
         ci.cancel();
      }
   }

}
