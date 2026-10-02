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

   private void rotate(Vec3d point) {
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
