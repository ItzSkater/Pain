package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Свой звук выстрела из лука и арбалета, слышен только тебе.
 * Выстрел ловится по состоянию: лук был натянут и отпущен, арбалет был заряжен и разрядился.
 */
@ModuleAnnotation(
   name = "ShootSounds",
   category = Category.MISC,
   description = "Свой звук выстрела из лука и арбалета"
)
public final class ShootSounds extends Module {
   public static final ShootSounds INSTANCE = new ShootSounds();
   private final ModeSetting sound = new ModeSetting("Звук", "Nonagon shoot", "Звон", "Металл", "Пузырь", "Щелчок");
   private final BooleanSetting bow = new BooleanSetting("Лук", true);
   private final BooleanSetting crossbow = new BooleanSetting("Арбалет", true);
   private final NumberSetting volume = new NumberSetting("Громкость", 1.0F, 0.1F, 2.0F, 0.05F);
   private final NumberSetting pitch = new NumberSetting("Высота", 1.0F, 0.5F, 2.0F, 0.05F);
   private final BooleanSetting randomPitch = new BooleanSetting("Чуть разная высота", false);
   private int bowTicks;
   private boolean crossbowCharged;

   private ShootSounds() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.bowTicks = 0;
      this.crossbowCharged = false;
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null) {
         return;
      }

      // лук: натягивали хотя бы три тика (меньше — стрела не вылетает) и отпустили
      boolean drawing = mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof BowItem;
      if (drawing) {
         this.bowTicks = mc.player.getItemUseTime();
      } else {
         if (this.bowTicks >= 3 && this.bow.isEnabled()) {
            this.play();
         }

         this.bowTicks = 0;
      }

      // арбалет: в прошлый тик был заряжен, в этот — уже нет, а предмет в руке тот же
      ItemStack main = mc.player.getMainHandStack();
      ItemStack off = mc.player.getOffHandStack();
      boolean holding = main.isOf(Items.CROSSBOW) || off.isOf(Items.CROSSBOW);
      boolean charged = main.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(main) || off.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(off);
      if (this.crossbowCharged && !charged && holding && this.crossbow.isEnabled()) {
         this.play();
      }

      this.crossbowCharged = charged;
   }

   private void play() {
      String name = this.sound.is("Звон") ? "hit_bell" : this.sound.is("Металл") ? "hit_metal" : this.sound.is("Пузырь") ? "hit_bubble"
         : this.sound.is("Щелчок") ? "hit_click" : "nonagon_shoot";
      float p = this.pitch.getCurrent();
      if (this.randomPitch.isEnabled()) {
         p *= 0.95F + ThreadLocalRandom.current().nextFloat() * 0.10F;
      }

      mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.of("pain", name)), p, this.volume.getCurrent()));
   }
}
