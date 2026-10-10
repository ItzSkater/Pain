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
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
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
import tech.pain.utility.game.player.PlayerIntersectionUtil;
import tech.pain.utility.game.player.rotation.Rotation;

/**
 * Рейдж-бот для метательного оружия: лук, арбалет, трезубец, снежки, яйца.
 * Пока оружие в руке, персонаж для сервера смотрит в пол, а в момент выстрела резко
 * разворачивается на цель — с поправкой на падение снаряда и движение цели. Своя камера при этом не дёргается.
 * Идея Firrty.
 */
@ModuleAnnotation(
   name = "RageBot",
   category = Category.COMBAT,
   description = "Смотрит в пол и резко наводится на цель в момент выстрела: лук, арбалет, трезубец, снежки, яйца"
)
public final class RageBot extends Module {
   public static final RageBot INSTANCE = new RageBot();
   private final BooleanSetting bow = new BooleanSetting("Лук", true);
   private final BooleanSetting crossbow = new BooleanSetting("Арбалет", true);
   private final BooleanSetting trident = new BooleanSetting("Трезубец", true);
   private final BooleanSetting snowball = new BooleanSetting("Снежки", true);
   private final BooleanSetting egg = new BooleanSetting("Яйца", true);
   private final BooleanSetting lookDown = new BooleanSetting("Смотреть в пол", true);
   private final BooleanSetting autoShoot = new BooleanSetting("Стрелять самому", true);
   private final NumberSetting minPull = new NumberSetting("Натяжение лука", 1.0F, 0.2F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.autoShoot.isEnabled());
   private final NumberSetting throwDelay = new NumberSetting("Пауза между бросками (тики)", 5.0F, 1.0F, 20.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.autoShoot.isEnabled());
   private final NumberSetting range = new NumberSetting("Дальность", 60.0F, 10.0F, 120.0F, 1.0F);
   private final NumberSetting fov = new NumberSetting("Угол обзора", 360.0F, 10.0F, 360.0F, 5.0F);
   private final BooleanSetting players = new BooleanSetting("Игроки", true);
   private final BooleanSetting mobs = new BooleanSetting("Враждебные мобы", false);
   private final BooleanSetting friends = new BooleanSetting("Не целить друзей", true);
   private final BooleanSetting needSight = new BooleanSetting("Только видимых", true);
   private final BooleanSetting lead = new BooleanSetting("Упреждение", true);
   private final BooleanSetting head = new BooleanSetting("Целить в голову", false);
   private int aimTicks;
   private int cooldown;

   private RageBot() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.aimTicks = 0;
      this.cooldown = 0;
   }

   private enum Kind {
      NONE, BOW, TRIDENT, CROSSBOW, THROW
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null) {
         return;
      }

      if (this.cooldown > 0) {
         --this.cooldown;
      }

      ItemStack main = mc.player.getMainHandStack();
      ItemStack active = mc.player.getActiveItem();
      Kind kind = Kind.NONE;
      float velocity = 0.0F;
      float gravity = 0.05F;
      boolean ready = false;
      if (mc.player.isUsingItem() && active.getItem() instanceof BowItem && this.bow.isEnabled()) {
         kind = Kind.BOW;
         float pull = BowItem.getPullProgress(mc.player.getItemUseTime());
         velocity = pull * 3.0F;
         ready = pull >= this.minPull.getCurrent();
      } else if (mc.player.isUsingItem() && active.isOf(Items.TRIDENT) && this.trident.isEnabled()) {
         kind = Kind.TRIDENT;
         velocity = 2.5F;
         ready = mc.player.getItemUseTime() >= 10;
      } else if (main.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(main) && this.crossbow.isEnabled()) {
         kind = Kind.CROSSBOW;
         velocity = 3.15F;
         ready = true;
      } else if (main.isOf(Items.SNOWBALL) && this.snowball.isEnabled() || main.isOf(Items.EGG) && this.egg.isEnabled()) {
         kind = Kind.THROW;
         velocity = 1.5F;
         gravity = 0.03F;
         ready = true;
      }

      if (kind == Kind.NONE) {
         this.aimTicks = 0;
         return;
      }

      LivingEntity target = this.pick();
      Rotation shot = target == null || velocity < 0.3F ? null : this.aimAt(target, velocity, gravity);
      // выстрел: держим наводку один тик, на втором стреляем — так сервер уже знает новый угол
      if (shot != null && ready && (this.autoShoot.isEnabled() || kind == Kind.BOW || kind == Kind.TRIDENT)) {
         if (kind == Kind.BOW || kind == Kind.TRIDENT) {
            RotationComponent.update(shot, 360.0F, 360.0F, 360.0F, 360.0F, 2, 4, false);
            if (this.autoShoot.isEnabled() && ++this.aimTicks >= 2) {
               mc.interactionManager.stopUsingItem(mc.player);
               this.aimTicks = 0;
            }

            return;
         }

         if (this.cooldown == 0) {
            RotationComponent.update(shot, 360.0F, 360.0F, 360.0F, 360.0F, 2, 4, false);
            // у броска и арбалета угол уходит на сервер прямо в пакете использования предмета
            final float yaw = shot.getYaw();
            final float pitch = shot.getPitch();
            PlayerIntersectionUtil.sendSequencedPacket(seq -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, seq, yaw, pitch));
            mc.player.swingHand(Hand.MAIN_HAND);
            this.cooldown = (int)this.throwDelay.getCurrent();
            return;
         }
      }

      this.aimTicks = 0;
      if (this.lookDown.isEnabled()) {
         RotationComponent.update(new Rotation(mc.player.getYaw(), 90.0F), 360.0F, 360.0F, 360.0F, 360.0F, 2, 3, false);
      }
   }

   private Rotation aimAt(LivingEntity target, float velocity, float gravity) {
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
      double flight = eye.distanceTo(base) / (double)velocity;

      for (int i = 0; i < 4; ++i) {
         aim = this.lead.isEnabled() ? base.add(vel.multiply(flight)) : base;
         double[] s = solve(eye, aim, velocity, gravity);
         if (s == null) {
            return null;
         }

         pitch = (float)s[0];
         flight = s[1];
      }

      return new Rotation(Rotation.lookingAt(aim, eye).getYaw(), pitch);
   }

   /** Перебирает угол от прямой наводки вверх и берёт тот, при котором снаряд проходит ближе всего к точке. Возвращает {угол, тики полёта}. */
   private static double[] solve(Vec3d eye, Vec3d aim, float power, float gravity) {
      double dx = aim.x - eye.x;
      double dz = aim.z - eye.z;
      double flat = Math.sqrt(dx * dx + dz * dz);
      double dy = aim.y - eye.y;
      float direct = (float)-Math.toDegrees(Math.atan2(dy, flat));
      double bestErr = Double.MAX_VALUE;
      float bestPitch = direct;
      double bestTicks = flat / (double)power;

      for (float p = direct; p > direct - 60.0F && p > -89.5F; p -= 0.25F) {
         double rad = Math.toRadians((double)p);
         double vh = Math.cos(rad) * (double)power;
         double vy = -Math.sin(rad) * (double)power;
         double x = 0.0;
         double y = 0.0;

         for (int t = 1; t <= 140; ++t) {
            x += vh;
            y += vy;
            if (x >= flat) {
               // снаряд пересёк вертикаль цели: смотрим, на сколько выше или ниже он прошёл
               double over = (x - flat) / Math.max(1.0E-6, vh);
               double err = Math.abs(y - over * vy - dy);
               if (err < bestErr) {
                  bestErr = err;
                  bestPitch = p;
                  bestTicks = (double)t - over;
               }

               break;
            }

            vh *= 0.99;
            vy = vy * 0.99 - (double)gravity;
            if (y < dy - 90.0) {
               break;
            }
         }
      }

      return bestErr > 2.5 ? null : new double[]{(double)MathHelper.clamp(bestPitch, -89.5F, 89.5F), bestTicks};
   }

   private LivingEntity pick() {
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      float maxRange = this.range.getCurrent();

      for (Entity e : mc.world.getEntities()) {
         if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) {
            continue;
         }

         boolean ok = e instanceof PlayerEntity ? this.players.isEnabled() : e instanceof HostileEntity && this.mobs.isEnabled();
         if (!ok) {
            continue;
         }

         if (e instanceof PlayerEntity p && this.friends.isEnabled() && Pain.getInstance().getFriendManager().isFriend(p.getName().getString())) {
            continue;
         }

         double dist = (double)mc.player.distanceTo(e);
         if (dist > (double)maxRange || this.needSight.isEnabled() && !mc.player.canSee(e)) {
            continue;
         }

         Vec3d to = e.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
         float off = Math.abs(MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-to.x, to.z)) - mc.player.getYaw()));
         if (off > this.fov.getCurrent() / 2.0F) {
            continue;
         }

         // ближе к прицелу важнее, чем ближе по расстоянию
         double score = (double)off * 2.0 + dist * 0.2;
         if (score < bestScore) {
            bestScore = score;
            best = living;
         }
      }

      return best;
   }
}
