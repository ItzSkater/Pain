package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.ButtonSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Пишет в чат сообщения из файла Pain/spammer.txt. Одно сообщение — текст между двумя «&»:
 * {@code &I use arch btw&}. Всё, что вне пары «&», игнорируется, так что рядом можно держать заметки.
 */
@ModuleAnnotation(
   name = "Spammer",
   category = Category.MISC,
   description = "Пишет в чат сообщения из Pain/spammer.txt (текст между & и &), случайно или по порядку"
)
public final class Spammer extends Module {
   public static final Spammer INSTANCE = new Spammer();
   private static final Pattern MESSAGE = Pattern.compile("&([^&]+)&");
   // в пояснении нельзя писать сам знак — он начал бы сообщение
   private static final String EXAMPLE = "Сообщения для модуля Spammer. Одно сообщение - текст между двумя амперсандами.\n"
      + "Всё остальное игнорируется. Файл перечитывается сам после сохранения.\n\n"
      + "&I use arch btw&\n"
      + "&Pain Client&\n";
   private final ModeSetting order = new ModeSetting("Порядок", "Случайно", "По очереди");
   private final ModeSetting chat = new ModeSetting("Чат", "Local", "Global");
   private final NumberSetting delay = new NumberSetting("Пауза (сек)", 10.0F, 1.0F, 120.0F, 0.5F);
   private final NumberSetting spread = new NumberSetting("Разброс паузы (сек)", 2.0F, 0.0F, 30.0F, 0.5F);
   private final ButtonSetting open = new ButtonSetting("Открыть spammer.txt", () -> {
      this.reload(true);
      Util.getOperatingSystem().open(file().toFile());
   });
   private final List<String> messages = new ArrayList<>();
   private FileTime loadedStamp;
   private long lastCheck;
   private long nextAt;
   private int index;
   private int lastRandom = -1;

   private Spammer() {
   }

   private static Path file() {
      return mc.runDirectory.toPath().resolve("Pain").resolve("spammer.txt");
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.index = 0;
      this.lastRandom = -1;
      this.reload(true);
      this.nextAt = System.currentTimeMillis() + 1000L;
      if (this.messages.isEmpty()) {
         this.notify("в Pain/spammer.txt нет сообщений вида &текст&");
      } else {
         this.notify("сообщений: " + this.messages.size());
      }
   }

   /** Читает файл, если он изменился (или force); при первом запуске создаёт пример. */
   private void reload(boolean force) {
      try {
         Path file = file();
         if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, EXAMPLE, StandardCharsets.UTF_8);
         }

         FileTime stamp = Files.getLastModifiedTime(file);
         if (!force && stamp.equals(this.loadedStamp)) {
            return;
         }

         this.loadedStamp = stamp;
         this.messages.clear();
         Matcher matcher = MESSAGE.matcher(Files.readString(file, StandardCharsets.UTF_8));

         while (matcher.find()) {
            String text = matcher.group(1).replaceAll("[\\n\\r§]", " ").trim();
            if (!text.isEmpty()) {
               this.messages.add(text.length() > 250 ? text.substring(0, 250) : text);
            }
         }

         this.index = 0;
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   private void notify(String text) {
      Pain.getInstance().getNotifyManager().addNotification("", Text.of("Spammer: " + text));
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.getNetworkHandler() == null) {
         return;
      }

      long now = System.currentTimeMillis();
      if (now - this.lastCheck > 2000L) {
         this.lastCheck = now;
         this.reload(false);
      }

      if (now < this.nextAt || this.messages.isEmpty()) {
         return;
      }

      String text;
      if (this.order.is("Случайно")) {
         int pick = ThreadLocalRandom.current().nextInt(this.messages.size());
         // не повторяем одно и то же сообщение два раза подряд
         if (pick == this.lastRandom && this.messages.size() > 1) {
            pick = (pick + 1) % this.messages.size();
         }

         this.lastRandom = pick;
         text = this.messages.get(pick);
      } else {
         text = this.messages.get(this.index % this.messages.size());
         ++this.index;
      }

      if (text.startsWith("/")) {
         mc.getNetworkHandler().sendChatCommand(text.substring(1));
      } else {
         mc.getNetworkHandler().sendChatMessage(this.chat.is("Global") ? "!" + text : text);
      }

      float extra = this.spread.getCurrent() > 0.0F ? ThreadLocalRandom.current().nextFloat() * this.spread.getCurrent() : 0.0F;
      this.nextAt = now + (long)((this.delay.getCurrent() + extra) * 1000.0F);
   }
}
