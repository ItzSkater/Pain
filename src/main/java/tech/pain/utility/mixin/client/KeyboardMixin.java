package tech.pain.utility.mixin.client;

import com.darkmagician6.eventapi.EventManager;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.base.events.impl.input.EventKey;

@Mixin({Keyboard.class})
public class KeyboardMixin {
   @Inject(
           method = {"onKey"},
           at = {@At("HEAD")},
           cancellable = true
   )
   public void triggerKeyEvent(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
      if (key != -1 && tech.pain.client.modules.impl.render.Menu3D.INSTANCE.handleKey(key, scancode, action)) {
         ci.cancel();
         return;
      }

      if (key != -1) {
         EventManager.call(new EventKey(action, key));
      }
   }
}