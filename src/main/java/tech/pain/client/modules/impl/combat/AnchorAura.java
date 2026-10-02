package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
   name = "AnchorAura",
   category = Category.COMBAT,
   description = "Ставит якорь возрождения, заряжает и подрывает"
)
public final class AnchorAura extends Module {
   public static final AnchorAura INSTANCE = new AnchorAura();
   private static final float PLACE_ANGLE_TOLERANCE = 30.0F;
   private static final float POWER = 5.0F;

   private final NumberSetting targetRange = new NumberSetting("Дистанция цели", 10.0F, 3.0F, 16.0F, 0.5F);
   private final NumberSetting placeRange = new NumberSetting("Дист. установки", 4.5F, 1.0F, 6.0F, 0.1F);
   private final NumberSetting hitRange = new NumberSetting("Дист. подрыва", 4.5F, 2.0F, 6.0F, 0.1F);
   private final NumberSetting minDamage = new NumberSetting("Мин. урон", 5.0F, 0.5F, 20.0F, 0.5F);
   private final NumberSetting maxSelfDamage = new NumberSetting("Макс. самоурон", 9.0F, 0.5F, 20.0F, 0.5F);
   private final NumberSetting actionDelay = new NumberSetting("Задержка", 0.0F, 0.0F, 1000.0F, 10.0F);
   private final NumberSetting rotSpeed = new NumberSetting("Скорость поворота", 180.0F, 20.0F, 360.0F, 5.0F);
   private final MultiBooleanSetting targets = new MultiBooleanSetting(
      "Цели",
      new MultiBooleanSetting.Value("Игроки", true),
      new MultiBooleanSetting.Value("Мобы", false)
   );
   private final MultiBooleanSetting options = new MultiBooleanSetting(
      "Опции",
      new MultiBooleanSetting.Value("Авто свап", true),
      new MultiBooleanSetting.Value("Игнор стен", true),
      new MultiBooleanSetting.Value("Без суицида", true),
      new MultiBooleanSetting.Value("Человечно", true),
      new MultiBooleanSetting.Value("Дебаг", false)
   );

   private final java.util.Random rng = new java.util.Random();
   private final Timer actionTimer = new Timer();
   private final Timer debugTimer = new Timer();
   private LivingEntity currentTarget;
   private int restoreSlot = -1;

   private AnchorAura() {
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player != null && mc.world != null && mc.interactionManager != null) {
         if (this.restoreSlot != -1 && mc.player.getInventory().selectedSlot != this.restoreSlot) {
            switchSlot(this.restoreSlot);
            this.restoreSlot = -1;
         }

         if (RespawnAnchorBlock.isNether(mc.world)) {
            this.debug("в этом измерении якорь не взрывается (Nether)");
            return;
         }

         this.currentTarget = this.findTarget();
         if (this.currentTarget == null) {
            this.debug("нет цели");
         } else if (this.actionTimer.finished((long)this.actionDelay.getCurrent())) {
            if (!this.handleExistingAnchor()) {
               this.placeAnchor();
            }
         }
      }
   }

   private boolean handleExistingAnchor() {
      int r = MathHelper.ceil(this.hitRange.getCurrent());
      BlockPos targetPos = this.currentTarget.getBlockPos();
      BlockPos bestAnchor = null;
      float bestDamage = Math.max(this.minDamage.getCurrent(), 0.0F);

      for (BlockPos pos : BlockPos.iterate(targetPos.add(-r, -2, -r), targetPos.add(r, 2, r))) {
         BlockState state = mc.world.getBlockState(pos);
         if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
            Vec3d center = new Vec3d((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D);
            double dist = mc.player.getEyePos().distanceTo(center);
            if (!(dist > (double)this.hitRange.getCurrent() + 1.0D)) {
               float selfDamage = explosionDamage(mc.player, center);
               if (this.selfDamageAllowed(selfDamage)) {
                  float damage = explosionDamage(this.currentTarget, center);
                  if (damage > bestDamage) {
                     bestAnchor = pos.toImmutable();
                     bestDamage = damage;
                  }
               }
            }
         }
      }

      if (bestAnchor == null) {
         return false;
      } else {
         Vec3d center = new Vec3d((double)bestAnchor.getX() + 0.5D, (double)bestAnchor.getY() + 0.5D, (double)bestAnchor.getZ() + 0.5D);
         this.rotate(center);
         if (angleTo(center) > PLACE_ANGLE_TOLERANCE) {
            return true;
         }

         int charges = mc.world.getBlockState(bestAnchor).get(RespawnAnchorBlock.CHARGES);
         Vec3d hit = new Vec3d((double)bestAnchor.getX() + 0.5D, (double)bestAnchor.getY() + 1.0D, (double)bestAnchor.getZ() + 0.5D);
         if (charges == 0) {
            int glow = findHotbar(Items.GLOWSTONE);
            if (glow != -1 && this.useSlotOnBlock(glow, bestAnchor, hit)) {
               this.actionTimer.reset();
               this.debug("заряжен якорь на " + bestAnchor.toShortString());
            }
         } else {
            int slot = this.detonateSlot();
            if (slot != -1 && this.useSlotOnBlock(slot, bestAnchor, hit)) {
               this.actionTimer.reset();
               this.debug("подрыв якоря, урон " + bestDamage);
            }
         }

         return true;
      }
   }

   private void placeAnchor() {
      int anchorSlot = findHotbar(Items.RESPAWN_ANCHOR);
      if (anchorSlot == -1) {
         this.debug("нет якоря в хотбаре");
         return;
      }

      BlockPos base = this.findBestBase();
      if (base == null) {
         this.debug("нет позиции для якоря");
      } else {
         BlockHitResult hit = tech.pain.utility.game.player.PlaceUtil.getPlaceResult(base);
         if (hit != null) {
            this.rotate(hit.getPos());
            if (!(angleTo(hit.getPos()) > PLACE_ANGLE_TOLERANCE)) {
               if (this.useSlotOnBlock(anchorSlot, hit)) {
                  this.actionTimer.reset();
                  this.debug("поставлен якорь на " + base.toShortString());
               }
            }
         }
      }
   }

   private BlockPos findBestBase() {
      LivingEntity target = this.currentTarget;
      int r = MathHelper.ceil(this.placeRange.getCurrent());
      BlockPos targetPos = target.getBlockPos();
      boolean ignoreWalls = this.options.isEnable("Игнор стен");
      BlockPos best = null;
      float bestDamage = Math.max(this.minDamage.getCurrent(), 0.0F);

      for (BlockPos pos : BlockPos.iterate(targetPos.add(-r, -2, -r), targetPos.add(r, 2, r))) {
         BlockPos base = pos.toImmutable();
         BlockState state = mc.world.getBlockState(base);
         if ((state.isAir() || state.isReplaceable()) && tech.pain.utility.game.player.PlaceUtil.getPlaceResult(base) != null) {
            Vec3d center = new Vec3d((double)base.getX() + 0.5D, (double)base.getY() + 0.5D, (double)base.getZ() + 0.5D);
            double dist = mc.player.getEyePos().distanceTo(center);
            if (!(dist > (double)this.placeRange.getCurrent() + 1.0D) && (ignoreWalls || this.isVisible(center))) {
               float selfDamage = explosionDamage(mc.player, center);
               if (this.selfDamageAllowed(selfDamage)) {
                  float damage = explosionDamage(target, center);
                  if (damage > bestDamage) {
                     best = base;
                     bestDamage = damage;
                  }
               }
            }
         }
      }

      return best;
   }

   private int detonateSlot() {
      int totem = findHotbar(Items.TOTEM_OF_UNDYING);
      if (totem != -1) {
         return totem;
      }

      for (int slot = 0; slot < 9; ++slot) {
         net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty() && !stack.isOf(Items.GLOWSTONE)) {
            return slot;
         }
      }

      return -1;
   }

   private boolean useSlotOnBlock(int slot, BlockPos block, Vec3d hitVec) {
      return this.useSlotOnBlock(slot, new BlockHitResult(hitVec, Direction.UP, block, false));
   }

   private boolean useSlotOnBlock(int slot, BlockHitResult hit) {
      boolean holding = mc.player.getInventory().selectedSlot == slot;
      if (!holding) {
         if (!this.options.isEnable("Авто свап")) {
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

   private static int findHotbar(net.minecraft.item.Item item) {
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
         float speed = this.rotSpeed.getCurrent();
         RotationComponent.update(rotation, speed, speed, 360.0F, 360.0F, 2, 2, false);
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
      double radius = 2.0D * (double)POWER;
      double dist = center.distanceTo(explosion);
      if (dist >= radius) {
         return 0.0F;
      } else {
         double impact = (1.0D - dist / radius) * (double)exposure(explosion, center);
         double raw = (impact * impact + impact) / 2.0D * 7.0D * (double)POWER * 2.0D + 1.0D;
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
         System.out.println("[AnchorAura] " + message);
         this.debugTimer.reset();
      }
   }

   public void onDisable() {
      super.onDisable();
      this.currentTarget = null;
      this.restoreSlot = -1;
   }
}
