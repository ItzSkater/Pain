package tech.pain.utility.game.player.rotation.custom;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.rotation.Rotation;
public class ShardRotation extends RotationsSystem {
   private LivingEntity lastTarget;
   private float lastOutputYaw;
   private float lastOutputPitch;
   private float smoothedYawSpeed;
   private float smoothedPitchSpeed;
   private boolean initialized;
   private float breathingOffset;
   private int tickCounter;
   private int frameCounter;
   private float currentYaw;
   private float currentPitch;
   private float yawVelocity;
   private float pitchVelocity;
   private boolean rotationActive;
   private float jitterYaw;
   private float jitterPitch;
   private float driftX;
   private float driftY;
   private float driftZ;

   public ShardRotation() {
   }

   public void reset() {
      this.lastTarget = null;
      this.smoothedYawSpeed = 0.0F;
      this.smoothedPitchSpeed = 0.0F;
      this.breathingOffset = 0.0F;
      this.tickCounter = 0;
      this.frameCounter = 0;
      this.rotationActive = false;
      this.currentYaw = 0.0F;
      this.currentPitch = 0.0F;
      this.yawVelocity = 0.0F;
      this.pitchVelocity = 0.0F;
      this.jitterYaw = 0.0F;
      this.jitterPitch = 0.0F;
      this.driftX = 0.0F;
      this.driftY = 0.0F;
      this.driftZ = 0.0F;
      this.initialized = mc.player != null;
      if (mc.player != null) {
         this.lastOutputYaw = mc.player.getYaw();
         this.lastOutputPitch = mc.player.getPitch();
      } else {
         this.lastOutputYaw = 0.0F;
         this.lastOutputPitch = 0.0F;
      }
   }

   public void onAttack() {
   }

   private float getGCDValue() {
      return GCDUtil.getGCDValue();
   }

   private float tanhClamp(float value, float limit) {
      return limit <= 0.0F ? 0.0F : (float)(limit * Math.tanh(value / limit));
   }

   private float epochWave(float amplitude, boolean cosine, float period) {
      float t = (float)(System.currentTimeMillis() % 1000L) / period;
      return (float)(cosine ? Math.cos(t) : Math.sin(t)) * amplitude;
   }

   private float hitboxMultiplier(LivingEntity target) {
      return 1.0F;
   }

   @Override
   public void updateRotations(LivingEntity target) {
      if (mc.player != null && target != null) {
         if (mc.player.isBlocking()) {
            this.rotate = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
            this.lastOutputYaw = this.rotate.x;
            this.lastOutputPitch = this.rotate.y;
            this.rotationActive = false;
         } else {
            if (!this.initialized) {
               this.lastOutputYaw = mc.player.getYaw();
               this.lastOutputPitch = mc.player.getPitch();
               this.currentYaw = this.lastOutputYaw;
               this.currentPitch = this.lastOutputPitch;
               this.yawVelocity = 0.0F;
               this.pitchVelocity = 0.0F;
               this.rotationActive = true;
               this.initialized = true;
            }

            if (this.lastTarget != target) {
               this.lastTarget = target;
               this.smoothedYawSpeed = 0.0F;
               this.smoothedPitchSpeed = 0.0F;
               this.tickCounter = 0;
               this.frameCounter = 0;
               this.lastOutputYaw = mc.player.getYaw();
               this.lastOutputPitch = mc.player.getPitch();
               this.currentYaw = this.lastOutputYaw;
               this.currentPitch = this.lastOutputPitch;
               this.yawVelocity = 0.0F;
               this.pitchVelocity = 0.0F;
               this.rotationActive = true;
            }

            this.tickCounter++;
            this.frameCounter++;
            this.breathingOffset = (float)(Math.sin(this.tickCounter * 0.09) * 0.08 * 0.5);
            Vec3d aimPoint = BestPoint.getMultipoint(target, 128.0);
            if (this.shouldUseElytraPredict(target)) {
               aimPoint = this.getPredictedPoint(target, aimPoint);
            }

            this.driftX = MathHelper.clamp(this.driftX * 0.965F + (float)(Math.random() - 0.5) * 0.16F, -0.33F, 0.33F);
            this.driftZ = MathHelper.clamp(this.driftZ * 0.965F + (float)(Math.random() - 0.5) * 0.16F, -0.33F, 0.33F);
            this.driftY = MathHelper.clamp(this.driftY * 0.98F + (float)(Math.random() - 0.5) * 0.02F, -0.06F, 0.06F);
            aimPoint = aimPoint.add(this.driftX, this.driftY, this.driftZ);
            Vec2f toTarget = RotationUtils.getRotations(aimPoint);
            float targetYaw = toTarget.x;
            float targetPitch = toTarget.y;
            float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - this.lastOutputYaw));
            float pitchDiff = Math.abs(targetPitch - this.lastOutputPitch);
            float maxYawSpeed = mc.player.isGliding() ? 0.38F : 0.28F;
            if (yawDiff > 90.0F) {
               maxYawSpeed += 0.22F;
            } else if (yawDiff > 45.0F) {
               maxYawSpeed += 0.13F;
            } else if (yawDiff > 20.0F) {
               maxYawSpeed += 0.06F;
            }

            float wantedYawSpeed;
            if (yawDiff > 60.0F) {
               wantedYawSpeed = maxYawSpeed;
            } else if (yawDiff > 30.0F) {
               wantedYawSpeed = maxYawSpeed * 0.8F;
            } else if (yawDiff > 10.0F) {
               wantedYawSpeed = maxYawSpeed * 0.6F;
            } else {
               wantedYawSpeed = maxYawSpeed * 0.4F;
            }

            this.smoothedYawSpeed = this.smoothedYawSpeed + (wantedYawSpeed - this.smoothedYawSpeed) * 0.2F;
            float yawStep = MathHelper.clamp(this.smoothedYawSpeed, 0.0F, maxYawSpeed);
            float maxPitchSpeed = mc.player.isGliding() ? 0.24F : 0.18F;
            float wantedPitchSpeed;
            if (pitchDiff > 15.0F) {
               wantedPitchSpeed = maxPitchSpeed;
            } else if (pitchDiff > 6.0F) {
               wantedPitchSpeed = maxPitchSpeed * 0.6F;
            } else {
               wantedPitchSpeed = maxPitchSpeed * 0.3F;
            }

            this.smoothedPitchSpeed = this.smoothedPitchSpeed + (wantedPitchSpeed - this.smoothedPitchSpeed) * 0.14F;
            float pitchStep = MathHelper.clamp(this.smoothedPitchSpeed, 0.0F, maxPitchSpeed);
            if (this.frameCounter <= 3) {
               yawStep *= 0.2F * this.frameCounter;
               pitchStep *= 0.2F * this.frameCounter;
            }

            if (!this.rotationActive) {
               this.lastOutputYaw = mc.player.getYaw();
               this.lastOutputPitch = mc.player.getPitch();
               this.currentYaw = this.lastOutputYaw;
               this.currentPitch = this.lastOutputPitch;
               this.yawVelocity = 0.0F;
               this.pitchVelocity = 0.0F;
               this.rotationActive = true;
            }

            float deltaYaw = MathHelper.wrapDegrees(targetYaw - this.currentYaw);
            float deltaPitch = targetPitch - this.currentPitch;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            float speedJitter = 0.9F + rnd.nextFloat() * 0.2F;
            float maxYawVelocity = (mc.player.isGliding() ? 150.0F : 120.0F) * speedJitter;
            float maxPitchVelocity = (mc.player.isGliding() ? 24.0F : 18.0F) * speedJitter;
            deltaYaw = this.tanhClamp(deltaYaw, maxYawVelocity);
            deltaPitch = this.tanhClamp(deltaPitch, maxPitchVelocity);
            boolean gate = System.currentTimeMillis() % 500L >= 350L;
            float gateFactor = gate ? 0.0F : 1.0F;
            float yawWant = deltaYaw * (yawStep * (0.95F + this.breathingOffset * 0.4F) * gateFactor * 1.4F);
            float pitchWant = deltaPitch * (pitchStep * 0.55F * gateFactor);
            float yawSmoothing = 0.09F + rnd.nextFloat() * 0.03F;
            float pitchSmoothing = 0.12F + rnd.nextFloat() * 0.04F;
            float absDeltaYaw = Math.abs(deltaYaw);
            float absDeltaPitch = Math.abs(deltaPitch);
            yawSmoothing *= absDeltaYaw < 3.0F ? 0.5F + 0.5F * (absDeltaYaw / 3.0F) : 1.0F;
            pitchSmoothing *= absDeltaPitch < 3.0F ? 0.5F + 0.5F * (absDeltaPitch / 3.0F) : 1.0F;
            this.yawVelocity = this.yawVelocity + (yawWant - this.yawVelocity) * yawSmoothing;
            this.pitchVelocity = this.pitchVelocity + (pitchWant - this.pitchVelocity) * pitchSmoothing;
            boolean nearTarget = yawDiff < 15.0F && pitchDiff < 10.0F;
            boolean attacking = nearTarget && mc.player.getAttackCooldownProgress(1.0F) > 0.8F;
            if (attacking) {
               this.yawVelocity *= 0.75F;
               this.pitchVelocity *= 0.75F;
               this.yawVelocity = this.tanhClamp(this.yawVelocity, 22.0F);
               this.pitchVelocity = this.tanhClamp(this.pitchVelocity, 11.0F);
            } else {
               this.yawVelocity = this.tanhClamp(this.yawVelocity, 38.0F);
               this.pitchVelocity = this.tanhClamp(this.pitchVelocity, 22.0F);
            }

            this.currentYaw = this.currentYaw + this.yawVelocity;
            this.currentPitch = this.currentPitch + this.pitchVelocity;
            this.currentPitch = MathHelper.clamp(this.currentPitch, -89.0F, 89.0F);
            float gcd = this.getGCDValue();
            double gcdValue = gcd > 0.0F ? gcd : 0.005;
            float absYawVelocity = Math.abs(this.yawVelocity);
            float absPitchVelocity = Math.abs(this.pitchVelocity);
            float yawJitterScale = Math.max(0.1F, 1.0F - absYawVelocity / 5.0F);
            float pitchJitterScale = Math.max(0.1F, 1.0F - absPitchVelocity / 2.0F);
            double yawJitterAmp = gcdValue * 0.16 * yawJitterScale;
            double pitchJitterAmp = gcdValue * 0.12 * pitchJitterScale;
            this.jitterYaw = this.jitterYaw * 0.88F + (float)(rnd.nextGaussian() * yawJitterAmp) * 0.12F;
            this.jitterPitch = this.jitterPitch * 0.88F + (float)(rnd.nextGaussian() * pitchJitterAmp) * 0.12F;
            float outputYaw = this.currentYaw + this.jitterYaw;
            float outputPitch = this.currentPitch + this.jitterPitch;
            if (mc.player.getAttackCooldownProgress(1.0F) > 0.85F) {
               outputYaw += (rnd.nextFloat() * 2.0F - 1.0F) * gcd * 0.12F;
               outputPitch += (rnd.nextFloat() * 2.0F - 1.0F) * gcd * 0.08F;
            }

            float multiplier = this.hitboxMultiplier(target);
            outputYaw += this.epochWave((0.4F + (float)Math.random() * 0.1F) * multiplier * gateFactor, false, 19.0F);
            outputPitch += this.epochWave((0.05F + (float)Math.random() * 0.0F) * multiplier * gateFactor, false, 32.0F);
            float snappedYaw;
            float snappedPitch;
            if (gcd > 0.0F) {
               float rawDeltaYaw = outputYaw - this.lastOutputYaw;
               float rawDeltaPitch = outputPitch - this.lastOutputPitch;
               int yawSteps = Math.round(rawDeltaYaw / gcd);
               int pitchSteps = Math.round(rawDeltaPitch / gcd);
               int maxPitchSteps = Math.max(1, Math.round(22.0F / gcd));
               pitchSteps = MathHelper.clamp(pitchSteps, -maxPitchSteps, maxPitchSteps);
               snappedYaw = this.lastOutputYaw + yawSteps * gcd;
               snappedPitch = this.lastOutputPitch + pitchSteps * gcd;
            } else {
               snappedYaw = outputYaw;
               snappedPitch = outputPitch;
            }

            snappedPitch = MathHelper.clamp(snappedPitch, -89.0F, 89.0F);
            if (gcd > 0.0F) {
               if (Math.abs(MathHelper.wrapDegrees(this.currentYaw - snappedYaw)) > gcd * 4.0F) {
                  this.currentYaw = snappedYaw;
                  this.yawVelocity *= 0.92F;
               }

               if (Math.abs(this.currentPitch - snappedPitch) > gcd * 4.0F) {
                  this.currentPitch = snappedPitch;
                  this.pitchVelocity *= 0.92F;
               }
            }

            Rotation out = new Rotation(snappedYaw, snappedPitch);
            float speed = mc.player.isGliding() && target.isGliding() ? 360.0F : 180.0F;
            RotationComponent.update(out, speed, speed, speed, speed, 0, 1, false);
            this.rotate = new Vec2f(out.getYaw(), out.getPitch());
            this.lastOutputYaw = out.getYaw();
            this.lastOutputPitch = out.getPitch();
         }
      }
   }
}
