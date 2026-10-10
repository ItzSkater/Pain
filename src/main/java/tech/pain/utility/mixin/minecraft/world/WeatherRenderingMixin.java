package tech.pain.utility.mixin.minecraft.world;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.WeatherControl;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
   // в реалистичном режиме осадки рисует сам модуль, ванильные полотна дождя и снега убираем
   @Inject(method = "renderPrecipitation(Lnet/minecraft/world/World;Lnet/minecraft/client/render/VertexConsumerProvider;IFLnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), cancellable = true)
   private void pain$ownWeather(World world, VertexConsumerProvider vertexConsumers, int ticks, float delta, Vec3d pos, CallbackInfo ci) {
      if (WeatherControl.INSTANCE.realistic()) {
         ci.cancel();
      }
   }

   // ванильные брызги и дымок от дождя тоже не нужны — всплески рисует модуль; звук дождя остаётся
   @Redirect(method = "addParticlesAndSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"))
   private void pain$noVanillaSplash(ClientWorld world, ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz) {
      if (!WeatherControl.INSTANCE.realistic()) {
         world.addParticle(effect, x, y, z, vx, vy, vz);
      }
   }
}
