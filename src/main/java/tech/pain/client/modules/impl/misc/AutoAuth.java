package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import tech.pain.base.events.impl.server.EventPacket;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;

@ModuleAnnotation(
   name = "AutoAuth",
   category = Category.MISC,
   description = "Авто регистрация"
)
public final class AutoAuth extends Module {
   public static final AutoAuth INSTANCE = new AutoAuth();

   private AutoAuth() {
   }

   @EventTarget
   public void onReceive(EventPacket event) {
      if (event.isReceive()) {
         Packet var3 = event.getPacket();
         if (var3 instanceof GameMessageS2CPacket) {
            GameMessageS2CPacket chatMessagePacket = (GameMessageS2CPacket)var3;
            if (mc.getNetworkHandler() == null) {
               return;
            }

            // пароль не хранится в исходниках: берётся из файла Pain/autoauth.txt в папке игры
            String password = this.password();
            if (password == null) {
               return;
            }

            String content = chatMessagePacket.content().getString().toLowerCase();
            if (content.contains("зарегистрируйтесь") || content.contains("/register")) {
               mc.getNetworkHandler().sendChatCommand("register %s %s".formatted(new Object[]{password, password}));
            }
         }

      }
   }

   private boolean warned;

   /** Первая строка файла Pain/autoauth.txt; если файла нет — один раз подсказываем, куда его положить. */
   private String password() {
      try {
         java.nio.file.Path file = mc.runDirectory.toPath().resolve("Pain").resolve("autoauth.txt");
         if (java.nio.file.Files.exists(file)) {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(file);
            String value = lines.isEmpty() ? "" : lines.get(0).trim();
            if (!value.isEmpty()) {
               return value;
            }
         }

         if (!this.warned) {
            this.warned = true;
            tech.pain.Pain.getInstance().getNotifyManager().addNotification("\uf06a", net.minecraft.text.Text.of("AutoAuth: впиши пароль в " + file));
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      return null;
   }
}
