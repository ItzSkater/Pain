package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.PlayerIntersectionUtil;
import tech.pain.utility.game.player.rotation.Rotation;

/**
 * Быстро бросает под себя взрывные зелья с полезными эффектами. Взгляд опускается вниз скрыто —
 * камера не дёргается, а угол броска уходит на сервер прямо в пакете использования предмета.
 * Зелье бросается, только если хотя бы одного его эффекта на тебе сейчас нет.
 */
@ModuleAnnotation(
   name = "AutoBuff",
   category = Category.COMBAT,
   description = "Смотрит вниз и быстро бросает под себя все взрывные зелья с баффами"
)
public final class AutoBuff extends Module {
   public static final AutoBuff INSTANCE = new AutoBuff();
   private final ModeSetting mode = new ModeSetting("Режим", "Один раз", "Постоянно");
   private final NumberSetting delay = new NumberSetting("Задержка между бросками, тики", 1.0F, 0.0F, 10.0F, 1.0F);
   private final NumberSetting renew = new NumberSetting("Обновлять за, сек", 5.0F, 0.0F, 60.0F, 1.0F);
   private final BooleanSetting fromInventory = new BooleanSetting("Брать из инвентаря", true);
   private final BooleanSetting groundOnly = new BooleanSetting("Только на земле", true);
   private final BooleanSetting heal = new BooleanSetting("Лечение", true);
   private final NumberSetting healBelow = new NumberSetting("Лечить при HP ниже", 12.0F, 1.0F, 20.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.heal.isEnabled());
   // эффект → тик, до которого ждём, что он появится после броска (чтобы не кинуть второе такое же зелье)
   private final Map<StatusEffect, Integer> pending = new HashMap<>();
   private int cooldown;
   private int idle;

   private AutoBuff() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.pending.clear();
      this.cooldown = 0;
      this.idle = 0;
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null) {
         return;
      }

      int now = mc.player.age;
      this.pending.values().removeIf(until -> until <= now || until > now + 100);
      if (mc.currentScreen != null || this.groundOnly.isEnabled() && !mc.player.isOnGround()) {
         return;
      }

      if (this.cooldown > 0) {
         --this.cooldown;
         return;
      }

      boolean thrown = false;

      // при нулевой задержке за один тик уходят все нужные зелья
      for (int guard = 0; guard < 9; ++guard) {
         int slot = this.findPotion();
         if (slot == -1) {
            break;
         }

         this.throwFrom(slot);
         thrown = true;
         if (this.delay.getCurrent() > 0.0F) {
            this.cooldown = (int)this.delay.getCurrent();
            break;
         }
      }

      if (thrown) {
         this.idle = 0;
      } else if (this.mode.is("Один раз") && ++this.idle > 3) {
         // бросать больше нечего
         this.toggle();
      }
   }

   /** Слот инвентаря (0–8 хотбар, 9–35 остальное) с зельем, которое сейчас нужно, или -1. */
   private int findPotion() {
      int last = this.fromInventory.isEnabled() ? 36 : 9;

      for (int i = 0; i < last; ++i) {
         if (this.needed(mc.player.getInventory().getStack(i))) {
            return i;
         }
      }

      return -1;
   }

   private boolean needed(ItemStack stack) {
      if (!stack.isOf(Items.SPLASH_POTION)) {
         return false;
      }

      PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
      if (contents == null) {
         return false;
      }

      boolean useful = false;
      boolean any = false;

      for (StatusEffectInstance effect : contents.getEffects()) {
         StatusEffect type = effect.getEffectType().value();
         any = true;
         // зелье хоть с одним вредным эффектом под себя не бросаем
         if (!type.isBeneficial()) {
            return false;
         }

         if (this.pending.containsKey(type)) {
            continue;
         }

         if (type.isInstant()) {
            useful |= this.heal.isEnabled() && mc.player.getHealth() < this.healBelow.getCurrent();
         } else {
            StatusEffectInstance active = mc.player.getStatusEffect(effect.getEffectType());
            useful |= active == null
               || active.getAmplifier() < effect.getAmplifier()
               || !active.isInfinite() && (float)active.getDuration() < this.renew.getCurrent() * 20.0F;
         }
      }

      return any && useful;
   }

   private void throwFrom(int slot) {
      ItemStack stack = mc.player.getInventory().getStack(slot);
      PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
      List<RegistryEntry<StatusEffect>> effects = new ArrayList<>();
      if (contents != null) {
         for (StatusEffectInstance effect : contents.getEffects()) {
            effects.add(effect.getEffectType());
         }
      }

      int selected = mc.player.getInventory().selectedSlot;
      int hotbar = slot < 9 ? slot : selected;
      int syncId = mc.player.playerScreenHandler.syncId;
      // зелье из инвентаря на время броска меняется местами с предметом в руке, потом возвращается обратно
      if (slot >= 9) {
         mc.interactionManager.clickSlot(syncId, slot, hotbar, SlotActionType.SWAP, mc.player);
      }

      final float yaw = mc.player.getYaw();
      RotationComponent.update(new Rotation(yaw, 90.0F), 360.0F, 360.0F, 360.0F, 360.0F, 3, 5, false);
      if (hotbar != selected) {
         mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hotbar));
      }

      PlayerIntersectionUtil.sendSequencedPacket(seq -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, seq, yaw, 90.0F));
      mc.player.swingHand(Hand.MAIN_HAND);
      if (hotbar != selected) {
         mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selected));
      }

      if (slot >= 9) {
         mc.interactionManager.clickSlot(syncId, slot, hotbar, SlotActionType.SWAP, mc.player);
      }

      // зелье долетает и срабатывает не мгновенно: полторы секунды такие же эффекты повторно не бросаем
      for (RegistryEntry<StatusEffect> effect : effects) {
         this.pending.put(effect.value(), mc.player.age + 30);
      }
   }
}
