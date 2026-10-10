package tech.pain.utility.mixin.minecraft.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.WeatherControl;

@Mixin(Biome.class)
public class BiomeWeatherMixin {
   // только поток отрисовки: в одиночной игре сервер живёт в этом же процессе, и ему подмену видеть нельзя,
   // иначе в мире начнёт ложиться настоящий снег
   @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
   private void pain$snow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
      if (!RenderSystem.isOnRenderThread()) {
         return;
      }

      if (WeatherControl.INSTANCE.forceSnow()) {
         cir.setReturnValue(Biome.Precipitation.SNOW);
      } else if (WeatherControl.INSTANCE.forcePrecipitation()) {
         cir.setReturnValue(Biome.Precipitation.RAIN);
      }
   }

   @Inject(method = "hasPrecipitation", at = @At("HEAD"), cancellable = true)
   private void pain$dry(CallbackInfoReturnable<Boolean> cir) {
      if (RenderSystem.isOnRenderThread() && WeatherControl.INSTANCE.forcePrecipitation()) {
         cir.setReturnValue(true);
      }
   }
}
