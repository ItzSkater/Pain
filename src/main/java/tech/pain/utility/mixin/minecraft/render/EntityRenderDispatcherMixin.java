package tech.pain.utility.mixin.minecraft.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.ClawdSkin;

@Mixin({EntityRenderDispatcher.class})
public class EntityRenderDispatcherMixin {
   /** Модуль Claw'd: обычную модель игрока не рисуем, вместо неё модуль рисует Claw'd. */
   @Inject(
      method = {"render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void painClawdSkin(Entity entity, double x, double y, double z, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
      if (ClawdSkin.INSTANCE.replaces(entity)) {
         ci.cancel();
      }
   }
}
