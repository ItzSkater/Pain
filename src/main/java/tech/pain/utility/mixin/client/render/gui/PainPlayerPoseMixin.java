package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.Emotes;

/** Поза эмоции поверх ванильной позы модели игрока. */
@Mixin(PlayerEntityModel.class)
public abstract class PainPlayerPoseMixin {
   @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"), require = 0)
   private void pain$emote(PlayerEntityRenderState state, CallbackInfo ci) {
      Emotes.INSTANCE.apply((PlayerEntityModel)(Object)this, state);
   }
}
