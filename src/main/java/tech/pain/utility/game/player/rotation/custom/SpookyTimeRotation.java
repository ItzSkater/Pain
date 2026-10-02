package tech.pain.utility.game.player.rotation.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.rotation.Rotation;
public class SpookyTimeRotation extends RotationsSystem {
   private float currentSpeedYaw = 24.0F;
   private float currentSpeedPitch = 6.0F;
   private float currentSpeedYawf = 0.0F;
   private float currentSpeedPitchf = 0.0F;

   @Override
   public void updateRotations(LivingEntity target) {
      if (mc.player != null && target != null) {
         Vec3d vec = NightixAuraUtil.getVector2(target);
         float yawJitter = (float)(Math.cos(System.currentTimeMillis() / 200.0) * NightixMathUtil.randomLerp(6.0F, 8.0F));
         float pitchJitter = (float)(Math.sin(System.currentTimeMillis() / 200.0) * NightixMathUtil.randomLerp(6.0F, 8.0F));
         float targetSpeedYaw = NightixMathUtil.randomLerp(35.0F, 40.0F);
         float targetSpeedPitch = NightixMathUtil.randomLerp(4.0F, 8.0F);
         float rawYaw = (float)Math.toDegrees(Math.atan2(-vec.x, vec.z));
         float rawPitch = (float)MathHelper.clamp(-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))), -90.0, 90.0);
         float smoothFactor = 0.3F;
         if (NightixRayTraceUtil.rayTraceEntity(mc.player.getYaw(), mc.player.getPitch(), 5.0, target)) {
            targetSpeedYaw = 0.0F;
            targetSpeedPitch = 0.0F;
         }

         this.currentSpeedYaw = this.currentSpeedYaw + (targetSpeedYaw - this.currentSpeedYaw) * smoothFactor;
         this.currentSpeedPitch = this.currentSpeedPitch + (targetSpeedPitch - this.currentSpeedPitch) * smoothFactor;
         RotationComponent.update(
            new Rotation(rawYaw, rawPitch),
            this.currentSpeedYaw,
            this.currentSpeedPitch,
            NightixMathUtil.random(360.0, 390.0),
            NightixMathUtil.random(360.0, 390.0),
            (int)NightixMathUtil.random(3.0, 5.0),
            1,
            false
         );
      }
   }
}
