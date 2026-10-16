package tech.pain.utility.mixin.client.render.gui;

import java.util.function.Function;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import tech.pain.client.screens.title.MenuStyle;

/** Поле ввода в виде «Pain»: вместо ванильной текстуры рамки — своя плашка. Текст и курсор рисует игра. */
@Mixin(TextFieldWidget.class)
public abstract class PainWidgetsMixin {
   @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"), require = 0)
   private void pain$field(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier sprite, int x, int y, int w, int h) {
      TextFieldWidget self = (TextFieldWidget)(Object)this;
      if (!MenuStyle.painLook() || !MenuStyle.textField(context, x, y, w, h, self.isFocused())) {
         context.drawGuiTexture(layers, sprite, x, y, w, h);
      }
   }
}
