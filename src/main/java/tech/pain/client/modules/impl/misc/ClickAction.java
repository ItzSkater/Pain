package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import tech.pain.Pain;
import tech.pain.base.events.impl.input.EventKey;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.KeySetting;

@ModuleAnnotation(
   name = "ClickFriend",
   description = "Добавляет друга по бинду",
   category = Category.MISC
)
public final class ClickAction extends Module {
   private final KeySetting friendBind = new KeySetting("Добавить друга");
   public static final ClickAction INSTANCE = new ClickAction();

   @EventTarget
   public void onKey(EventKey e) {
      if (e.isKeyDown(this.friendBind.getKeyCode())) {
         HitResult var4 = mc.crosshairTarget;
         if (var4 instanceof EntityHitResult) {
            EntityHitResult result = (EntityHitResult)var4;
            Entity var5 = result.getEntity();
            if (var5 instanceof PlayerEntity) {
               PlayerEntity player = (PlayerEntity)var5;
               if (Pain.getInstance().getFriendManager().isFriend(player.getGameProfile().getName())) {
                  Pain.getInstance().getFriendManager().removeFriend(player.getGameProfile().getName());
               } else {
                  Pain.getInstance().getFriendManager().add(player.getGameProfile().getName());
               }
            }
         }
      }

   }
}
