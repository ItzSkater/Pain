package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.Cosmetics;
import tech.pain.client.modules.impl.render.Emotes;

/** Своему игроку: плащ из Cosmetics и вращение из Emotes — правкой готового состояния отрисовки. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PainPlayerLookMixin {
   @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"), require = 0)
   private void pain$look(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
      Cosmetics.INSTANCE.applyCape(state);
      Emotes.INSTANCE.applySpin(state);
   }
}
