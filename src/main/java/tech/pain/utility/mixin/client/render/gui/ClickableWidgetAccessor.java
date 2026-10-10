package tech.pain.utility.mixin.client.render.gui;

import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClickableWidget.class})
public interface ClickableWidgetAccessor {
   @Accessor("alpha")
   float pain$getAlpha();
}
