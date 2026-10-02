package tech.pain.base.comand.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import tech.pain.base.comand.api.CommandAbstract;
import tech.pain.base.swarm.SwarmManager;
import tech.pain.utility.game.other.MessageUtil;

public class SwarmCommand extends CommandAbstract {
   public SwarmCommand() {
      super("swarm");
   }

   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("ip").then(arg("address", StringArgumentType.string()).executes((context) -> {
         String raw = StringArgumentType.getString(context, "address");
         String host = raw;
         int port = 25565;
         int idx = raw.lastIndexOf(58);
         if (idx > 0) {
            host = raw.substring(0, idx);

            try {
               port = Integer.parseInt(raw.substring(idx + 1));
            } catch (NumberFormatException var7) {
               port = 25565;
            }
         }

         SwarmManager.INSTANCE.setTarget(host, port);
         MessageUtil.displayInfo("Swarm: сервер " + host + ":" + port);
         return 1;
      })));

      builder.then(literal("nick").then(arg("base", StringArgumentType.string()).executes((context) -> {
         String base = StringArgumentType.getString(context, "base");
         SwarmManager.INSTANCE.setBaseNick(base);
         MessageUtil.displayInfo("Swarm: ник ботов " + base + "N");
         return 1;
      })));

      builder.then(literal("start").then(arg("count", IntegerArgumentType.integer(1, 50)).executes((context) -> {
         int count = IntegerArgumentType.getInteger(context, "count");
         SwarmManager.INSTANCE.start(count);
         MessageUtil.displayInfo("Swarm: запуск " + count + " ботов на " + SwarmManager.INSTANCE.getHost() + ":" + SwarmManager.INSTANCE.getPort());
         return 1;
      })));

      builder.then(literal("stop").executes((context) -> {
         SwarmManager.INSTANCE.stopAll();
         MessageUtil.displayInfo("Swarm: все боты остановлены");
         return 1;
      }));

      builder.then(literal("list").executes((context) -> {
         MessageUtil.displayInfo("Swarm: активно ботов " + SwarmManager.INSTANCE.count());
         return 1;
      }));
   }
}
