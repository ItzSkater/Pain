package tech.pain.utility.game.player.rotation.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.interfaces.IMinecraft;

public abstract class RotationsSystem implements IMinecraft {
   public Vec2f rotate = Vec2f.ZERO;

   public abstract void updateRotations(LivingEntity target);

   public static Vec2f correctRotation(float yaw, float pitch) {
      if ((yaw != -90.0F || pitch != 90.0F) && yaw != -180.0F) {
         float gcd = GCDUtil.getGCD();
         yaw -= yaw % gcd;
         pitch -= pitch % gcd;
         return new Vec2f(yaw, pitch);
      } else {
         return new Vec2f(mc.player.getYaw(), mc.player.getPitch());
      }
   }

   protected boolean shouldUseElytraPredict(LivingEntity target) {
      return mc.player != null && target != null && mc.player.isGliding() && target.isGliding();
   }

   protected Vec3d getPredictedPoint(LivingEntity target, Vec3d point) {
      return !this.shouldUseElytraPredict(target) ? point : bypasselytrahacking(target);
   }

   protected Box getPredictedBox(LivingEntity target) {
      Box box = target.getBoundingBox();
      if (!this.shouldUseElytraPredict(target)) {
         return box;
      }

      Vec3d center = box.getCenter();
      Vec3d predicted = this.getPredictedPoint(target, center);
      return box.offset(predicted.subtract(center));
   }

   protected static Vec3d bypasselytrahacking(LivingEntity target) {
      return Vec3d.fromPolar(target.getPitch(), target.getYaw());
   }
}
