package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import java.util.Random;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.player.EventMotion;
import tech.pain.base.events.impl.player.EventMove;
import tech.pain.base.events.impl.server.EventPacket;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.movement.Speed;

@ModuleAnnotation(
   name = "TargetStrafe",
   category = Category.COMBAT,
   description = "Кружит вокруг цели ауры"
)
public final class TargetStrafe extends Module {
   public static final TargetStrafe INSTANCE = new TargetStrafe();

   private final BooleanSetting jump = new BooleanSetting("Прыжки", true);
   private final BooleanSetting fast = new BooleanSetting("Ускорение", false);
   private final NumberSetting fastSpeedMultiplier = new NumberSetting("Множитель", 2.0F, 1.1F, 50.0F, 0.1F, () -> this.fast.isEnabled());
   private final NumberSetting distance = new NumberSetting("Дистанция", 1.3F, 0.2F, 7.0F, 0.1F);
   private final NumberSetting maxTurn = new NumberSetting("Макс поворот", 70.0F, 10.0F, 180.0F, 1.0F);
   private final BooleanSetting random = new BooleanSetting("Рандом", false);
   private final NumberSetting randomMinDistance = new NumberSetting("Мин дистанция", 0.6F, 0.2F, 7.0F, 0.1F, () -> this.random.isEnabled());
   private final ModeSetting boost = new ModeSetting("Буст", "Нет", "Урон");
   private final NumberSetting velReduction = new NumberSetting("Ослабление", 6.0F, 0.1F, 10.0F, 0.1F, () -> this.boost.is("Урон"));
   private final NumberSetting maxVelocitySpeed = new NumberSetting("Макс скорость", 0.8F, 0.1F, 2.0F, 0.1F, () -> this.boost.is("Урон"));

   private final Random rng = new Random();
   private final tech.pain.utility.math.Timer randomTimer = new tech.pain.utility.math.Timer();
   private long randomInterval = 250L;
   private double currentRadius;
   private double oldSpeed;
   private double contextFriction;
   private double fovVal;
   private boolean switchDir;
   private int jumpTicks;
   private int noSlowTicks;
   private int waterTicks;

   private TargetStrafe() {
   }

   public void onEnable() {
      super.onEnable();
      this.oldSpeed = 0.0D;
      this.currentRadius = 0.0D;
      this.randomTimer.reset();
      if (mc.options != null) {
         this.fovVal = mc.options.getFovEffectScale().getValue();
         mc.options.getFovEffectScale().setValue(0.0D);
      }
   }

   public void onDisable() {
      super.onDisable();
      if (mc.options != null) {
         mc.options.getFovEffectScale().setValue(this.fovVal);
      }
   }

   private boolean canStrafe() {
      if (mc.player == null) {
         return false;
      } else if (mc.player.isSneaking()) {
         return false;
      } else if (mc.player.isInLava()) {
         return false;
      } else if (Speed.INSTANCE.isEnabled()) {
         return false;
      } else if (!mc.player.isSubmergedInWater() && this.waterTicks <= 0) {
         return !mc.player.getAbilities().flying;
      } else {
         return false;
      }
   }

   private boolean needToSwitch(double x, double z) {
      if (mc.player.horizontalCollision || (mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()) && this.jumpTicks <= 0) {
         this.jumpTicks = 10;
         return true;
      } else {
         for(int i = (int)(mc.player.getY() + 4.0D); i >= 0; --i) {
            BlockPos playerPos = new BlockPos((int)Math.floor(x), (int)Math.floor((double)i), (int)Math.floor(z));
            if (mc.world.getBlockState(playerPos).getBlock() == net.minecraft.block.Blocks.LAVA || mc.world.getBlockState(playerPos).getBlock() == net.minecraft.block.Blocks.FIRE) {
               return true;
            }

            if (!mc.world.isAir(playerPos)) {
               return false;
            }
         }

         return false;
      }
   }

   private double calculateSpeed(EventMove move) {
      double moveY = move.getMovePos().y;
      --this.jumpTicks;
      float speedAttributes = this.getAIMoveSpeed();
      float frictionFactor = mc.world.getBlockState(new BlockPos.Mutable().set(mc.player.getX(), this.strafeBox().getMin(Direction.Axis.Y) - moveY, mc.player.getZ())).getBlock().getSlipperiness() * 0.91F;
      float n6 = mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST) && mc.player.isUsingItem() ? 0.88F : (float)(this.oldSpeed > 0.32D && mc.player.isUsingItem() ? 0.88D : 0.91D);
      if (mc.player.isOnGround()) {
         n6 = frictionFactor;
      }

      float n7 = (float)(0.1631F / Math.pow((double)n6, 3.0D));
      float n8;
      if (mc.player.isOnGround()) {
         n8 = speedAttributes * n7;
         if (moveY > 0.0D) {
            n8 += 0.2F;
         }
      } else {
         n8 = 0.0255F;
      }

      boolean noslow = false;
      double max2 = this.oldSpeed + (double)n8;
      double max = 0.0D;
      if (mc.player.isUsingItem() && moveY <= 0.0D) {
         double n10 = this.oldSpeed + (double)n8 * 0.25D;
         if (moveY != 0.0D && Math.abs(moveY) < 0.08D) {
            n10 += 0.055D;
         }

         if (max2 > (max = Math.max(0.043D, n10))) {
            noslow = true;
            ++this.noSlowTicks;
         } else {
            this.noSlowTicks = Math.max(this.noSlowTicks - 1, 0);
         }
      } else {
         this.noSlowTicks = 0;
      }

      if (this.noSlowTicks > 3) {
         max2 = max - 0.019D;
      } else {
         max2 = Math.max(noslow ? 0.0D : 0.25D, max2) - (mc.player.age % 2 == 0 ? 0.001D : 0.002D);
      }

      this.contextFriction = (double)n6;
      return max2;
   }

   private Box strafeBox() {
      return new Box(mc.player.getX() - 0.1D, mc.player.getY(), mc.player.getZ() - 0.1D, mc.player.getX() + 0.1D, mc.player.getY() + 1.0D, mc.player.getZ() + 0.1D);
   }

   private float getAIMoveSpeed() {
      boolean prevSprinting = mc.player.isSprinting();
      mc.player.setSprinting(false);
      float speed = mc.player.getMovementSpeed() * 1.3F;
      mc.player.setSprinting(prevSprinting);
      return speed;
   }

   private double wrapDS(double x, double z) {
      double diffX = x - mc.player.getX();
      double diffZ = z - mc.player.getZ();
      return Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D;
   }

   @EventTarget
   private void onMove(EventMove event) {
      if (mc.player != null && mc.world != null) {
         LivingEntity target = Aura.INSTANCE.getTarget();
         if (this.canStrafe()) {
            if (target != null) {
               double moveY = event.getMovePos().y;
               double speed = this.calculateSpeed(event);
               if (this.fast.isEnabled()) {
                  speed *= (double)this.fastSpeedMultiplier.getCurrent();
               }

               double px = mc.player.getX();
               double pz = mc.player.getZ();
               double tx = target.getX();
               double tz = target.getZ();

               // desired orbit radius; random mode wanders in/out but never past "Дистанция"
               double radius = this.distance.getCurrent();
               if (this.random.isEnabled()) {
                  if (this.currentRadius <= 0.0D) {
                     this.currentRadius = radius;
                  }

                  if (this.randomTimer.finished(this.randomInterval)) {
                     if (this.rng.nextInt(3) == 0) {
                        this.switchDir = !this.switchDir;
                     }

                     double min = Math.min(this.randomMinDistance.getCurrent(), radius);
                     this.currentRadius = min + this.rng.nextDouble() * (radius - min);
                     this.randomInterval = 150L + (long)this.rng.nextInt(350);
                     this.randomTimer.reset();
                  }

                  radius = this.currentRadius;
               }

               // advance the angle AROUND the target, then aim at a point that lies
               // exactly on the orbit. clamping the linear step to the distance to that
               // point stops high speeds from overshooting and flinging off the circle.
               double curAngle = Math.atan2(pz - tz, px - tx);
               double angularStep = speed / Math.max(radius, 0.35D);
               double maxStep = Math.toRadians((double)this.maxTurn.getCurrent());
               if (angularStep > maxStep) {
                  angularStep = maxStep;
               }

               curAngle += this.switchDir ? angularStep : -angularStep;
               double desiredX = tx + radius * Math.cos(curAngle);
               double desiredZ = tz + radius * Math.sin(curAngle);
               if (this.needToSwitch(desiredX, desiredZ)) {
                  this.switchDir = !this.switchDir;
                  curAngle += (this.switchDir ? 2.0D : -2.0D) * angularStep;
                  desiredX = tx + radius * Math.cos(curAngle);
                  desiredZ = tz + radius * Math.sin(curAngle);
               }

               double vx = desiredX - px;
               double vz = desiredZ - pz;
               double mag = Math.hypot(vx, vz);
               double step = Math.min(speed, mag);
               double newX = 0.0D;
               double newZ = 0.0D;
               if (mag > 1.0E-6D) {
                  newX = vx / mag * step;
                  newZ = vz / mag * step;
               }

               event.setMovePos(new Vec3d(newX, moveY, newZ));
            }
         } else {
            this.oldSpeed = 0.0D;
         }
      }
   }

   @EventTarget
   private void updateValues(EventMotion e) {
      if (mc.player != null) {
         this.oldSpeed = Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ) * this.contextFriction;
         if (mc.player.isOnGround() && this.jump.isEnabled() && Aura.INSTANCE.getTarget() != null && this.canStrafe()) {
            mc.player.jump();
         }

         if (mc.player.isSubmergedInWater()) {
            this.waterTicks = 10;
         } else {
            --this.waterTicks;
         }
      }
   }

   @EventTarget
   private void onPacket(EventPacket e) {
      Packet<?> packet = e.getPacket();
      if (packet instanceof PlayerPositionLookS2CPacket) {
         this.oldSpeed = 0.0D;
      } else if (packet instanceof EntityVelocityUpdateS2CPacket velocity && this.boost.is("Урон") && mc.player != null && velocity.getEntityId() == mc.player.getId()) {
         if (!mc.player.isOnGround()) {
            double vX = Math.abs(velocity.getVelocityX());
            double vZ = Math.abs(velocity.getVelocityZ());
            this.oldSpeed = (vX + vZ) / ((double)this.velReduction.getCurrent() * 1000.0D);
            this.oldSpeed = Math.min(this.oldSpeed, (double)this.maxVelocitySpeed.getCurrent());
            e.cancel();
         }
      }
   }
}
