package tech.pain.base.discord;

import java.io.IOException;
import lombok.Generated;
import net.minecraft.util.Identifier;
import tech.pain.Pain;
import tech.pain.base.discord.utils.DiscordEventHandlers;
import tech.pain.base.discord.utils.DiscordRPC;
import tech.pain.base.discord.utils.DiscordRichPresence;
import tech.pain.utility.render.display.BufferUtil;

public class DiscordManager {
   private volatile boolean running;
   private DiscordManager.DiscordInfo info = new DiscordManager.DiscordInfo("Unknown", "", "");
   private Identifier avatarId;
   private String appId;
   private java.util.function.Supplier<DiscordRichPresence> presence;
   // каждый запуск получает свой номер: старый поток обратных вызовов видит, что его сменили, и выходит
   private volatile int generation;

   public DiscordManager() {
   }

   /**
    * Включает статус от имени приложения appId. Название в строке «Играет в …» Discord берёт из названия
    * этого приложения, поэтому для другой игры нужен другой ID. presence собирается заново при каждом обновлении.
    */
   public synchronized void start(String appId, java.util.function.Supplier<DiscordRichPresence> presence) {
      this.presence = presence;
      if (this.running && appId.equals(this.appId)) {
         this.update();
         return;
      }

      this.stopRPC();
      this.appId = appId;

      try {
         DiscordEventHandlers handlers = (new DiscordEventHandlers.Builder()).ready((user) -> {
            this.setInfo(new DiscordManager.DiscordInfo(user.username, "https://cdn.discordapp.com/avatars/" + user.userId + "/" + user.avatar + ".png", user.userId));
            this.update();
         }).build();
         DiscordRPC.INSTANCE.Discord_Initialize(appId, handlers, true, "");
         this.running = true;
         int mine = ++this.generation;
         Thread thread = new Thread(() -> {
            try {
               while (this.running && this.generation == mine) {
                  DiscordRPC.INSTANCE.Discord_RunCallbacks();
                  this.load();
                  Thread.sleep(2000L);
               }
            } catch (Throwable ignored) {
            }
         }, "Discord-RPC");
         thread.setDaemon(true);
         thread.start();
      } catch (Throwable e) {
         this.running = false;
      }
   }

   /** Пересобирает и отправляет статус текущего приложения. */
   public synchronized void update() {
      if (!this.running || this.presence == null) {
         return;
      }

      try {
         DiscordRPC.INSTANCE.Discord_UpdatePresence(this.presence.get());
      } catch (Throwable ignored) {
      }
   }

   public synchronized void stopRPC() {
      if (!this.running) {
         return;
      }

      this.running = false;
      ++this.generation;

      try {
         DiscordRPC.INSTANCE.Discord_ClearPresence();
         DiscordRPC.INSTANCE.Discord_Shutdown();
      } catch (Throwable ignored) {
      }
   }

   public void load() throws IOException {
      if (this.avatarId == null && !this.info.avatarUrl.isEmpty()) {
         this.avatarId = BufferUtil.registerDynamicTexture("avatar-", BufferUtil.getHeadFromURL(this.info.avatarUrl));
      }

   }

   @Generated
   public void setRunning(boolean running) {
      this.running = running;
   }

   @Generated
   public void setInfo(DiscordManager.DiscordInfo info) {
      this.info = info;
   }

   @Generated
   public void setAvatarId(Identifier avatarId) {
      this.avatarId = avatarId;
   }

   @Generated
   public boolean isRunning() {
      return this.running;
   }

   @Generated
   public DiscordManager.DiscordInfo getInfo() {
      return this.info;
   }

   @Generated
   public Identifier getAvatarId() {
      return this.avatarId;
   }

   public static record DiscordInfo(String userName, String avatarUrl, String userId) {
      public DiscordInfo(String userName, String avatarUrl, String userId) {
         this.userName = userName;
         this.avatarUrl = avatarUrl;
         this.userId = userId;
      }

      public String userName() {
         return this.userName;
      }

      public String avatarUrl() {
         return this.avatarUrl;
      }

      public String userId() {
         return this.userId;
      }
   }
}
