package tech.pain.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.player.EventMove;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Обычный полёт: клиент сам задаёт своё движение. Три режима — свой расчёт скорости (Motion),
 * полёт как в креативе (Creative) и медленное планирование (Glide). Рассчитан на свой сервер,
 * одиночную игру и серверы, где полёт не проверяется: пакеты он не подделывает.
 */
@ModuleAnnotation(
   name = "Fly",
   category = Category.MOVEMENT,
   description = "Полёт: свой расчёт скорости, креативный или планирование, с разгоном, торможением и анти-киком"
)
public final class Fly extends Module {
   public static final Fly INSTANCE = new Fly();
   private final ModeSetting mode = new ModeSetting("Режим", "Motion", "Creative", "Glide");
   private final NumberSetting hSpeed = new NumberSetting("Скорость (блоков/сек)", 10.0F, 1.0F, 60.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> !this.mode.is("Glide"));
   private final NumberSetting vSpeed = new NumberSetting("Вверх/вниз (блоков/сек)", 8.0F, 1.0F, 40.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   private final NumberSetting sprintMult = new NumberSetting("Множитель при беге (x)", 1.5F, 1.0F, 4.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   // --- Motion: как набирается и гасится скорость ---
   private final NumberSetting accel = new NumberSetting("Разгон (1 — мгновенно)", 0.35F, 0.02F, 1.0F, 0.01F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   private final NumberSetting brake = new NumberSetting("Торможение (1 — мгновенно)", 0.5F, 0.02F, 1.0F, 0.01F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   private final NumberSetting sideMult = new NumberSetting("Вбок (x)", 1.0F, 0.2F, 1.5F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   private final NumberSetting backMult = new NumberSetting("Назад (x)", 1.0F, 0.2F, 1.5F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   private final BooleanSetting lookFly = new BooleanSetting("Лететь по взгляду", false, () -> this.mode.is("Motion"));
   private final NumberSetting idleFall = new NumberSetting("Снижение без клавиш (блоков/сек)", 0.0F, 0.0F, 5.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Motion"));
   // --- Glide ---
   private final NumberSetting glideFall = new NumberSetting("Скорость падения (блоков/сек)", 1.0F, 0.05F, 10.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Glide"));
   private final NumberSetting glideMult = new NumberSetting("Скорость вперёд (x)", 1.0F, 0.5F, 4.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Glide"));
   // --- общее ---
   private final BooleanSetting antiKick = new BooleanSetting("Анти-кик за полёт", true, () -> !this.mode.is("Glide"));
   private final NumberSetting antiKickEvery = new NumberSetting("Анти-кик: раз в тиков", 30.0F, 5.0F, 70.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.antiKick.isEnabled() && !this.mode.is("Glide"));
   private final BooleanSetting stopOnDisable = new BooleanSetting("Останавливаться при выключении", true);
   private double velX;
   private double velZ;
   private int ticks;
   private boolean hadFlight;
   private float oldFlySpeed = 0.05F;

   private Fly() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.velX = 0.0;
      this.velZ = 0.0;
      this.ticks = 0;
      if (mc.player != null) {
         this.hadFlight = mc.player.getAbilities().allowFlying;
         this.oldFlySpeed = mc.player.getAbilities().getFlySpeed();
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (mc.player == null) {
         return;
      }

      // возвращаем способности, какими они были: в креативе полёт должен остаться
      mc.player.getAbilities().setFlySpeed(this.oldFlySpeed);
      if (!this.hadFlight) {
         mc.player.getAbilities().allowFlying = false;
         mc.player.getAbilities().flying = false;
      }

      if (this.stopOnDisable.isEnabled()) {
         mc.player.setVelocity(0.0, Math.min(0.0, mc.player.getVelocity().y), 0.0);
      }
   }

   @EventTarget
   public void onMove(EventMove event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      ++this.ticks;
      if (this.mode.is("Creative")) {
         mc.player.getAbilities().allowFlying = true;
         mc.player.getAbilities().flying = true;
         // в игре скорость полёта 0.05 даёт около 10.9 блоков/сек
         mc.player.getAbilities().setFlySpeed(this.hSpeed.getCurrent() / 10.9F * 0.05F);
         if (this.kickTick()) {
            event.setMovePos(new Vec3d(event.getMovePos().x, -0.04, event.getMovePos().z));
         }

         return;
      }

      if (this.mode.is("Glide")) {
         Vec3d move = event.getMovePos();
         if (move.y < 0.0 && !mc.player.isOnGround()) {
            double fall = -(double)this.glideFall.getCurrent() / 20.0;
            double mult = (double)this.glideMult.getCurrent();
            event.setMovePos(new Vec3d(move.x * mult, Math.max(move.y, fall), move.z * mult));
            mc.player.setVelocity(mc.player.getVelocity().x, Math.max(mc.player.getVelocity().y, fall), mc.player.getVelocity().z);
            mc.player.fallDistance = 0.0F;
         }

         return;
      }

      // Motion
      float forward = mc.player.input.movementForward;
      float sideways = mc.player.input.movementSideways;
      double speed = (double)this.hSpeed.getCurrent() / 20.0;
      if (mc.options.sprintKey.isPressed() || mc.player.isSprinting()) {
         speed *= (double)this.sprintMult.getCurrent();
      }

      double targetX = 0.0;
      double targetZ = 0.0;
      double up = 0.0;
      boolean input = forward != 0.0F || sideways != 0.0F;
      if (input) {
         double len = Math.sqrt((double)(forward * forward + sideways * sideways));
         double f = (double)forward / len * (forward < 0.0F ? (double)this.backMult.getCurrent() : 1.0);
         double s = (double)sideways / len * (double)this.sideMult.getCurrent();
         double yaw = Math.toRadians((double)mc.player.getYaw());
         double flat = 1.0;
         if (this.lookFly.isEnabled() && forward != 0.0F) {
            // по взгляду: часть скорости уходит в высоту
            double pitch = Math.toRadians((double)mc.player.getPitch());
            flat = Math.cos(pitch);
            up += -Math.sin(pitch) * f * speed;
         }

         targetX = (-Math.sin(yaw) * f * flat + Math.cos(yaw) * s) * speed;
         targetZ = (Math.cos(yaw) * f * flat + Math.sin(yaw) * s) * speed;
      }

      double k = (double)(input ? this.accel.getCurrent() : this.brake.getCurrent());
      this.velX += (targetX - this.velX) * k;
      this.velZ += (targetZ - this.velZ) * k;
      if (Math.abs(this.velX) < 1.0E-4) {
         this.velX = 0.0;
      }

      if (Math.abs(this.velZ) < 1.0E-4) {
         this.velZ = 0.0;
      }

      double vertical = (double)this.vSpeed.getCurrent() / 20.0;
      if (mc.options.jumpKey.isPressed()) {
         up += vertical;
      }

      if (mc.options.sneakKey.isPressed()) {
         up -= vertical;
      }

      if (up == 0.0) {
         up = -(double)this.idleFall.getCurrent() / 20.0;
      }

      if (up >= 0.0 && this.kickTick()) {
         up = -0.04;
      }

      Vec3d move = new Vec3d(this.velX, up, this.velZ);
      event.setMovePos(move);
      // своя скорость не должна складываться с игровой: гравитация и инерция обнуляются каждый тик
      mc.player.setVelocity(move);
      mc.player.fallDistance = 0.0F;
   }

   /** Сервер кикает за долгое зависание в воздухе, поэтому изредка нужно чуть опуститься. */
   private boolean kickTick() {
      return this.antiKick.isEnabled() && !mc.player.isOnGround() && this.ticks % (int)this.antiKickEvery.getCurrent() == 0;
   }
}
