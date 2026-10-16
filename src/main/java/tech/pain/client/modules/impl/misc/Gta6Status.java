package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.text.Text;
import tech.pain.Pain;
import tech.pain.base.discord.utils.DiscordRichPresence;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.StringSetting;

/**
 * Шуточный статус в Discord: «Играет в Grand Theft Auto VI». Discord пишет в статусе название
 * приложения, поэтому нужно своё приложение с таким названием на discord.com/developers —
 * его ID вставляется в настройку. Пока модуль включён, он заменяет обычный статус Pain.
 */
@ModuleAnnotation(
   name = "GTA6RPC",
   category = Category.MISC,
   description = "Статус в Discord, будто ты играешь в GTA 6 (нужно своё приложение Discord с таким названием)"
)
public final class Gta6Status extends Module {
   public static final Gta6Status INSTANCE = new Gta6Status();
   private static final String[] ACTIVITIES = {
      "Свободная игра", "Катается по Вайс-Сити", "Ограбление банка", "Уходит от погони", "На пляже Вайс-Бич", "Миссия: «Бонни и Клайд»", "Тюнингует тачку", "В клубе на Оушен-Драйв"
   };
   private final StringSetting appId = new StringSetting("ID приложения", "1560558000584785920", 24);
   private final StringSetting image = new StringSetting("Картинка: ключ или ссылка", "logo", 200);
   private final BooleanSetting rotate = new BooleanSetting("Менять занятие", true);
   private long started = System.currentTimeMillis() / 1000L;
   private int ticks;
   private boolean warned;

   private Gta6Status() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.started = System.currentTimeMillis() / 1000L;
      this.ticks = 0;
      if (!this.ready() && !this.warned) {
         this.warned = true;
         Pain.getInstance().getNotifyManager().addNotification("", Text.of("GTA6RPC: создай приложение «Grand Theft Auto VI» на discord.com/developers и вставь его ID в настройки"));
      }

      DiscordStatus.sync(this, true);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      DiscordStatus.sync(this, false);
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      // ID могли вставить уже после включения; занятие меняется раз в пять минут
      if (++this.ticks % 100 == 0 && this.ready()) {
         DiscordStatus.sync(this, true);
      }
   }

   public boolean ready() {
      return this.appId().matches("\\d{15,22}");
   }

   public String appId() {
      return this.appId.get().trim();
   }

   DiscordRichPresence presence() {
      int slot = this.rotate.isEnabled() ? (int)((System.currentTimeMillis() / 1000L - this.started) / 300L) : 0;
      String activity = ACTIVITIES[slot % ACTIVITIES.length];
      return new DiscordRichPresence.Builder()
         .setStartTimestamp(this.started)
         .setDetails(activity)
         .setState("Вайс-Сити, Леонида")
         .setLargeImage(this.image.get().trim(), "Grand Theft Auto VI")
         .build();
   }
}
