package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext.ShapeType;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.RaytracingUtil;
import tech.pain.utility.game.player.rotation.Rotation;
import tech.pain.utility.game.player.rotation.RotationUtil;
import tech.pain.utility.math.Timer;

@ModuleAnnotation(
   name = "CrystalAura",
   category = Category.COMBAT,
   description = "Ставит обсидиан, кристаллы и подрывает"
)
public final class CrystalAura extends Module {
   public static final CrystalAura INSTANCE = new CrystalAura();
   private static final float PLACE_ANGLE_TOLERANCE = 30.0F;

   private final NumberSetting targetRange = new NumberSetting("Дистанция цели", 10.0F, 3.0F, 16.0F, 0.5F);
   private final NumberSetting crystalRange = new NumberSetting("Дист. кристалла", 4.5F, 1.0F, 6.0F, 0.1F);
   private final NumberSetting obsidianRange = new NumberSetting("Дист. обсидиана", 4.5F, 1.0F, 6.0F, 0.1F);
   private final NumberSetting hitRange = new NumberSetting("Дист. подрыва", 4.5F, 2.0F, 6.0F, 0.1F);
   private final NumberSetting minDamage = new NumberSetting("Мин. урон", 5.0F, 0.5F, 20.0F, 0.5F);
   private final NumberSetting maxSelfDamage = new NumberSetting("Макс. самоурон", 9.0F, 0.5F, 20.0F, 0.5F);
   private final NumberSetting placeDelay = new NumberSetting("Задержка сетапа", 0.0F, 0.0F, 1000.0F, 10.0F);
   private final NumberSetting breakDelay = new NumberSetting("Задержка взрыва", 0.0F, 0.0F, 1000.0F, 10.0F);
   private final NumberSetting rotSpeed = new NumberSetting("Скорость поворота", 180.0F, 20.0F, 360.0F, 5.0F);
   // --- ротация: обычная (как раньше) или Custom с теми же настройками, что у Aura ---
   private final tech.pain.client.modules.api.setting.impl.ModeSetting rotation = tech.pain.Edition.ALPHA
      ? new tech.pain.client.modules.api.setting.impl.ModeSetting("Ротация", "Обычная", "Custom")
      : new tech.pain.client.modules.api.setting.impl.ModeSetting("Ротация", "Обычная");
   private final NumberSetting cSpread = new NumberSetting("Разброс точки (блоки)", 0.05F, 0.0F, 0.4F, 0.01F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cYawMin = new NumberSetting("Скорость по горизонтали от (°)", 40.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cYawMax = new NumberSetting("Скорость по горизонтали до (°)", 70.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cPitchMin = new NumberSetting("Скорость по вертикали от (°)", 25.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cPitchMax = new NumberSetting("Скорость по вертикали до (°)", 45.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final tech.pain.client.modules.api.setting.impl.ModeSetting cInterp = new tech.pain.client.modules.api.setting.impl.ModeSetting("Интерполяция поворота", () -> this.rotation.is("Custom"), "Кубическая", "Синусоидная", "Линейная", "Экспоненциальная");
   private final NumberSetting cExp = new NumberSetting("Доля поворота за тик", 0.5F, 0.05F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom") && this.cInterp.is("Экспоненциальная"));
   private final NumberSetting cAccel = new NumberSetting("Разгон поворота (тики)", 3.0F, 0.0F, 20.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom") && !this.cInterp.is("Экспоненциальная"));
   private final NumberSetting cSlowZone = new NumberSetting("Замедление у цели (°)", 6.0F, 0.0F, 45.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cSlowFactor = new NumberSetting("Сила замедления", 0.5F, 0.1F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom") && this.cSlowZone.getCurrent() > 0.0F);
   private final NumberSetting cFarBoost = new NumberSetting("Ускорение при большом угле (x)", 1.0F, 1.0F, 3.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cJitterYaw = new NumberSetting("Дрожание по горизонтали (°)", 0.4F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cJitterPitch = new NumberSetting("Дрожание по вертикали (°)", 0.3F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cJitterSpeed = new NumberSetting("Скорость дрожания", 1.0F, 0.2F, 4.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cOvershoot = new NumberSetting("Перелёт мимо цели (%)", 0.0F, 0.0F, 40.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cPauseChance = new NumberSetting("Шанс паузы (%)", 0.0F, 0.0F, 30.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final NumberSetting cPauseTicks = new NumberSetting("Длина паузы (тики)", 2.0F, 1.0F, 10.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom") && this.cPauseChance.getCurrent() > 0.0F);
   private final NumberSetting cReaction = new NumberSetting("Реакция на новую точку (тики)", 0.0F, 0.0F, 15.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final tech.pain.client.modules.api.setting.impl.BooleanSetting cGcd = new tech.pain.client.modules.api.setting.impl.BooleanSetting("Шаг как у мыши (GCD)", true, () -> this.rotation.is("Custom"));
   private final NumberSetting cReturn = new NumberSetting("Скорость возврата камеры (°)", 60.0F, 5.0F, 360.0F, 5.0F, (java.util.function.Supplier<Boolean>)() -> this.rotation.is("Custom"));
   private final tech.pain.client.modules.api.setting.impl.BooleanSetting cVisible = new tech.pain.client.modules.api.setting.impl.BooleanSetting("Видимый поворот (камера крутится)", false, () -> this.rotation.is("Custom"));
   private Vec3d cLastPoint;
   private Vec3d cOffset = Vec3d.ZERO;
   private long cLastCall;
   private int cMoveTicks;
   private int cReactionLeft;
   private int cPauseLeft;
   private boolean cOvershooting;
   private final double[] cPhases = new double[]{Math.random() * 6.28, Math.random() * 6.28, Math.random() * 6.28, Math.random() * 6.28};
   private final MultiBooleanSetting targets = new MultiBooleanSetting(
      "Цели",
      new MultiBooleanSetting.Value("Игроки", true),
      new MultiBooleanSetting.Value("Мобы", false)
   );
   private final MultiBooleanSetting options = new MultiBooleanSetting(
      "Опции",
      new MultiBooleanSetting.Value("Авто свап", true),
      new MultiBooleanSetting.Value("Ставить обсидиан", true),
      new MultiBooleanSetting.Value("Игнор стен", true),
      new MultiBooleanSetting.Value("Синхр. кулдаун", false),
      new MultiBooleanSetting.Value("Без суицида", true),
      new MultiBooleanSetting.Value("Человечно", true),
      new MultiBooleanSetting.Value("Дебаг", false)
   );

   private final java.util.Random rng = new java.util.Random();
   private long placeExtra = 0L;
   private long breakExtra = 0L;
   private final Timer placeTimer = new Timer();
   private final Timer breakTimer = new Timer();
   private final Timer debugTimer = new Timer();
   private LivingEntity currentTarget;
   private int restoreSlot = -1;

   private CrystalAura() {
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player != null && mc.world != null && mc.interactionManager != null) {
         if (this.restoreSlot != -1 && mc.player.getInventory().selectedSlot != this.restoreSlot) {
            switchSlot(this.restoreSlot);
            this.restoreSlot = -1;
         }

         this.currentTarget = this.findTarget();
         if (this.currentTarget == null) {
            this.debug("нет цели в радиусе " + this.targetRange.getCurrent());
         } else {
            if (!this.breakCrystals()) {
               this.setup();
            }
         }
      }
   }

   private LivingEntity findTarget() {
      boolean players = this.targets.isEnable("Игроки");
      boolean mobs = this.targets.isEnable("Мобы");
      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;
      double rangeSq = this.targetRange.getCurrent() * this.targetRange.getCurrent();

      for (Entity entity : mc.world.getEntities()) {
         if (entity != mc.player && entity instanceof LivingEntity living && living.isAlive()) {
            if (living instanceof PlayerEntity player) {
               if (!players || player.isSpectator() || player.isCreative()
                  || Pain.getInstance().getFriendManager().isFriend(player.getName().getString())) {
                  continue;
               }
            } else if (!mobs) {
               continue;
            }

            double distSq = mc.player.squaredDistanceTo(living);
            if (distSq <= rangeSq && distSq < bestDist) {
               best = living;
               bestDist = distSq;
            }
         }
      }

      return best;
   }

   private boolean breakCrystals() {
      if (this.options.isEnable("Синхр. кулдаун") && mc.player.getAttackCooldownProgress(0.0F) < 0.9F) {
         return false;
      }

      if (!this.breakTimer.finished((long)this.breakDelay.getCurrent() + this.breakExtra)) {
         return false;
      }

      Box searchBox = mc.player.getBoundingBox().expand(this.hitRange.getCurrent());
      EndCrystalEntity best = null;
      float bestDamage = -1.0F;

      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof EndCrystalEntity crystal && crystal.isAlive() && crystal.getBoundingBox().intersects(searchBox)) {
            Vec3d center = crystal.getPos();
            float selfDamage = explosionDamage(mc.player, center);
            if (this.selfDamageAllowed(selfDamage)) {
               float damage = explosionDamage(this.currentTarget, center);
               if (damage >= this.minDamage.getCurrent() && damage > bestDamage) {
                  best = crystal;
                  bestDamage = damage;
               }
            }
         }
      }

      if (best == null) {
         return false;
      } else {
         if (this.options.isEnable("Человечно")) {
            Vec3d bc = best.getBoundingBox().getCenter();
            this.rotate(bc);
            if (angleTo(bc) > PLACE_ANGLE_TOLERANCE) {
               return false;
            }
         }

         mc.interactionManager.attackEntity(mc.player, best);
         mc.player.swingHand(Hand.MAIN_HAND);
         this.breakTimer.reset();
         this.breakExtra = this.options.isEnable("Человечно") ? (long)this.rng.nextInt(90) : 0L;
         this.debug("подрыв кристалла, урон " + bestDamage);
         return true;
      }
   }

   private void resetPlace() {
      this.placeTimer.reset();
      this.placeExtra = this.options.isEnable("Человечно") ? (long)this.rng.nextInt(90) : 0L;
   }

   private void setup() {
      if (this.placeTimer.finished((long)this.placeDelay.getCurrent() + this.placeExtra)) {
         // 1) приоритет: поставить кристалл на уже существующий обсидиан/бедрок
         BlockPos crystalSpot = this.findBase(true);
         if (crystalSpot != null) {
            Vec3d center = new Vec3d((double)crystalSpot.getX() + 0.5D, (double)crystalSpot.getY() + 1.0D, (double)crystalSpot.getZ() + 0.5D);
            this.rotate(center);
            if (!(angleTo(center) > PLACE_ANGLE_TOLERANCE)) {
               BlockHitResult hit = new BlockHitResult(center, net.minecraft.util.math.Direction.UP, crystalSpot, false);
               if (this.placeItem(Items.END_CRYSTAL, hit)) {
                  this.resetPlace();
                  this.debug("поставлен кристалл на " + crystalSpot.toShortString());
               }
            }

            return;
         }

         // 2) иначе поставить обсидиан под будущий кристалл (клик по любой соседней грани)
         if (this.options.isEnable("Ставить обсидиан") && findHotbar(Items.OBSIDIAN) != -1) {
            BlockPos obiSpot = this.findBase(false);
            if (obiSpot != null) {
               BlockHitResult hit = tech.pain.utility.game.player.PlaceUtil.getPlaceResult(obiSpot);
               if (hit != null) {
                  this.rotate(hit.getPos());
                  if (!(angleTo(hit.getPos()) > PLACE_ANGLE_TOLERANCE)) {
                     if (this.placeItem(Items.OBSIDIAN, hit)) {
                        this.resetPlace();
                        this.debug("поставлен обсидиан на " + obiSpot.toShortString());
                     }
                  }

                  return;
               }
            }
         }

         this.debug("нет позиции для сетапа");
      }
   }

   private BlockPos findBase(boolean crystalOnly) {
      LivingEntity target = this.currentTarget;
      if (target == null) {
         return null;
      }

      int r = MathHelper.ceil(crystalOnly ? this.crystalRange.getCurrent() : this.obsidianRange.getCurrent());
      BlockPos targetPos = target.getBlockPos();
      boolean ignoreWalls = this.options.isEnable("Игнор стен");
      double range = crystalOnly ? (double)this.crystalRange.getCurrent() : (double)this.obsidianRange.getCurrent();
      BlockPos best = null;
      float bestDamage = Math.max(this.minDamage.getCurrent(), 0.0F);

      for (BlockPos pos : BlockPos.iterate(targetPos.add(-r, -2, -r), targetPos.add(r, 2, r))) {
         BlockPos base = pos.toImmutable();
         if (this.hasCrystalSpace(base)) {
            BlockState state = mc.world.getBlockState(base);
            boolean isObsidian = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN) || state.isOf(Blocks.BEDROCK);
            boolean valid;
            if (crystalOnly) {
               valid = isObsidian;
            } else {
               valid = !isObsidian && (state.isAir() || state.isReplaceable()) && tech.pain.utility.game.player.PlaceUtil.getPlaceResult(base) != null;
            }

            if (valid) {
               Vec3d explosion = new Vec3d((double)base.getX() + 0.5D, (double)base.getY() + 1.0D, (double)base.getZ() + 0.5D);
               double dist = mc.player.getEyePos().distanceTo(explosion);
               if (!(dist > range + 1.0D) && (ignoreWalls || this.isVisible(explosion))) {
                  float selfDamage = explosionDamage(mc.player, explosion);
                  if (this.selfDamageAllowed(selfDamage)) {
                     float damage = explosionDamage(target, explosion);
                     if (damage > bestDamage) {
                        best = base;
                        bestDamage = damage;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean hasCrystalSpace(BlockPos base) {
      BlockPos up = base.up();
      if (!mc.world.isAir(up) || !mc.world.isAir(up.up())) {
         return false;
      }

      Box box = new Box(up);

      for (Entity entity : mc.world.getOtherEntities(null, box)) {
         if (entity instanceof LivingEntity living && living.isAlive() || entity instanceof EndCrystalEntity) {
            return false;
         }
      }

      return true;
   }

   private boolean placeItem(Item item, BlockHitResult hit) {
      int slot = findHotbar(item);
      boolean holding = mc.player.getMainHandStack().isOf(item);
      if (!holding) {
         if (!this.options.isEnable("Авто свап") || slot == -1) {
            return false;
         }

         this.restoreSlot = mc.player.getInventory().selectedSlot;
         switchSlot(slot);
      }

      mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
      mc.player.swingHand(Hand.MAIN_HAND);
      if (!holding && this.restoreSlot != -1) {
         switchSlot(this.restoreSlot);
         this.restoreSlot = -1;
      }

      return true;
   }

   private static int findHotbar(Item item) {
      for (int slot = 0; slot < 9; ++slot) {
         if (mc.player.getInventory().getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private boolean isVisible(Vec3d point) {
      BlockHitResult result = RaytracingUtil.raycast(mc.player.getEyePos(), point, ShapeType.COLLIDER);
      return result == null || result.getType() == Type.MISS;
   }

   private float customNoise(int channel) {
      double t = (double)System.currentTimeMillis() / 1000.0 * (double)this.cJitterSpeed.getCurrent();
      return (float)(Math.sin(t * 2.7 + this.cPhases[channel]) * 0.6 + Math.sin(t * 7.3 + this.cPhases[channel + 2]) * 0.4);
   }

   /**
    * Ротация Custom — та же логика, что у Aura, но цель здесь точка (кристалл или блок), а не существо:
    * скорость и кривая разгона, замедление у цели, дрожание, перелёт, паузы, реакция на новую точку.
    */
   private void rotateCustom(Vec3d point) {
      java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
      long now = System.currentTimeMillis();
      boolean idle = now - this.cLastCall > 250L;
      this.cLastCall = now;
      // новая точка — та, что заметно дальше прежней (кристалл на том же блоке новой точкой не считается)
      boolean fresh = this.cLastPoint == null || this.cLastPoint.squaredDistanceTo(point) > 0.25;
      if (idle || fresh) {
         this.cMoveTicks = 0;
         this.cOvershooting = this.cOvershoot.getCurrent() > 0.0F;
         double spread = (double)this.cSpread.getCurrent();
         this.cOffset = spread <= 0.0 ? Vec3d.ZERO : new Vec3d(rnd.nextDouble(-spread, spread), rnd.nextDouble(-spread, spread), rnd.nextDouble(-spread, spread));
         if (fresh) {
            this.cReactionLeft = (int)this.cReaction.getCurrent();
         }
      }

      this.cLastPoint = point;
      Rotation want = RotationUtil.calculateAngle(point.add(this.cOffset));
      float yaw = mc.player.getYaw();
      float pitch = mc.player.getPitch();
      float dy = MathHelper.wrapDegrees(want.getYaw() + this.customNoise(0) * this.cJitterYaw.getCurrent() - yaw);
      float dp = MathHelper.clamp(want.getPitch() + this.customNoise(1) * this.cJitterPitch.getCurrent(), -90.0F, 90.0F) - pitch;
      float stepYaw = 0.0F;
      float stepPitch = 0.0F;
      if (this.cReactionLeft > 0) {
         --this.cReactionLeft;
      } else if (this.cPauseLeft > 0) {
         --this.cPauseLeft;
      } else {
         if (this.cPauseChance.getCurrent() > 0.0F && rnd.nextFloat() * 100.0F < this.cPauseChance.getCurrent()) {
            this.cPauseLeft = (int)this.cPauseTicks.getCurrent();
         }

         // перелёт: первый рывок уходит чуть дальше точки, потом голова возвращается
         if (this.cOvershooting) {
            if (Math.abs(dy) < 3.0F) {
               this.cOvershooting = false;
            } else {
               dy += dy * this.cOvershoot.getCurrent() / 100.0F;
            }
         }

         float yawSpeed = Math.min(this.cYawMin.getCurrent(), this.cYawMax.getCurrent()) + rnd.nextFloat() * Math.abs(this.cYawMax.getCurrent() - this.cYawMin.getCurrent());
         float pitchSpeed = Math.min(this.cPitchMin.getCurrent(), this.cPitchMax.getCurrent()) + rnd.nextFloat() * Math.abs(this.cPitchMax.getCurrent() - this.cPitchMin.getCurrent());
         float angle = (float)Math.hypot((double)dy, (double)dp);
         float factor = angle > 60.0F ? this.cFarBoost.getCurrent() : 1.0F;
         if (this.cSlowZone.getCurrent() > 0.0F && angle < this.cSlowZone.getCurrent()) {
            factor *= this.cSlowFactor.getCurrent() + (1.0F - this.cSlowFactor.getCurrent()) * angle / this.cSlowZone.getCurrent();
         }

         if (this.cInterp.is("Экспоненциальная")) {
            stepYaw = MathHelper.clamp(dy * this.cExp.getCurrent(), -yawSpeed * factor, yawSpeed * factor);
            stepPitch = MathHelper.clamp(dp * this.cExp.getCurrent(), -pitchSpeed * factor, pitchSpeed * factor);
         } else {
            float t = this.cAccel.getCurrent() <= 0.0F ? 1.0F : Math.min(1.0F, (float)(this.cMoveTicks + 1) / this.cAccel.getCurrent());
            float ease;
            if (this.cInterp.is("Кубическая")) {
               ease = t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow((double)(-2.0F * t + 2.0F), 3.0) / 2.0F;
            } else if (this.cInterp.is("Синусоидная")) {
               ease = 0.5F - 0.5F * (float)Math.cos(Math.PI * (double)t);
            } else {
               ease = t;
            }

            ease = Math.max(ease, 0.12F);
            stepYaw = MathHelper.clamp(dy, -yawSpeed * ease * factor, yawSpeed * ease * factor);
            stepPitch = MathHelper.clamp(dp, -pitchSpeed * ease * factor, pitchSpeed * ease * factor);
         }

         ++this.cMoveTicks;
      }

      float newYaw = yaw + stepYaw;
      float newPitch = MathHelper.clamp(pitch + stepPitch, -90.0F, 90.0F);
      if (this.cGcd.isEnabled()) {
         newYaw -= (newYaw - yaw) % Rotation.gcd();
         newPitch -= (newPitch - pitch) % Rotation.gcd();
      }

      float back = this.cReturn.getCurrent();
      RotationComponent.update(new Rotation(newYaw, newPitch), 360.0F, 360.0F, back, back, 3, 2, this.cVisible.isEnabled());
   }

   private void rotate(Vec3d point) {
      if (this.rotation.is("Custom")) {
         this.rotateCustom(point);
         return;
      }

      Rotation rotation = RotationUtil.calculateAngle(point);
      if (this.options.isEnable("Человечно")) {
         float jitterYaw = (this.rng.nextFloat() - 0.5F) * 1.6F;
         float jitterPitch = (this.rng.nextFloat() - 0.5F) * 1.0F;
         rotation = new Rotation(rotation.getYaw() + jitterYaw, rotation.getPitch() + jitterPitch);
         float base = this.rotSpeed.getCurrent();
         float yawSpeed = base * (0.65F + this.rng.nextFloat() * 0.7F);
         float pitchSpeed = yawSpeed * (0.6F + this.rng.nextFloat() * 0.3F);
         RotationComponent.update(rotation, yawSpeed, pitchSpeed, 360.0F, 360.0F, 2, 2, false);
      } else {
         RotationComponent.update(rotation, 360.0F, 360.0F, 360.0F, 360.0F, 1, 2, false);
      }
   }

   private static float angleTo(Vec3d point) {
      Rotation target = RotationUtil.calculateAngle(point);
      return RotationUtil.getClientRotation().getDelta(target);
   }

   private static void switchSlot(int slot) {
      if (slot >= 0 && slot <= 8 && mc.player.getInventory().selectedSlot != slot) {
         mc.player.getInventory().selectedSlot = slot;
         mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }

   private boolean selfDamageAllowed(float selfDamage) {
      if (selfDamage > this.maxSelfDamage.getCurrent()) {
         return false;
      } else {
         return !this.options.isEnable("Без суицида") || selfDamage < mc.player.getHealth() - 0.5F;
      }
   }

   private static float explosionDamage(LivingEntity target, Vec3d explosion) {
      Vec3d center = target.getBoundingBox().getCenter();
      double dist = center.distanceTo(explosion);
      double radius = 12.0D;
      if (dist >= radius) {
         return 0.0F;
      } else {
         double impact = (1.0D - dist / radius) * (double)exposure(explosion, center);
         double raw = (impact * impact + impact) / 2.0D * 7.0D * 6.0D * 2.0D + 1.0D;
         float armor = (float)target.getArmor();
         float toughness = (float)target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
         float f = 2.0F + toughness / 4.0F;
         float clamped = MathHelper.clamp(armor - (float)raw / f, armor * 0.2F, 20.0F);
         return (float)raw * (1.0F - clamped / 25.0F);
      }
   }

   private static float exposure(Vec3d explosion, Vec3d center) {
      BlockHitResult result = RaytracingUtil.raycast(explosion, center, ShapeType.COLLIDER);
      return result == null || result.getType() == Type.MISS ? 1.0F : 0.7F;
   }

   private void debug(String message) {
      if (this.options.isEnable("Дебаг") && this.debugTimer.finished(1000L)) {
         System.out.println("[CrystalAura] " + message);
         this.debugTimer.reset();
      }
   }

   public void onDisable() {
      super.onDisable();
      this.currentTarget = null;
      this.restoreSlot = -1;
   }
}
