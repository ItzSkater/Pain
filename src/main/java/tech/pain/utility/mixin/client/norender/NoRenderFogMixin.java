package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import org.joml.Vector4f;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.render.BackgroundRenderer.class)
public abstract class NoRenderFogMixin {
   @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true, require = 0)
   private static void pain$noFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickenFog, float tickDelta, CallbackInfoReturnable<Fog> cir) {
      boolean inFluid = camera.getSubmersionType() != CameraSubmersionType.NONE;
      if (NoRender.INSTANCE.hide(inFluid ? "Туман под водой" : "Туман")) {
         // туман начинается далеко за дальностью прорисовки, то есть его не видно
         cir.setReturnValue(new Fog(viewDistance * 8.0F, viewDistance * 9.0F, FogShape.SPHERE, color.x, color.y, color.z, color.w));
      }
   }

}
