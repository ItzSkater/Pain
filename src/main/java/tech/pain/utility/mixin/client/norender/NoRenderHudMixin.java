package tech.pain.utility.mixin.client.norender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tech.pain.client.modules.impl.render.NoRender;

// require = 0: если цель не нашлась, пункт просто не сработает, а игра не упадёт
@Mixin(net.minecraft.client.gui.hud.InGameHud.class)
public abstract class NoRenderHudMixin {
   @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$portal(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Портал и тошнота")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$nausea(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Портал и тошнота")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$vignette(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Виньетка")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$spyglass(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Подзорная труба")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$scoreboard(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Скорборд")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$title(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Титры")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$effects(CallbackInfo ci) {
      if (NoRender.INSTANCE.hide("Иконки эффектов")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
   private void pain$frost(net.minecraft.client.gui.DrawContext context, net.minecraft.util.Identifier texture, float opacity, CallbackInfo ci) {
      if (texture.getPath().contains("powder_snow_outline") && NoRender.INSTANCE.hide("Иней")) {
         ci.cancel();
      }
   }

}
