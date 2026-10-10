package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Звук попадания, как в шутерных читах: короткий звон при каждом ударе, слышен только тебе.
 * Звуки синтезированы для Pain, чужих записей здесь нет. Идея Firrty.
 */
@ModuleAnnotation(
   name = "HitSound",
   category = Category.MISC,
   description = "Звук при ударе по цели: звон, металл, пузырь или щелчок"
)
public final class HitSound extends Module {
   public static final HitSound INSTANCE = new HitSound();
   private final ModeSetting sound = new ModeSetting("Звук", "Звон", "Металл", "Пузырь", "Щелчок");
   private final NumberSetting volume = new NumberSetting("Громкость", 1.0F, 0.1F, 2.0F, 0.05F);
   private final NumberSetting pitch = new NumberSetting("Высота", 1.0F, 0.5F, 2.0F, 0.05F);
   private final BooleanSetting randomPitch = new BooleanSetting("Чуть разная высота", true);
   private final BooleanSetting critSound = new BooleanSetting("Свой звук на крит", true);
   private final BooleanSetting onlyLiving = new BooleanSetting("Только по живым целям", true);

   private HitSound() {
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      if (mc.player == null || event.getTarget() == null || this.onlyLiving.isEnabled() && !(event.getTarget() instanceof LivingEntity)) {
         return;
      }

      boolean crit = mc.player.fallDistance > 0.0F && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater();
      String name = crit && this.critSound.isEnabled() ? "hit_crit"
         : this.sound.is("Металл") ? "hit_metal" : this.sound.is("Пузырь") ? "hit_bubble" : this.sound.is("Щелчок") ? "hit_click" : "hit_bell";
      float p = this.pitch.getCurrent();
      if (this.randomPitch.isEnabled()) {
         // одинаковый звук сто раз подряд режет слух — слегка гуляем по высоте
         p *= 0.94F + ThreadLocalRandom.current().nextFloat() * 0.12F;
      }

      mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.of("pain", name)), p, this.volume.getCurrent()));
   }
}
