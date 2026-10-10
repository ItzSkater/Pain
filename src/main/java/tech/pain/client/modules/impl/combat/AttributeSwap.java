package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EntityTypeTags;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Подмена предмета на время удара. Сервер пересчитывает урон и скорость атаки от предмета в руке
 * только раз в тик, а чары удара берёт с того, что в руке прямо сейчас. Поэтому, если в одном тике
 * переключить слот, ударить и вернуть слот, удар пройдёт с базовым уроном и перезарядкой предмета,
 * который ты держал, но с чарами того, на который переключился.
 */
@ModuleAnnotation(
   name = "AttributeSwap",
   category = Category.COMBAT,
   description = "На время удара берёт из хотбара предмет с лучшими чарами: урон твоего оружия плюс чужие чары"
)
public final class AttributeSwap extends Module {
   public static final AttributeSwap INSTANCE = new AttributeSwap();
   private final ModeSetting pick = new ModeSetting("Брать", "Лучшие чары", "Заданный слот");
   private final NumberSetting slot = new NumberSetting("Слот", 2.0F, 1.0F, 9.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.pick.is("Заданный слот"));
   private final BooleanSetting fullCharge = new BooleanSetting("Только при полной перезарядке", true);
   private final BooleanSetting back = new BooleanSetting("Возвращать слот", true);
   private int saved = -1;

   private AttributeSwap() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.restore();
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      Entity target = event.getTarget();
      if (mc.player == null || mc.getNetworkHandler() == null || !(target instanceof LivingEntity living) || this.saved != -1) {
         return;
      }

      // слабый удар подменять незачем: чары добавятся, но база и так срезана перезарядкой
      if (this.fullCharge.isEnabled() && mc.player.getAttackCooldownProgress(0.5F) < 0.9F) {
         return;
      }

      int current = mc.player.getInventory().selectedSlot;
      int best = this.pick.is("Заданный слот") ? (int)this.slot.getCurrent() - 1 : this.bestSlot(living, current);
      if (best == current || best < 0 || best > 8) {
         return;
      }

      this.saved = current;
      mc.player.getInventory().selectedSlot = best;
      mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(best));
   }

   /** Вызывается из миксина сразу после отправки удара — слот возвращается в том же тике. */
   public void afterAttack() {
      if (this.back.isEnabled()) {
         this.restore();
      } else {
         this.saved = -1;
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      // удар могли отменить (например, по FakePlayer) — тогда слот возвращаем на следующем тике
      if (this.saved != -1) {
         this.afterAttack();
      }
   }

   private void restore() {
      if (this.saved != -1 && mc.player != null && mc.getNetworkHandler() != null) {
         mc.player.getInventory().selectedSlot = this.saved;
         mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(this.saved));
      }

      this.saved = -1;
   }

   private int bestSlot(LivingEntity target, int current) {
      int best = current;
      float bestBonus = bonus(mc.player.getInventory().getStack(current), target);

      for (int i = 0; i < 9; ++i) {
         float b = bonus(mc.player.getInventory().getStack(i), target);
         if (b > bestBonus + 0.01F) {
            bestBonus = b;
            best = i;
         }
      }

      return best;
   }

   /** Сколько урона добавляют чары предмета по этой цели. */
   private static float bonus(ItemStack stack, LivingEntity target) {
      if (stack.isEmpty()) {
         return 0.0F;
      }

      float total = 0.0F;

      for (RegistryEntry<Enchantment> entry : stack.getEnchantments().getEnchantments()) {
         int level = stack.getEnchantments().getLevel(entry);
         if (entry.matchesKey(Enchantments.SHARPNESS)) {
            total += 0.5F * (float)level + 0.5F;
         } else if (entry.matchesKey(Enchantments.SMITE) && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            total += 2.5F * (float)level;
         } else if (entry.matchesKey(Enchantments.BANE_OF_ARTHROPODS) && target.getType().isIn(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            total += 2.5F * (float)level;
         }
      }

      return total;
   }
}
