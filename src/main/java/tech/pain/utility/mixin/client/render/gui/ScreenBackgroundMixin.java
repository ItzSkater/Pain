package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.screens.title.MenuStyle;

/** Обои Pain вместо ванильной панорамы на экранах вне мира. */
@Mixin({Screen.class})
public class ScreenBackgroundMixin {
   @Shadow
   public int width;
   @Shadow
   public int height;

   @Inject(method = {"renderBackground"}, at = {@At("HEAD")}, cancellable = true)
   private void painWallpaper(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (MenuStyle.wallpaperEnabled() && MenuStyle.wallpaper(context, this.width, this.height)) {
         ci.cancel();
      }
   }
}
