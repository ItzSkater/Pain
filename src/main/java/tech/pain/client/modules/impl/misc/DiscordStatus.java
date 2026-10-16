package tech.pain.client.modules.impl.misc;

import net.minecraft.client.MinecraftClient;
import tech.pain.Pain;
import tech.pain.base.discord.DiscordManager;
import tech.pain.base.discord.utils.DiscordRichPresence;
import tech.pain.base.discord.utils.RPCButton;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;

/**
 * Статус в Discord «Играет в Pain Client». Если включён GTA6RPC, он главнее: статус один на всех,
 * поэтому решает sync() — смотрит, какие из двух модулей включены, и запускает нужное приложение.
 */
@ModuleAnnotation(
   name = "DiscordRPC",
   category = Category.MISC,
   description = "Статус в Discord: играешь в Pain Client, с лого, версией, ником и кнопками"
)
public final class DiscordStatus extends Module {
   public static final DiscordStatus INSTANCE = new DiscordStatus();
   // имя в строке «Играет в …» — это название приложения с этим ID в Discord
   private static final String APP_ID = "1558422354680619058";
   private static final String LOGO_URL = "https://raw.githubusercontent.com/ItzSkater/Pain/main/site/public/logo.png";
   private final BooleanSetting showNick = new BooleanSetting("Показывать ник", true);
   private final BooleanSetting buttons = new BooleanSetting("Кнопки GitHub и Telegram", true);
   private long started = System.currentTimeMillis() / 1000L;

   private DiscordStatus() {
      // как и раньше, статус включён сразу; выключенное состояние запомнится в конфиге
      this.setEnabled(true);
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.started = System.currentTimeMillis() / 1000L;
      sync(this, true);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      sync(this, false);
   }

   /**
    * Включает статус, который сейчас должен быть. changed/state — модуль, который прямо сейчас
    * переключается: его флаг enabled в этот момент ещё старый.
    */
   public static void sync(Module changed, boolean state) {
      DiscordManager manager = Pain.getInstance() == null ? null : Pain.getInstance().getDiscordManager();
      if (manager == null) {
         return;
      }

      boolean gta = changed == Gta6Status.INSTANCE ? state : Gta6Status.INSTANCE.isEnabled();
      boolean pain = changed == INSTANCE ? state : INSTANCE.isEnabled();
      if (gta && Gta6Status.INSTANCE.ready()) {
         manager.start(Gta6Status.INSTANCE.appId(), Gta6Status.INSTANCE::presence);
      } else if (pain) {
         manager.start(APP_ID, INSTANCE::presence);
      } else {
         manager.stopRPC();
      }
   }

   public static void sync() {
      sync(null, false);
   }

   private DiscordRichPresence presence() {
      String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("pain").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
      DiscordRichPresence.Builder builder = new DiscordRichPresence.Builder().setStartTimestamp(this.started).setDetails("Pain v" + version).setLargeImage(LOGO_URL, "Pain Client");
      if (this.showNick.isEnabled()) {
         builder.setState("Ник » " + MinecraftClient.getInstance().getSession().getUsername());
      }

      if (this.buttons.isEnabled()) {
         builder.setButtons(RPCButton.create("GitHub", "https://github.com/ItzSkater/Pain"), RPCButton.create("Telegram", "https://t.me/painclientbtw"));
      }

      return builder.build();
   }
}
