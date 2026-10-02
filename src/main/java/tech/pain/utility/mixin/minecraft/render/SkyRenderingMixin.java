package tech.pain.utility.mixin.minecraft.render;

import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.SkyShader;

/** Модуль SkyShader: вместо ванильного неба рисуется свой купол с картинкой. */
@Mixin({SkyRendering.class})
public class SkyRenderingMixin {
   @Inject(method = {"renderSky"}, at = {@At("HEAD")}, cancellable = true)
   private void painSky(float red, float green, float blue, CallbackInfo ci) {
      if (SkyShader.INSTANCE.isEnabled()) {
         SkyShader.INSTANCE.drawDome();
         ci.cancel();
      }
   }

   @Inject(method = {"renderEndSky"}, at = {@At("HEAD")}, cancellable = true)
   private void painEndSky(CallbackInfo ci) {
      if (SkyShader.INSTANCE.isEnabled()) {
         SkyShader.INSTANCE.drawDome();
         ci.cancel();
      }
   }

   @Inject(method = {"renderCelestialBodies"}, at = {@At("HEAD")}, cancellable = true)
   private void painCelestial(CallbackInfo ci) {
      if (SkyShader.INSTANCE.isEnabled() && SkyShader.INSTANCE.hideCelestial()) {
         ci.cancel();
      }
   }

   @Inject(method = {"renderGlowingSky"}, at = {@At("HEAD")}, cancellable = true)
   private void painGlow(CallbackInfo ci) {
      if (SkyShader.INSTANCE.isEnabled()) {
         ci.cancel();
      }
   }

   @Inject(method = {"renderSkyDark"}, at = {@At("HEAD")}, cancellable = true)
   private void painDark(CallbackInfo ci) {
      if (SkyShader.INSTANCE.isEnabled()) {
         ci.cancel();
      }
   }
}
