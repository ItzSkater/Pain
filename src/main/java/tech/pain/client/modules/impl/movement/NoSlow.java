package tech.pain.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;
import java.util.Objects;
import net.minecraft.util.Hand;
import tech.pain.base.events.impl.player.EventSlowWalking;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.utility.game.player.PlayerIntersectionUtil;

@ModuleAnnotation(
   name = "NoSlow",
   category = Category.MOVEMENT,
   description = "Убирает замедление во время еды"
)
public final class NoSlow extends Module {
   public static final NoSlow INSTANCE = new NoSlow();
   private final ModeSetting mode = new ModeSetting("Мод", new String[0]);
   private final ModeSetting.Value grimNew;
   private final ModeSetting.Value hw;
   private BooleanSetting sprint;
   private int ticks;

   private NoSlow() {
      this.grimNew = new ModeSetting.Value(this.mode, "Grim New");
      this.hw = (new ModeSetting.Value(this.mode, "Grim old")).select();
      ModeSetting.Value var10005 = this.hw;
      Objects.requireNonNull(var10005);
      this.sprint = new BooleanSetting("Спринт", true, var10005::isSelected);
      this.ticks = 0;
   }

   @EventTarget
   public void onItemUse(EventSlowWalking e) {
      if (this.grimNew.isSelected() && mc.player.getItemUseTime() % 2 == 0) {
         e.setCancelled(true);
      }

      if (this.hw.isSelected()) {
         Hand hand = mc.player.getActiveHand();
         if (this.sprint.isEnabled()) {
            mc.player.setSprinting(mc.player.canSprint() && mc.player.isWalking() && !mc.player.isBlind() && !mc.player.isGliding() && (!mc.player.shouldSlowDown() || mc.player.isSubmergedInWater()));
         }

         PlayerIntersectionUtil.useItem(hand.equals(Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND);
         e.setCancelled(true);
      }

   }

   @EventTarget
   public void update(EventUpdate tickEvent) {
      if (!mc.player.isUsingItem() || !mc.player.isOnGround()) {
         this.ticks = 0;
      }

   }
}
