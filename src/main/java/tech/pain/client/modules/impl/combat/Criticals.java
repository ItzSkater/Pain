package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Пакетные криты: перед ударом с земли отправляет серверу два пакета позиции — чуть выше и обратно,
 * оба «в воздухе». Для сервера игрок в момент удара падает, поэтому удар засчитывается критическим.
 */
@ModuleAnnotation(
   name = "Criticals",
   category = Category.COMBAT,
   description = "Пакетные криты: каждый удар с земли становится критическим без прыжка"
)
public final class Criticals extends Module {
   public static final Criticals INSTANCE = new Criticals();
   private final ModeSetting mode = new ModeSetting("Режим", "2 пакета", "4 пакета");
   private final BooleanSetting fullCharge = new BooleanSetting("Только заряженный удар", false);
   private final NumberSetting height = new NumberSetting("Высота", 0.0625F, 0.02F, 0.3F, 0.0025F);
   private final BooleanSetting onlyLiving = new BooleanSetting("Только по живым", true);

   private Criticals() {
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      // фейк-игрок существует только на клиенте — пакеты за него серверу не нужны
      if (mc.player == null || mc.getNetworkHandler() == null || event.isCancelled() || tech.pain.client.modules.impl.misc.FakePlayer.isFake(event.getTarget())) {
         return;
      }

      if (this.onlyLiving.isEnabled() && !(event.getTarget() instanceof LivingEntity)) {
         return;
      }

      // крит возможен только с земли: в воде, лаве, на лестнице, верхом и со слепотой игра его не даёт
      if (!mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing()
         || mc.player.hasVehicle() || mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || mc.player.getAbilities().flying) {
         return;
      }

      if (this.fullCharge.isEnabled() && mc.player.getAttackCooldownProgress(0.5F) < 0.9F) {
         return;
      }

      // булавой с земли бить критом нет смысла: её урон считается от высоты падения
      if (mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.MaceItem) {
         return;
      }

      double x = mc.player.getX();
      double y = mc.player.getY();
      double z = mc.player.getZ();
      boolean collision = mc.player.horizontalCollision;
      double h = (double)this.height.getCurrent();
      // «4 пакета» повторяет подскок второй раз пониже — надёжнее на серверах, где первый подскок съедается
      double[] offsets = this.mode.is("4 пакета") ? new double[]{h, 0.0, h * 0.8, 0.0} : new double[]{h, 0.0};

      for (double offset : offsets) {
         mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + offset, z, false, collision));
      }

      // частицы крита на цели — чтобы было видно, что сработало
      mc.player.addCritParticles(event.getTarget());
   }
}
