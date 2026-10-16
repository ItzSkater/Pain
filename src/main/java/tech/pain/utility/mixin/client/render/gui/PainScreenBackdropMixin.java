package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.screens.title.MenuStyle;

/** Затемнение за меню в игре (пауза, настройки, чат-экраны) — свой градиент вместо серого. */
@Mixin(Screen.class)
public abstract class PainScreenBackdropMixin {
   @Shadow
   public int width;
   @Shadow
   public int height;

   @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$backdrop(DrawContext context, CallbackInfo ci) {
      if (MenuStyle.painLook() && MenuStyle.inGameBackground(context, this.width, this.height)) {
         ci.cancel();
      }
   }
}
