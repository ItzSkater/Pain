package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.PlayerInventoryUtil;
import tech.pain.utility.game.player.rotation.Rotation;

/**
 * Elytra Target: в полёте на элитрах сам ведёт тебя к цели. Голова поворачивается на точку перед целью
 * (с упреждением по её скорости), поэтому полёт идёт на перехват; по желанию модуль сам пускает
 * фейерверки, когда скорость падает. Камера остаётся свободной. Бьёт по-прежнему Aura.
 */
@ModuleAnnotation(
   name = "ElytraTarget",
   category = Category.COMBAT,
   description = "На элитрах сам летит к цели на перехват и подкидывает фейерверки"
)
public final class ElytraTarget extends Module {
   public static final ElytraTarget INSTANCE = new ElytraTarget();
   private final ModeSetting who = new ModeSetting("Цель", "Цель Aura, иначе ближайший", "Только цель Aura", "Ближайший игрок");
   private final NumberSetting range = new NumberSetting("Дальность поиска", 60.0F, 10.0F, 150.0F, 5.0F);
   private final BooleanSetting onlyFlying = new BooleanSetting("Только летящие цели", false);
   private final NumberSetting lead = new NumberSetting("Упреждение (x)", 1.0F, 0.0F, 3.0F, 0.1F);
   private final ModeSetting aimAt = new ModeSetting("Куда лететь", "В цель", "Чуть выше", "За спину");
   private final NumberSetting turnSpeed = new NumberSetting("Скорость поворота (°)", 40.0F, 5.0F, 180.0F, 5.0F);
   private final NumberSetting stopDistance = new NumberSetting("Не вести ближе (блоки)", 2.5F, 0.0F, 8.0F, 0.5F);
   private final BooleanSetting fireworks = new BooleanSetting("Сам пускает фейерверки", true);
   private final NumberSetting fireworkSpeed = new NumberSetting("Фейерверк при скорости ниже (блоков/сек)", 22.0F, 5.0F, 60.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.fireworks.isEnabled());
   private final NumberSetting fireworkDelay = new NumberSetting("Пауза между фейерверками (мс)", 1500.0F, 300.0F, 6000.0F, 100.0F, (java.util.function.Supplier<Boolean>)() -> this.fireworks.isEnabled());
   private final NumberSetting fireworkMinDistance = new NumberSetting("Фейерверк, если цель дальше (блоки)", 6.0F, 0.0F, 40.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.fireworks.isEnabled());
   private final BooleanSetting legitSwap = new BooleanSetting("Брать фейерверк по-честному (через слот)", true, () -> this.fireworks.isEnabled());
   private LivingEntity target;
   private long lastFirework;

   private ElytraTarget() {
   }

   public LivingEntity getTarget() {
      return this.target;
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.target = null;
   }

   private boolean valid(Entity entity) {
      if (!(entity instanceof PlayerEntity player) || player == mc.player || !player.isAlive() || player.getHealth() <= 0.0F || player.isSpectator()) {
         return false;
      }

      if (Pain.getInstance().getFriendManager().isFriend(player.getName().getString()) || AntiBot.INSTANCE.isBot(player)) {
         return false;
      }

      if (this.onlyFlying.isEnabled() && !player.isGliding()) {
         return false;
      }

      return mc.player.distanceTo(player) <= this.range.getCurrent();
   }

   private LivingEntity pick() {
      LivingEntity aura = Aura.INSTANCE.isEnabled() ? Aura.INSTANCE.getTarget() : null;
      if (aura != null && !this.who.is("Ближайший игрок") && aura.isAlive()) {
         return aura;
      }

      if (this.who.is("Только цель Aura")) {
         return null;
      }

      // прежнюю цель держим, пока она годится, — иначе полёт мечется между игроками
      if (this.target != null && this.valid(this.target)) {
         return this.target;
      }

      LivingEntity best = null;
      double bestDist = Double.MAX_VALUE;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (this.valid(player)) {
            double d = (double)mc.player.squaredDistanceTo(player);
            if (d < bestDist) {
               bestDist = d;
               best = player;
            }
         }
      }

      return best;
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      if (!mc.player.isGliding()) {
         this.target = null;
         return;
      }

      this.target = this.pick();
      if (this.target == null) {
         return;
      }

      Vec3d eye = mc.player.getEyePos();
      Vec3d point = this.target.getBoundingBox().getCenter();
      double distance = eye.distanceTo(point);
      if (distance <= (double)this.stopDistance.getCurrent()) {
         // вплотную ведёт уже Aura; здесь не мешаем
         return;
      }

      // перехват: целимся туда, где цель окажется, когда мы до неё долетим
      Vec3d targetVel = new Vec3d(this.target.getX() - this.target.prevX, this.target.getY() - this.target.prevY, this.target.getZ() - this.target.prevZ);
      double mySpeed = Math.max(0.4, mc.player.getVelocity().length());
      double ticks = Math.min(40.0, distance / mySpeed) * (double)this.lead.getCurrent();
      point = point.add(targetVel.multiply(ticks));
      if (this.aimAt.is("Чуть выше")) {
         point = point.add(0.0, 1.2, 0.0);
      } else if (this.aimAt.is("За спину")) {
         // точка в паре блоков позади цели по её взгляду — заходим сзади
         Vec3d look = Vec3d.fromPolar(0.0F, this.target.getYaw());
         point = point.subtract(look.multiply(2.0));
      }

      Rotation want = Rotation.lookingAt(point, eye);
      float speed = this.turnSpeed.getCurrent();
      // приоритет ниже, чем у Aura: когда она наводится для удара, её поворот важнее
      RotationComponent.update(want, speed, speed, 40.0F, 40.0F, 4, 0, false);
      if (!this.fireworks.isEnabled()) {
         return;
      }

      long now = System.currentTimeMillis();
      double blocksPerSecond = mc.player.getVelocity().length() * 20.0;
      float off = Math.abs(MathHelper.wrapDegrees(want.getYaw() - mc.player.getYaw())) + Math.abs(want.getPitch() - mc.player.getPitch());
      // ускоряемся, только когда уже смотрим на цель, — иначе фейерверк унесёт в сторону
      if (blocksPerSecond < (double)this.fireworkSpeed.getCurrent() && distance > (double)this.fireworkMinDistance.getCurrent() && off < 25.0F
         && (float)(now - this.lastFirework) > this.fireworkDelay.getCurrent() && !mc.player.isUsingItem()) {
         this.lastFirework = now;
         if (this.legitSwap.isEnabled()) {
            PlayerInventoryUtil.swapAndUseLegit(Items.FIREWORK_ROCKET);
         } else {
            PlayerInventoryUtil.swapAndUseHvH(Items.FIREWORK_ROCKET);
         }
      }
   }
}
