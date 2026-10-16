package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.particle.ParticleManager.class)
public abstract class NoRenderParticleMixin {
   @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$filter(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
      NoRender module = NoRender.INSTANCE;
      if (!module.isEnabled()) {
         return;
      }

      ParticleType<?> type = effect.getType();
      boolean explosion = type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER;
      boolean potion = type == ParticleTypes.ENTITY_EFFECT || type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT;
      // барьеры и блоки света показываются частицей-маркером
      boolean marker = type == ParticleTypes.BLOCK_MARKER;
      boolean block = type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_CRUMBLE || type == ParticleTypes.FALLING_DUST;
      if (module.hide("Все частицы")
         || explosion && module.hide("Взрывы")
         || potion && module.hide("Частицы зелий")
         || marker && module.hide("Барьеры")
         || block && module.hide("Частицы блоков")) {
         cir.setReturnValue(null);
      }
   }

   @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$blockBreak(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Частицы блоков")) {
         ci.cancel();
      }
   }

   @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$blockBreaking(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Частицы блоков")) {
         ci.cancel();
      }
   }

}
