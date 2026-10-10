package tech.pain.utility.mixin.minecraft.render;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.SkyShader;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
   @Inject(method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/util/math/Vec3d;F)V", at = @At("HEAD"), cancellable = true)
   private void pain$shaderClouds(int color, CloudRenderMode mode, float height, Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d cameraPos, float ticks, CallbackInfo ci) {
      if (SkyShader.INSTANCE.ownClouds()) {
         ci.cancel();
      }
   }
}
