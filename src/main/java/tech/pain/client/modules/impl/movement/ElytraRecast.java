package tech.pain.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventMoveInput;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.utility.game.player.MovingUtil;
import tech.pain.utility.game.player.PlayerIntersectionUtil;

@ModuleAnnotation(
   name = "ElytraRecast",
   description = "Позволяет выше прыгать на элитрах",
   category = Category.MOVEMENT
)
public final class ElytraRecast extends Module {
   public static final ElytraRecast INSTANCE = new ElytraRecast();
   private int groundTick = 0;
   private boolean changed = false;

   @EventTarget
   public void update(EventMoveInput eventUpdate) {
      if (mc.player.isUsingItem()) {
         if (Pain.getInstance().getServerHandler().isServerSprint()) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));
            mc.player.setSprinting(false);
         }

         this.groundTick = 5;
      } else if (this.groundTick > 0) {
         --this.groundTick;
         return;
      }

      if (!mc.player.isUsingItem() && !mc.player.isTouchingWater() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem().equals(Items.ELYTRA) && MovingUtil.hasPlayerMovement()) {
         if (mc.player.isOnGround() && mc.player.isWalking()) {
            if (!mc.player.canSprint() || !mc.player.isWalking() || mc.player.isBlind() || mc.player.isUsingItem() || mc.player.shouldSlowDown() && !mc.player.isSubmergedInWater()) {
               if (Pain.getInstance().getServerHandler().isServerSprint()) {
                  mc.player.lastSprinting = true;
                  mc.player.setSprinting(false);
               }

               mc.player.setSprinting(false);
            } else {
               if (!mc.player.isSprinting() && Pain.getInstance().getServerHandler().isServerSprint()) {
                  mc.player.setSprinting(true);
               }

               if (!Pain.getInstance().getServerHandler().isServerSprint()) {
                  mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_SPRINTING));
                  mc.player.setSprinting(true);
                  this.changed = true;
               }
            }

            mc.player.jump();
         } else if (!mc.player.isGliding()) {
            PlayerIntersectionUtil.startFallFlying();
         }
      } else if (this.changed && Pain.getInstance().getServerHandler().isServerSprint()) {
         mc.player.lastSprinting = true;
         mc.player.setSprinting(false);
         this.changed = false;
      }

      if (this.groundTick > 0) {
         --this.groundTick;
      }

   }

   public void onDisable() {
      if (Pain.getInstance().getServerHandler().isServerSprint() && this.changed) {
         mc.player.lastSprinting = true;
         mc.player.setSprinting(false);
      }

      super.onDisable();
   }
}
