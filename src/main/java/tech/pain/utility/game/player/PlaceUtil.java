package tech.pain.utility.game.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.interfaces.IMinecraft;

/**
 * Поиск валидной точки установки блока (клик по любой соседней твёрдой грани,
 * с приоритетом видимой игроку). Портировано из InteractionUtility (RaceClient/thunderhack).
 */
public final class PlaceUtil implements IMinecraft {
   public static BlockHitResult getPlaceResult(BlockPos bp) {
      if (mc.world == null || mc.player == null) {
         return null;
      }

      for (Entity entity : mc.world.getOtherEntities(null, new Box(bp))) {
         if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrbEntity)) {
            return null;
         }
      }

      if (!mc.world.getBlockState(bp).isReplaceable()) {
         return null;
      }

      Vec3d eye = mc.player.getEyePos();
      BlockHitResult fallback = null;

      for (Direction dir : Direction.values()) {
         BlockPos neighbor = bp.offset(dir);
         if (!mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) {
            continue;
         }

         Direction facing = dir.getOpposite();
         Vec3d hitVec = new Vec3d(
            (double)neighbor.getX() + 0.5D + (double)facing.getOffsetX() * 0.5D,
            (double)neighbor.getY() + 0.5D + (double)facing.getOffsetY() * 0.5D,
            (double)neighbor.getZ() + 0.5D + (double)facing.getOffsetZ() * 0.5D
         );
         BlockHitResult result = new BlockHitResult(hitVec, facing, neighbor, false);
         Vec3d normal = new Vec3d((double)facing.getOffsetX(), (double)facing.getOffsetY(), (double)facing.getOffsetZ());
         if (eye.subtract(hitVec).dotProduct(normal) > 0.0D) {
            return result;
         }

         if (fallback == null) {
            fallback = result;
         }
      }

      return fallback;
   }

   private PlaceUtil() {
   }
}
