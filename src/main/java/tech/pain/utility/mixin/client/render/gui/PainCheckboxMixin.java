package tech.pain.utility.mixin.client.render.gui;

import java.util.function.Function;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tech.pain.client.screens.title.MenuStyle;

/** Галочка в виде «Pain»: свой квадрат вместо ванильной текстуры, подпись рисует игра. */
@Mixin(CheckboxWidget.class)
public abstract class PainCheckboxMixin {
   @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIII)V"), require = 0)
   private void pain$box(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier sprite, int x, int y, int w, int h, int color) {
      CheckboxWidget self = (CheckboxWidget)(Object)this;
      float alpha = (float)(color >>> 24 & 255) / 255.0F;
      if (!MenuStyle.painLook() || !MenuStyle.checkbox(context, x, y, Math.min(w, h), self.isChecked(), self.isHovered() || self.isFocused(), alpha)) {
         context.drawGuiTexture(layers, sprite, x, y, w, h, color);
      }
   }
}
