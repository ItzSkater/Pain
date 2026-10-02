package tech.pain.utility.game.player.rotation.custom;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.interfaces.IMinecraft;
public final class NightixRayTraceUtil implements IMinecraft {
   private NightixRayTraceUtil() {
   }

   public static boolean rayTraceEntity(float yaw, float pitch, double distance, Entity entity) {
      float tickDelta = mc.getRenderTickCounter().getTickDelta(true);
      Vec3d eyeVec = mc.player.getCameraPosVec(tickDelta);
      Vec3d lookVec = getVectorForRotation(pitch, yaw);
      Vec3d endVec = eyeVec.add(lookVec.multiply(distance));
      if (mc.player != null && mc.player.isGliding() && entity.distanceTo(entity) > distance * 3.0) {
         return true;
      }

      Box entityBox = entity.getBoundingBox();
      return entityBox.contains(eyeVec) || entityBox.raycast(eyeVec, endVec).isPresent();
   }

   public static Vec3d getVectorForRotation(float pitch, float yaw) {
      float yawRadians = -yaw * (float) Math.PI / 180.0F - (float) Math.PI;
      float pitchRadians = -pitch * (float) Math.PI / 180.0F;
      float cosYaw = MathHelper.cos(yawRadians);
      float sinYaw = MathHelper.sin(yawRadians);
      float cosPitch = -MathHelper.cos(pitchRadians);
      float sinPitch = MathHelper.sin(pitchRadians);
      return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
   }
}
