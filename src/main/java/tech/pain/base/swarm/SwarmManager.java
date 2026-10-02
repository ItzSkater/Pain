package tech.pain.base.swarm;

import java.util.ArrayList;
import java.util.List;

public final class SwarmManager {
   public static final SwarmManager INSTANCE = new SwarmManager();

   private final List<SwarmBot> bots = new ArrayList<>();
   private String host = "localhost";
   private int port = 25565;
   private String baseNick = "Bot";

   private SwarmManager() {
   }

   public void setTarget(String host, int port) {
      this.host = host;
      this.port = port;
   }

   public void setBaseNick(String baseNick) {
      this.baseNick = baseNick;
   }

   public String getHost() {
      return this.host;
   }

   public int getPort() {
      return this.port;
   }

   public String getBaseNick() {
      return this.baseNick;
   }

   public int count() {
      return this.bots.size();
   }

   public synchronized void start(int amount) {
      for (int i = 0; i < amount; ++i) {
         String name = this.baseNick + (this.bots.size() + 1);
         SwarmBot bot = new SwarmBot(name);
         this.bots.add(bot);
         bot.connect(this.host, this.port);
      }
   }

   public synchronized void stopAll() {
      for (SwarmBot bot : this.bots) {
         bot.disconnect();
      }

      this.bots.clear();
   }

   public List<SwarmBot> getBots() {
      return this.bots;
   }
}
