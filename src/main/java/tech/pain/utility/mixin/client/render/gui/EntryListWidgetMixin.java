package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.screens.title.MenuStyle;

/** Списки миров, серверов и настроек: стеклянная подложка вместо тёмной текстуры и полос. */
@Mixin({EntryListWidget.class})
public abstract class EntryListWidgetMixin {
   @Inject(method = {"drawMenuListBackground"}, at = {@At("HEAD")}, cancellable = true)
   private void painListBackground(DrawContext context, CallbackInfo ci) {
      if (!MenuStyle.enabled()) {
         return;
      }

      EntryListWidget<?> self = (EntryListWidget<?>)(Object)this;
      if (MenuStyle.listPanel(context, self.getX(), self.getY(), self.getWidth(), self.getHeight())) {
         ci.cancel();
      }
   }

   @Inject(method = {"drawHeaderAndFooterSeparators"}, at = {@At("HEAD")}, cancellable = true)
   private void painListSeparators(DrawContext context, CallbackInfo ci) {
      if (MenuStyle.enabled()) {
         ci.cancel();
      }
   }
}
