package tech.pain.utility.mixin.client;

import com.darkmagician6.eventapi.EventManager;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.render.BackgroundRenderer.FogType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.base.events.impl.render.EventFog;
import tech.pain.client.modules.impl.render.NoRender;
import tech.pain.utility.render.display.base.color.ColorUtil;

@Mixin({BackgroundRenderer.class})
public class BackGroundRendererMixin {
   @Inject(
      method = {"getFogModifier(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/BackgroundRenderer$StatusEffectFogModifier;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onGetFogModifier(Entity entity, float tickDelta, CallbackInfoReturnable<Object> info) {
      NoRender noRender = NoRender.INSTANCE;
      if (noRender.isRemoveBadEffect()) {
         info.setReturnValue((Object)null);
      }

   }

   @Inject(
      method = {"getFogColor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void getFogColorHook(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
      EventFog event = new EventFog();
      EventManager.call(event);
      if (event.isCancelled()) {
         int color = event.getColor();
         cir.setReturnValue(new Vector4f(ColorUtil.redf(color), ColorUtil.greenf(color), ColorUtil.bluef(color), ColorUtil.alphaf(color)));
      } else if (tech.pain.client.modules.impl.render.SkyShader.INSTANCE.ownClouds() && camera.getSubmersionType() == net.minecraft.block.enums.CameraSubmersionType.NONE && world.getDimension().hasSkyLight()) {
         // под небом «Атмосфера» туман берёт цвет его горизонта, иначе даль и небо расходятся в оттенке
         float[] c = tech.pain.client.modules.impl.render.SkyShader.INSTANCE.horizonColor(tickDelta);
         cir.setReturnValue(new Vector4f(c[0], c[1], c[2], 1.0F));
      }

   }

   @Inject(
      method = {"applyFog"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void modifyFog(Camera camera, FogType fogType, Vector4f color, float viewDistance, boolean thickenFog, float tickDelta, CallbackInfoReturnable<Fog> cir) {
      EventFog event = new EventFog();
      EventManager.call(event);
      if (event.isCancelled()) {
         int color1 = event.getColor();
         cir.setReturnValue(new Fog(2.0F, event.getDistance(), FogShape.CYLINDER, ColorUtil.redf(color1), ColorUtil.greenf(color1), ColorUtil.bluef(color1), ColorUtil.alphaf(color1)));
      } else {
         // дымка дождя и снега из модуля Weather: даль тонет в сером
         float mist = tech.pain.client.modules.impl.render.WeatherControl.INSTANCE.mistAmount();
         if (mist > 0.01F && fogType == FogType.FOG_TERRAIN && camera.getSubmersionType() == net.minecraft.block.enums.CameraSubmersionType.NONE) {
            float end = viewDistance + (Math.min(viewDistance, 72.0F) - viewDistance) * mist;
            float grey = 0.3F * color.x + 0.59F * color.y + 0.11F * color.z;
            float k = mist * 0.7F;
            cir.setReturnValue(new Fog(end * (1.0F - 0.92F * mist), end, FogShape.CYLINDER, color.x + (grey * 0.95F - color.x) * k, color.y + (grey - color.y) * k, color.z + (grey * 1.06F - color.z) * k, color.w));
         }
      }

   }
}
