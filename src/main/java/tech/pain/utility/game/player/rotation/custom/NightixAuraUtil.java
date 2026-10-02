package tech.pain.utility.game.player.rotation.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.interfaces.IMinecraft;
public final class NightixAuraUtil implements IMinecraft {
   private NightixAuraUtil() {
   }

   public static Vec3d getVector2(LivingEntity target) {
      double yExpand = MathHelper.clamp(target.getEyeY() - target.getY(), 0.0, target.getHeight());
      double xExpand = MathHelper.clamp(mc.player.getX() - target.getX(), -0.0, 0.0);
      double zExpand = MathHelper.clamp(mc.player.getZ() - target.getZ(), -0.0, 0.0);
      return new Vec3d(
         target.getX() - mc.player.getX() + xExpand,
         target.getY() - mc.player.getEyeY() + yExpand,
         target.getZ() - mc.player.getZ() + zExpand
      );
   }

   public static double getStrictDistance(Entity entity) {
      return getClosestVec(entity).length();
   }

   public static Vec3d getClosestVec(Entity entity) {
      Vec3d eyePosVec = mc.player.getEyePos();
      return getClosestVec(eyePosVec, entity).subtract(eyePosVec);
   }

   public static Vec3d getClosestVec(Vec3d vec, Box AABB) {
      return new Vec3d(
         NightixMathUtil.clamp(vec.x, AABB.minX, AABB.maxX),
         NightixMathUtil.clamp(vec.y, AABB.minY, AABB.maxY),
         NightixMathUtil.clamp(vec.z, AABB.minZ, AABB.maxZ)
      );
   }

   public static Vec3d getClosestVec(Vec3d vec, Entity entity) {
      return getClosestVec(vec, entity.getBoundingBox());
   }

   public static boolean validDistance(Entity entity, float distance, boolean smart) {
      return getStrictDistance(entity) < distance;
   }
}
