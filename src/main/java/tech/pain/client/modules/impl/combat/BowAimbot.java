package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.rotation.Rotation;

/**
 * Наводит лук и арбалет на цель: считает, куда стрела прилетит, а не куда направлен прицел.
 * Учитывает силу натяжения, падение стрелы, сопротивление воздуха и движение цели.
 * Идея Firrty.
 */
@ModuleAnnotation(
   name = "BowAimbot",
   category = Category.COMBAT,
   description = "Наводит лук и арбалет с упреждением по движению цели и поправкой на падение стрелы"
)
public final class BowAimbot extends Module {
   public static final BowAimbot INSTANCE = new BowAimbot();
   private final NumberSetting range = new NumberSetting("Дальность", 60.0F, 10.0F, 120.0F, 1.0F);
   private final NumberSetting fov = new NumberSetting("Угол обзора", 90.0F, 10.0F, 360.0F, 5.0F);
   private final NumberSetting speed = new NumberSetting("Скорость наводки", 90.0F, 10.0F, 360.0F, 5.0F);
   private final NumberSetting minPull = new NumberSetting("Мин. натяжение", 0.3F, 0.0F, 1.0F, 0.05F);
   private final BooleanSetting players = new BooleanSetting("Игроки", true);
   private final BooleanSetting mobs = new BooleanSetting("Враждебные мобы", false);
   private final BooleanSetting friends = new BooleanSetting("Не целить друзей", true);
   private final BooleanSetting needSight = new BooleanSetting("Только видимых", true);
   private final BooleanSetting lead = new BooleanSetting("Упреждение", true);
   private final BooleanSetting head = new BooleanSetting("Целить в голову", false);

   private BowAimbot() {
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      ItemStack held = mc.player.getActiveItem();
      float power;
      if (mc.player.isUsingItem() && held.getItem() instanceof BowItem) {
         power = BowItem.getPullProgress(mc.player.getItemUseTime());
         if (power < this.minPull.getCurrent()) {
            return;
         }

         power *= 3.0F;
      } else {
         ItemStack main = mc.player.getMainHandStack();
         if (main.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(main)) {
            power = 3.15F;
         } else {
            return;
         }
      }

      LivingEntity target = this.pick();
      if (target == null) {
         return;
      }

      Vec3d eye = mc.player.getEyePos().subtract(0.0, 0.1, 0.0);
      Vec3d vel = new Vec3d(target.getX() - target.prevX, target.getY() - target.prevY, target.getZ() - target.prevZ);
      // у стоящей на земле цели вертикальная скорость — шум; прыжок и падение оставляем
      if (target.isOnGround()) {
         vel = new Vec3d(vel.x, 0.0, vel.z);
      }

      double height = this.head.isEnabled() ? target.getStandingEyeHeight() - 0.1 : target.getHeight() * 0.55;
      Vec3d base = new Vec3d(target.getX(), target.getY() + height, target.getZ());
      Vec3d aim = base;
      float pitch = 0.0F;

      // время полёта зависит от места, а место — от времени полёта, поэтому уточняем несколько раз
      double flight = eye.distanceTo(base) / Math.max(0.5F, power);
      for (int i = 0; i < 4; ++i) {
         aim = this.lead.isEnabled() ? base.add(vel.multiply(flight)) : base;
         Solution s = this.solve(eye, aim, power);
         if (s == null) {
            return;
         }

         pitch = s.pitch;
         flight = s.ticks;
      }

      float yaw = Rotation.lookingAt(aim, eye).getYaw();
      float turn = this.speed.getCurrent();
      RotationComponent.update(new Rotation(yaw, pitch), turn, turn, 40.0F, 40.0F, 3, 3, false);
   }

   private static final class Solution {
      final float pitch;
      final double ticks;

      Solution(float pitch, double ticks) {
         this.pitch = pitch;
         this.ticks = ticks;
      }
   }

   /** Перебирает угол снизу вверх и берёт тот, при котором стрела пролетает ближе всего к точке. */
   private Solution solve(Vec3d eye, Vec3d aim, float power) {
      double dx = aim.x - eye.x;
      double dz = aim.z - eye.z;
      double flat = Math.sqrt(dx * dx + dz * dz);
      double dy = aim.y - eye.y;
      float direct = (float)-Math.toDegrees(Math.atan2(dy, flat));
      double bestErr = Double.MAX_VALUE;
      float bestPitch = direct;
      double bestTicks = flat / Math.max(0.5F, power);

      // стрела не бьёт выше прямой линии, а падает — значит, искать нужно только «вверх» от прямой
      for (float p = direct; p > direct - 55.0F && p > -89.5F; p -= 0.25F) {
         double rad = Math.toRadians(p);
         double vh = Math.cos(rad) * power;
         double vy = -Math.sin(rad) * power;
         double x = 0.0;
         double y = 0.0;
         double prevErr = Double.MAX_VALUE;

         for (int t = 1; t <= 120; ++t) {
            x += vh;
            y += vy;
            vh *= 0.99;
            vy = vy * 0.99 - 0.05;
            if (x >= flat) {
               // проходим плоскость цели: на сколько выше или ниже стрела
               double over = x - flat;
               double yAt = y - (over / Math.max(1.0E-6, vh)) * vy;
               double err = Math.abs(yAt - dy);
               if (err < bestErr) {
                  bestErr = err;
                  bestPitch = p;
                  bestTicks = t - over / Math.max(1.0E-6, vh / 0.99);
               }

               break;
            }

            if (y < dy - 80.0) {
               break;
            }
         }
      }

      return bestErr > 3.0 ? null : new Solution(MathHelper.clamp(bestPitch, -89.5F, 89.5F), bestTicks);
   }

   private LivingEntity pick() {
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      float maxRange = this.range.getCurrent();

      for (Entity e : mc.world.getEntities()) {
         if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive() || living.isInvisible() && !(e instanceof PlayerEntity)) {
            continue;
         }

         boolean ok = e instanceof PlayerEntity ? this.players.isEnabled() : e instanceof HostileEntity && this.mobs.isEnabled();
         if (!ok) {
            continue;
         }

         if (e instanceof PlayerEntity p && this.friends.isEnabled() && Pain.getInstance().getFriendManager().isFriend(p.getName().getString())) {
            continue;
         }

         double dist = mc.player.distanceTo(e);
         if (dist > (double)maxRange || this.needSight.isEnabled() && !mc.player.canSee(e)) {
            continue;
         }

         Vec3d to = e.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
         float off = Math.abs(MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-to.x, to.z)) - mc.player.getYaw()));
         if (off > this.fov.getCurrent() / 2.0F) {
            continue;
         }

         // ближе к центру прицела важнее, чем ближе по расстоянию
         double score = off * 2.0 + dist * 0.2;
         if (score < bestScore) {
            bestScore = score;
            best = living;
         }
      }

      return best;
   }
}
