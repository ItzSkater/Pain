package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.input.EventKey;
import tech.pain.base.events.impl.other.EventGameUpdate;
import tech.pain.base.events.impl.player.EventMove;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.KeySetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(
   name = "FlickBot",
   category = Category.MISC,
   description = "Разворот на 360 по биндую за заданное время"
)
public final class FlickBot extends Module {
   public static final FlickBot INSTANCE = new FlickBot();

   private final KeySetting bind = new KeySetting("Бинд");
   private final NumberSetting duration = new NumberSetting("Длительность (мс)", 100.0F, 1.0F, 500.0F, 1.0F);

   private boolean flicking;
   private float startYaw;
   private long startMs;

   private FlickBot() {
   }

   public void onDisable() {
      super.onDisable();
      this.flicking = false;
   }

   @EventTarget
   private void onKey(EventKey event) {
      if (mc.player != null) {
         int key = this.bind.getKeyCode();
         if (key != -1 && event.isKeyDown(key) && !this.flicking) {
            this.startYaw = mc.player.getYaw();
            this.startMs = System.currentTimeMillis();
            this.flicking = true;
         }
      }
   }

   @EventTarget
   private void onMove(EventMove event) {
      if (this.flicking && mc.player != null) {
         // keep the movement locked to the yaw the flick started from, so the player
         // walks straight instead of curving while the camera spins.
         Vec3d mp = event.getMovePos();
         double horiz = Math.hypot(mp.x, mp.z);
         if (horiz > 1.0E-6D) {
            double[] dir = this.directionForYaw(this.startYaw, horiz);
            event.setMovePos(new Vec3d(dir[0], mp.y, dir[1]));
         }
      }
   }

   private double[] directionForYaw(float yaw, double distance) {
      float forward = mc.player.input.movementForward;
      float sideways = mc.player.input.movementSideways;
      if (forward != 0.0F) {
         if (sideways > 0.0F) {
            yaw += forward > 0.0F ? -45.0F : 45.0F;
         } else if (sideways < 0.0F) {
            yaw += forward > 0.0F ? 45.0F : -45.0F;
         }

         sideways = 0.0F;
         forward = forward > 0.0F ? 1.0F : -1.0F;
      }

      double sinYaw = Math.sin(Math.toRadians((double)(yaw + 90.0F)));
      double cosYaw = Math.cos(Math.toRadians((double)(yaw + 90.0F)));
      double x = (double)forward * distance * cosYaw + (double)sideways * distance * sinYaw;
      double z = (double)forward * distance * sinYaw - (double)sideways * distance * cosYaw;
      return new double[]{x, z};
   }

   @EventTarget
   private void onGameUpdate(EventGameUpdate event) {
      if (this.flicking) {
         if (mc.player == null) {
            this.flicking = false;
         } else {
            long dur = (long)this.duration.getCurrent();
            if (dur < 1L) {
               dur = 1L;
            }

            long elapsed = System.currentTimeMillis() - this.startMs;
            float f = (float)elapsed / (float)dur;
            if (f >= 1.0F) {
               mc.player.setYaw(MathHelper.wrapDegrees(this.startYaw));
               this.flicking = false;
            } else {
               mc.player.setYaw(this.startYaw + 360.0F * f);
            }
         }
      }
   }
}
