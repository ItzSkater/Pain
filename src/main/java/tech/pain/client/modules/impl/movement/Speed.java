package tech.pain.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.player.EventMove;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.game.player.MovingUtil;

@ModuleAnnotation(
   name = "Speed",
   category = Category.MOVEMENT,
   description = "Ускоряет передвижение (порт из Race)"
)
public class Speed extends Module {
   public static final Speed INSTANCE = new Speed();

   private final ModeSetting mode = new ModeSetting("Мод", "NCP", "StrictStrafe", "MatrixDamage");
   private final BooleanSetting pauseInLiquids = new BooleanSetting("Пауза в воде", false);
   private final BooleanSetting pauseWhileSneaking = new BooleanSetting("Пауза при шифте", false);
   private final NumberSetting hurttime = new NumberSetting("HurtTime", 0.0F, 0.0F, 10.0F, 1.0F, () -> this.mode.is("MatrixDamage"));
   private final NumberSetting boostFactor = new NumberSetting("Множитель", 2.0F, 0.0F, 10.0F, 0.1F, () -> this.mode.is("MatrixDamage"));
   private final BooleanSetting allowOffGround = new BooleanSetting("В воздухе", true, () -> this.mode.is("MatrixDamage"));

   private int stage = 1;
   private int ticks = 0;
   private double baseSpeed = 0.2873D;
   private float prevForward = 0.0F;

   private Speed() {
   }

   public void onEnable() {
      super.onEnable();
      this.stage = 1;
      this.ticks = 0;
      this.baseSpeed = 0.2873D;
   }

   private boolean isPaused() {
      return mc.player.isInFluid() && this.pauseInLiquids.isEnabled() || mc.player.isSneaking() && this.pauseWhileSneaking.isEnabled();
   }

   private double currentPlayerSpeed() {
      return Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ);
   }

   private double baseMoveSpeed() {
      double base = 0.2873D;
      if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
         base *= 1.0D + 0.2D * (double)(mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
      }

      if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
         base /= 1.0D + 0.15D * (double)(mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() + 1);
      }

      return base;
   }

   private double jumpSpeed() {
      double js = 0.41999998688697815D;
      if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
         js += (double)((float)(mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1F);
      }

      return js;
   }

   @EventTarget
   private void onMove(EventMove event) {
      if (mc.player != null && mc.world != null) {
         if (!this.isPaused()) {
            if (this.mode.is("NCP") || this.mode.is("StrictStrafe")) {
               if (!mc.player.getAbilities().flying && !mc.player.isGliding() && mc.player.getHungerManager().getFoodLevel() > 6) {
                  double moveY = event.getMovePos().y;
                  boolean strict = this.mode.is("StrictStrafe");
                  float movementForward = mc.player.input.movementForward;
                  if (MovingUtil.hasPlayerMovement()) {
                     double currentSpeed = !strict && movementForward <= 0.0F && this.prevForward > 0.0F ? this.currentPlayerSpeed() * 0.66D : this.currentPlayerSpeed();
                     boolean canJump = !mc.player.horizontalCollision;
                     if (this.stage == 1 && mc.player.isOnGround() && canJump) {
                        double js = this.jumpSpeed();
                        mc.player.setVelocity(mc.player.getVelocity().x, js, mc.player.getVelocity().z);
                        moveY = js;
                        this.baseSpeed *= 2.149D;
                        this.stage = 2;
                     } else if (this.stage == 2) {
                        this.baseSpeed = currentSpeed - 0.66D * (currentSpeed - this.baseMoveSpeed());
                        this.stage = 3;
                     } else {
                        if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0D, mc.player.getVelocity().getY(), 0.0D)).iterator().hasNext() || mc.player.verticalCollision) {
                           this.stage = 1;
                        }

                        this.baseSpeed = currentSpeed - currentSpeed / 159.0D;
                     }

                     this.baseSpeed = Math.max(this.baseSpeed, this.baseMoveSpeed());
                     double ncpSpeed = strict || movementForward < 1.0F ? 0.465D : 0.576D;
                     double ncpBypassSpeed = strict || movementForward < 1.0F ? 0.44D : 0.57D;
                     if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
                        double amplifier = (double)mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
                        ncpSpeed *= 1.0D + 0.2D * (amplifier + 1.0D);
                        ncpBypassSpeed *= 1.0D + 0.2D * (amplifier + 1.0D);
                     }

                     if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                        double amplifier = (double)mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
                        ncpSpeed /= 1.0D + 0.2D * (amplifier + 1.0D);
                        ncpBypassSpeed /= 1.0D + 0.2D * (amplifier + 1.0D);
                     }

                     this.baseSpeed = Math.min(this.baseSpeed, this.ticks > 25 ? ncpSpeed : ncpBypassSpeed);
                     if (this.ticks++ > 50) {
                        this.ticks = 0;
                     }

                     double[] dir = MovingUtil.calculateDirection(this.baseSpeed);
                     event.setMovePos(new Vec3d(dir[0], moveY, dir[1]));
                     this.prevForward = movementForward;
                  } else {
                     event.setMovePos(new Vec3d(0.0D, moveY, 0.0D));
                  }
               }
            }
         }
      }
   }

   @EventTarget
   private void onUpdate(EventUpdate event) {
      if (mc.player != null && mc.world != null && this.mode.is("MatrixDamage")) {
         if (!this.isPaused()) {
            if (MovingUtil.hasPlayerMovement() && (float)mc.player.hurtTime > this.hurttime.getCurrent()) {
               float boost = this.boostFactor.getCurrent();
               if (mc.player.isOnGround()) {
                  MovingUtil.setVelocity(0.387D * (double)boost);
               } else if (mc.player.isTouchingWater()) {
                  MovingUtil.setVelocity(0.346D * (double)boost);
               } else if (this.allowOffGround.isEnabled()) {
                  MovingUtil.setVelocity(0.448D * (double)boost);
               }
            }
         }
      }
   }
}
