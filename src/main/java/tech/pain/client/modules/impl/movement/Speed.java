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
   description = "Ускоряет передвижение: готовые режимы и Custom — свой алгоритм с кривой разгона"
)
public class Speed extends Module {
   public static final Speed INSTANCE = new Speed();

   private final ModeSetting mode = tech.pain.Edition.ALPHA ? new ModeSetting("Мод", "NCP", "StrictStrafe", "MatrixDamage", "Custom") : new ModeSetting("Мод", "NCP", "StrictStrafe", "MatrixDamage");
   // --- Custom: свой алгоритм, настраивается всё ---
   private final ModeSetting algorithm = new ModeSetting("Алгоритм", () -> this.mode.is("Custom"), "BHop", "Low Hop", "Y-Port", "Ground", "Strafe");
   // скорость
   private final ModeSetting speedSource = new ModeSetting("Скорость", () -> this.mode.is("Custom"), "По кривой", "Своя текущая");
   private final NumberSetting minSpeed = new NumberSetting("Скорость от (x)", 1.0F, 0.2F, 5.0F, 0.05F, () -> this.byCurve());
   private final NumberSetting maxSpeed = new NumberSetting("Скорость до (x)", 1.6F, 0.2F, 5.0F, 0.05F, () -> this.byCurve());
   private final ModeSetting curve = new ModeSetting("Интерполяция", () -> this.byCurve(), "Кубическая", "Синусоидная", "Линейная", "Нет");
   private final NumberSetting rampTicks = new NumberSetting("Длина разгона (тики)", 12.0F, 2.0F, 80.0F, 1.0F, () -> this.byCurve() && !this.curve.is("Нет"));
   private final ModeSetting rampMode = new ModeSetting("Ход кривой", () -> this.byCurve() && !this.curve.is("Нет"), "Разгон и удержание", "Туда-обратно", "С каждого прыжка");
   private final NumberSetting ownSpeedMult = new NumberSetting("Множитель своей скорости", 1.0F, 0.5F, 3.0F, 0.01F, () -> this.mode.is("Custom") && this.speedSource.is("Своя текущая"));
   private final NumberSetting groundMult = new NumberSetting("На земле (x)", 1.0F, 0.2F, 3.0F, 0.05F, () -> this.mode.is("Custom"));
   private final NumberSetting airMult = new NumberSetting("В воздухе (x)", 1.0F, 0.2F, 3.0F, 0.05F, () -> this.mode.is("Custom"));
   private final NumberSetting sideMult = new NumberSetting("Вбок (x)", 1.0F, 0.2F, 1.5F, 0.05F, () -> this.mode.is("Custom"));
   private final NumberSetting backMult = new NumberSetting("Назад (x)", 1.0F, 0.2F, 1.5F, 0.05F, () -> this.mode.is("Custom"));
   private final NumberSetting speedCap = new NumberSetting("Предел скорости (блоков/сек, 0 — нет)", 0.0F, 0.0F, 40.0F, 0.5F, () -> this.mode.is("Custom"));
   // стрейф — управление направлением
   private final NumberSetting strafeGround = new NumberSetting("Сила стрейфа на земле", 1.0F, 0.0F, 1.0F, 0.05F, () -> this.mode.is("Custom"));
   private final NumberSetting strafeAir = new NumberSetting("Сила стрейфа в воздухе", 1.0F, 0.0F, 1.0F, 0.05F, () -> this.mode.is("Custom"));
   private final NumberSetting strafeTurn = new NumberSetting("Макс. поворот за тик (°, 180 — без ограничения)", 180.0F, 5.0F, 180.0F, 5.0F, () -> this.mode.is("Custom"));
   private final BooleanSetting strafeOnlyAir = new BooleanSetting("Стрейфить только в воздухе", false, () -> this.mode.is("Custom"));
   // прыжок
   private final NumberSetting jumpHeight = new NumberSetting("Высота прыжка", 0.42F, 0.05F, 1.0F, 0.01F, () -> this.jumps());
   private final NumberSetting jumpBoost = new NumberSetting("Буст скорости при прыжке (x)", 1.0F, 1.0F, 3.0F, 0.05F, () -> this.jumps());
   private final NumberSetting groundTicksBeforeJump = new NumberSetting("Тики на земле перед прыжком", 0.0F, 0.0F, 10.0F, 1.0F, () -> this.jumps());
   private final BooleanSetting jumpOnlyWithKey = new BooleanSetting("Прыгать только с зажатым пробелом", false, () -> this.jumps());
   // воздух и падение
   private final NumberSetting airKeep = new NumberSetting("Сохранение скорости в воздухе", 1.0F, 0.8F, 1.0F, 0.005F, () -> this.mode.is("Custom") && !this.algorithm.is("Ground"));
   private final NumberSetting fallBoost = new NumberSetting("Ускорение падения", 1.0F, 1.0F, 3.0F, 0.05F, () -> this.mode.is("Custom") && !this.algorithm.is("Ground"));
   private final NumberSetting yPortDown = new NumberSetting("Y-Port: скорость вниз", 0.42F, 0.1F, 1.5F, 0.02F, () -> this.mode.is("Custom") && this.algorithm.is("Y-Port"));
   // урон
   private final NumberSetting hurtBoost = new NumberSetting("Буст при получении урона (x)", 1.0F, 1.0F, 4.0F, 0.05F, () -> this.mode.is("Custom"));
   private final BooleanSetting pauseOnHurt = new BooleanSetting("Пауза при получении урона", false, () -> this.mode.is("Custom"));
   // условия
   private final BooleanSetting onlySprint = new BooleanSetting("Только при беге", false, () -> this.mode.is("Custom"));
   private final BooleanSetting forceSprint = new BooleanSetting("Сам включает бег", true, () -> this.mode.is("Custom"));
   private final BooleanSetting resetOnWall = new BooleanSetting("Сброс разгона при ударе о стену", true, () -> this.byCurve() && !this.curve.is("Нет"));
   private final BooleanSetting stopInstantly = new BooleanSetting("Мгновенная остановка", true, () -> this.mode.is("Custom"));
   private int customTicks;
   private int groundTicks;
   private double customSpeed;
   private final BooleanSetting pauseInLiquids = new BooleanSetting("Пауза в воде", false);
   private final BooleanSetting pauseWhileSneaking = new BooleanSetting("Пауза при шифте", false);
   private final NumberSetting hurttime = new NumberSetting("HurtTime", 0.0F, 0.0F, 10.0F, 1.0F, () -> this.mode.is("MatrixDamage"));
   private final NumberSetting boostFactor = new NumberSetting("Множитель", 2.0F, 0.0F, 10.0F, 0.1F, () -> this.mode.is("MatrixDamage"));
   private final BooleanSetting allowOffGround = new BooleanSetting("В воздухе", true, () -> this.mode.is("MatrixDamage"));

   private int stage = 1;
   private int ticks = 0;
   private double baseSpeed = 0.2873D;
   private float prevForward = 0.0F;
   private int airTicks;

   private Speed() {
   }

   public void onEnable() {
      super.onEnable();
      this.stage = 1;
      this.ticks = 0;
      this.baseSpeed = 0.2873D;
      this.customTicks = 0;
      this.customSpeed = 0.0D;
   }

   /** Кривая разгона: t от 0 до 1 → доля пути от «Скорость от» до «Скорость до». */
   private double ease(double t) {
      t = Math.max(0.0D, Math.min(1.0D, t));
      if (this.curve.is("Кубическая")) {
         // плавный старт, быстрый средний участок, плавный выход на максимум
         return t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0D;
      } else if (this.curve.is("Синусоидная")) {
         return 0.5D - 0.5D * Math.cos(Math.PI * t);
      } else {
         return this.curve.is("Нет") ? 1.0D : t;
      }
   }

   private boolean byCurve() {
      return this.mode.is("Custom") && this.speedSource.is("По кривой");
   }

   private boolean jumps() {
      return this.mode.is("Custom") && (this.algorithm.is("BHop") || this.algorithm.is("Low Hop") || this.algorithm.is("Y-Port"));
   }

   /**
    * Свой алгоритм. Величина скорости — по кривой между «от» и «до» или своя текущая, с множителями для земли,
    * воздуха, движения вбок и назад. Направление — стрейф: насколько быстро вектор движения поворачивается
    * к нажатым клавишам. Прыжок, падение и реакция на урон — по своим настройкам.
    */
   private void customMove(EventMove event) {
      if (mc.player.getAbilities().flying || mc.player.isGliding()) {
         return;
      }

      double moveY = event.getMovePos().y;
      if (!MovingUtil.hasPlayerMovement()) {
         this.customTicks = 0;
         this.customSpeed = 0.0D;
         if (this.stopInstantly.isEnabled()) {
            event.setMovePos(new Vec3d(0.0D, moveY, 0.0D));
         }

         return;
      }

      if (this.forceSprint.isEnabled() && mc.player.input.movementForward > 0.0F && mc.player.getHungerManager().getFoodLevel() > 6) {
         mc.player.setSprinting(true);
      }

      if (this.onlySprint.isEnabled() && !mc.player.isSprinting() || this.pauseOnHurt.isEnabled() && mc.player.hurtTime > 0) {
         return;
      }

      boolean ground = mc.player.isOnGround();
      this.groundTicks = ground ? this.groundTicks + 1 : 0;
      boolean hops = this.jumps();
      boolean jumped = false;
      if (hops && ground && !mc.player.horizontalCollision && (float)this.groundTicks > this.groundTicksBeforeJump.getCurrent()
         && (!this.jumpOnlyWithKey.isEnabled() || mc.options.jumpKey.isPressed())) {
         double js = (double)this.jumpHeight.getCurrent();
         if (this.algorithm.is("Low Hop")) {
            js = Math.min(js, 0.3D);
         }

         if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            js += (double)((float)(mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1F);
         }

         mc.player.setVelocity(mc.player.getVelocity().x, js, mc.player.getVelocity().z);
         moveY = js;
         jumped = true;
         if (this.rampMode.is("С каждого прыжка")) {
            this.customTicks = 0;
         }
      } else if (!ground && !this.algorithm.is("Ground")) {
         if (this.algorithm.is("Y-Port") && moveY > 0.0D && mc.player.fallDistance == 0.0F && this.airTicks >= 1) {
            // Y-Port: сразу после отрыва прижимаемся обратно к земле
            moveY = -(double)this.yPortDown.getCurrent();
            mc.player.setVelocity(mc.player.getVelocity().x, moveY, mc.player.getVelocity().z);
         } else if (moveY < 0.0D && this.fallBoost.getCurrent() > 1.0F) {
            moveY *= (double)this.fallBoost.getCurrent();
         }
      }

      this.airTicks = ground && !jumped ? 0 : this.airTicks + 1;
      boolean inAir = !ground || jumped;
      // --- величина скорости ---
      double target;
      double floor;
      if (this.speedSource.is("Своя текущая")) {
         target = Math.max(this.currentPlayerSpeed(), this.baseMoveSpeed()) * (double)this.ownSpeedMult.getCurrent();
         floor = this.baseMoveSpeed();
      } else {
         double length = (double)this.rampTicks.getCurrent();
         double t;
         if (this.rampMode.is("Туда-обратно")) {
            double phase = (double)this.customTicks % (length * 2.0D) / length;
            t = phase <= 1.0D ? phase : 2.0D - phase;
         } else {
            t = (double)this.customTicks / length;
         }

         ++this.customTicks;
         double lo = (double)Math.min(this.minSpeed.getCurrent(), this.maxSpeed.getCurrent());
         double hi = (double)Math.max(this.minSpeed.getCurrent(), this.maxSpeed.getCurrent());
         target = this.baseMoveSpeed() * (lo + (hi - lo) * this.ease(t));
         floor = this.baseMoveSpeed() * lo;
      }

      target *= (double)(inAir ? this.airMult.getCurrent() : this.groundMult.getCurrent());
      float forward = mc.player.input.movementForward;
      float sideways = mc.player.input.movementSideways;
      if (forward < 0.0F) {
         target *= (double)this.backMult.getCurrent();
      } else if (forward == 0.0F && sideways != 0.0F) {
         target *= (double)this.sideMult.getCurrent();
      }

      if (mc.player.hurtTime > 0) {
         target *= (double)this.hurtBoost.getCurrent();
      }

      if (jumped) {
         target *= (double)this.jumpBoost.getCurrent();
      }

      if (inAir && !jumped && this.airKeep.getCurrent() < 0.999F) {
         // в воздухе скорость понемногу теряется, но не ниже нижней границы
         this.customSpeed = Math.max(Math.min(floor, target), Math.min(Math.max(target, this.customSpeed), this.customSpeed * (double)this.airKeep.getCurrent()));
      } else if (inAir && !jumped && this.jumpBoost.getCurrent() > 1.0F) {
         // буст прыжка держится, пока летим
         this.customSpeed = Math.max(target, this.customSpeed);
      } else {
         this.customSpeed = target;
      }

      if (this.speedCap.getCurrent() > 0.0F) {
         this.customSpeed = Math.min(this.customSpeed, (double)this.speedCap.getCurrent() / 20.0D);
      }

      if (mc.player.horizontalCollision && this.resetOnWall.isEnabled()) {
         this.customTicks = 0;
      }

      // --- направление (стрейф) ---
      double[] want = MovingUtil.calculateDirection(1.0D);
      double wantAngle = Math.atan2(want[1], want[0]);
      double vx = mc.player.getX() - mc.player.prevX;
      double vz = mc.player.getZ() - mc.player.prevZ;
      float strength = inAir ? this.strafeAir.getCurrent() : this.strafeGround.getCurrent();
      if (this.strafeOnlyAir.isEnabled() && !inAir) {
         strength = 0.0F;
      }

      double angle = wantAngle;
      if (vx * vx + vz * vz > 1.0E-4D) {
         double current = Math.atan2(vz, vx);
         double diff = Math.atan2(Math.sin(wantAngle - current), Math.cos(wantAngle - current));
         // сила стрейфа — какая доля поворота к нужному направлению делается за тик
         double turn = diff * (double)strength;
         double limit = Math.toRadians((double)this.strafeTurn.getCurrent());
         turn = Math.max(-limit, Math.min(limit, turn));
         angle = current + turn;
      } else if (strength <= 0.0F) {
         // стоим и стрейф выключен — ведёт обычная физика игры
         return;
      }

      event.setMovePos(new Vec3d(Math.cos(angle) * this.customSpeed, moveY, Math.sin(angle) * this.customSpeed));
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
            if (this.mode.is("Custom")) {
               this.customMove(event);
               return;
            }

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
