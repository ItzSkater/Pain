package tech.pain.utility.game.player.rotation.custom;

import java.util.Random;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.neuro.NeuroManager;
import tech.pain.base.neuro.NeuroModel;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.rotation.Rotation;

/**
 * ИИ-ротация: воспроизводит человекоподобное наведение, обученное на ручном
 * аиме игрока (NeuroManager/NeuroModel). Порт логики Luxe (NeuroRotation)
 * на систему ротаций Pain (RotationComponent).
 */
public class NeuroRotation extends RotationsSystem {
   private final Random rng = new Random();
   private boolean initialized;
   private float lastCameraYaw;
   private float lastCameraPitch;
   private float prevAppliedYaw;
   private float prevAppliedPitch;
   private float lastHitX = 0.5F;
   private float lastHitY = 0.5F;
   private float lastHitZ = 0.5F;
   private float lastSmoothness = 1.0F;
   private float lastPeak = 0.0F;
   private int sinceAttack = 999;
   private int lastTick;

   public void notifyAttack() {
      this.sinceAttack = 0;
   }

   @Override
   public void updateRotations(LivingEntity target) {
      if (mc.player == null || target == null) {
         return;
      }

      // текущий взгляд — это поворот самого игрока: при скрытой ротации камера остаётся там, куда смотришь ты,
      // и считать ошибку от неё нельзя, иначе наведение топчется вокруг твоего прицела и не доходит до цели
      float cameraYaw = mc.player.getYaw();
      float cameraPitch = mc.player.getPitch();
      if (!this.initialized || mc.player.age - this.lastTick > 3 || mc.player.age < this.lastTick) {
         // начало новой серии наведения: прошлого движения нет
         this.lastCameraYaw = cameraYaw;
         this.lastCameraPitch = cameraPitch;
         this.prevAppliedYaw = 0.0F;
         this.prevAppliedPitch = 0.0F;
         this.initialized = true;
      }

      this.lastTick = mc.player.age;
      float appliedYaw = MathHelper.wrapDegrees(cameraYaw - this.lastCameraYaw);
      float appliedPitch = cameraPitch - this.lastCameraPitch;
      Vec3d center = target.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
      float centerYaw = yawTo(center);
      float centerPitch = pitchTo(center);
      float yawErr = MathHelper.wrapDegrees(centerYaw - cameraYaw);
      float pitchErr = centerPitch - cameraPitch;
      float distance = (float)mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());
      this.sinceAttack = Math.min(this.sinceAttack + 1, 999);

      float[] move = NeuroManager.get().nextMove(yawErr, pitchErr, (float)Math.min(this.sinceAttack, 60));
      if (move != null) {
         float replayYaw = cameraYaw + move[0];
         float replayPitch = MathHelper.clamp(cameraPitch + move[1], -90.0F, 90.0F);
         // RotationComponent делит скорость между осями по доле каждой, поэтому даём сумму: тогда шаг выйдет ровно записанным
         float step = Math.max(Math.abs(move[0]) + Math.abs(move[1]), 1.0F);
         RotationComponent.update(new Rotation(replayYaw, replayPitch), step, step, 360.0F, 360.0F, randInt(3, 5), 2, false);
      } else {
         double[] features = NeuroModel.features(
            yawErr, pitchErr, appliedYaw, appliedPitch, this.prevAppliedYaw, this.prevAppliedPitch,
            this.lastHitX, this.lastHitY, this.lastHitZ, distance, this.lastSmoothness, this.lastPeak
         );
         NeuroModel.Prediction prediction = NeuroManager.get().predictDetailed(features);
         this.lastHitX = prediction.hitX;
         this.lastHitY = prediction.hitY;
         this.lastHitZ = prediction.hitZ;
         this.lastSmoothness = prediction.smoothness;
         this.lastPeak = prediction.peak;
         Vec3d aimPoint = this.pointFromHitbox(target.getBoundingBox(), prediction);
         Vec3d aim = aimPoint.subtract(mc.player.getEyePos());
         float baseYaw = yawTo(aim);
         float basePitch = pitchTo(aim);
         float aimYawDelta = MathHelper.wrapDegrees(baseYaw - cameraYaw);
         float aimPitchDelta = basePitch - cameraPitch;
         float aimError = (float)Math.hypot(aimYawDelta, aimPitchDelta);
         float styleScale = MathHelper.clamp(1.0F - aimError / 35.0F, 0.15F, 1.0F);
         float jitterScale = 1.0F + (1.0F - prediction.smoothness) * 0.9F;
         float styleYaw = (prediction.yawShake + prediction.yawLead * 0.16F + prediction.yawAccel * 0.05F) * styleScale * jitterScale;
         float stylePitch = (prediction.pitchShake + prediction.pitchLead * 0.16F + prediction.pitchAccel * 0.05F) * styleScale * jitterScale;
         float[] handJitter = NeuroManager.get().nextJitter((float)Math.hypot(appliedYaw, appliedPitch));
         float jitterYaw = MathHelper.clamp(handJitter[0], -8.0F, 8.0F);
         float jitterPitch = MathHelper.clamp(handJitter[1], -5.0F, 5.0F);
         float yaw = baseYaw + styleYaw + jitterYaw;
         float pitch = MathHelper.clamp(basePitch + stylePitch + jitterPitch, -90.0F, 90.0F);
         float peakBoost = prediction.peak * 18.0F;
         float yawSpeed = MathHelper.clamp(Math.max(prediction.yawSpeed, Math.abs(aimYawDelta) * 0.6F) + peakBoost, 0.0F, 80.0F);
         float pitchSpeed = MathHelper.clamp(Math.max(prediction.pitchSpeed, Math.abs(aimPitchDelta) * 0.03F) + peakBoost * 0.4F, 0.0F, 11.0F);
         RotationComponent.update(new Rotation(yaw, pitch), yawSpeed, pitchSpeed, 360.0F, 360.0F, randInt(3, 5), 2, false);
      }

      this.prevAppliedYaw = appliedYaw;
      this.prevAppliedPitch = appliedPitch;
      this.lastCameraYaw = cameraYaw;
      this.lastCameraPitch = cameraPitch;
   }

   private Vec3d pointFromHitbox(Box box, NeuroModel.Prediction prediction) {
      return new Vec3d(
         MathHelper.lerp((double)prediction.hitX, box.minX, box.maxX),
         MathHelper.lerp((double)prediction.hitY, box.minY, box.maxY),
         MathHelper.lerp((double)prediction.hitZ, box.minZ, box.maxZ)
      );
   }

   private int randInt(int min, int max) {
      return min + this.rng.nextInt(Math.max(1, max - min + 1));
   }

   private static float yawTo(Vec3d aim) {
      return (float)Math.toDegrees(Math.atan2(-aim.x, aim.z));
   }

   private static float pitchTo(Vec3d aim) {
      return (float)MathHelper.clamp(-Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90.0, 90.0);
   }
}
