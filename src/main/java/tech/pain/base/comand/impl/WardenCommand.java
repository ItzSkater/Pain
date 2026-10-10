package tech.pain.base.comand.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;
import tech.pain.base.comand.api.CommandAbstract;
import tech.pain.client.modules.impl.player.AutoWarden;
import tech.pain.utility.game.other.MessageUtil;

/** ^warden warden <анка> | home <хом> | storage <анка> | status — настройка модуля AutoWarden. */
public class WardenCommand extends CommandAbstract {
   public WardenCommand() {
      super("warden");
   }

   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      for (String key : new String[]{"warden", "home", "storage"}) {
         builder.then(literal(key).then(arg("home", StringArgumentType.word()).executes((context) -> {
            String home = (String)context.getArgument("home", String.class);
            AutoWarden.INSTANCE.setHome(key, home);
            MessageUtil.displayInfo(String.valueOf(Formatting.GRAY) + "AutoWarden: " + key + " = " + String.valueOf(Formatting.WHITE) + home);
            return 1;
         })));
      }

      builder.then(literal("status").executes((context) -> {
         MessageUtil.displayInfo(String.valueOf(Formatting.GRAY) + "AutoWarden: " + AutoWarden.INSTANCE.status() + " | анка вардена=" + AutoWarden.INSTANCE.getHome("warden") + ", хом=" + AutoWarden.INSTANCE.getHome("home") + ", анка склада=" + AutoWarden.INSTANCE.getHome("storage"));
         return 1;
      }));
   }
}
