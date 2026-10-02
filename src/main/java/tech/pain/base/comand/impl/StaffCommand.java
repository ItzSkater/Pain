package tech.pain.base.comand.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import tech.pain.Pain;
import tech.pain.base.comand.api.CommandAbstract;
import tech.pain.base.comand.impl.args.FriendArgumentType;
import tech.pain.base.comand.impl.args.PlayerArgumentType;
import tech.pain.utility.game.other.MessageUtil;

public class StaffCommand extends CommandAbstract {
   public StaffCommand() {
      super("friend");
   }

   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("add").then(arg("player", PlayerArgumentType.create()).executes((context) -> {
         String name = (String)context.getArgument("player", String.class);
         if (Pain.getInstance().getStaffManager().getItems().contains(name)) {
            MessageUtil.displayMessage(MessageUtil.LogLevel.WARN, "Уже добавлен " + name);
            return 1;
         } else {
            Pain.getInstance().getStaffManager().add(name);
            MessageUtil.displayMessage(MessageUtil.LogLevel.INFO, "Добавили " + name);
            return 1;
         }
      })));
      builder.then(literal("remove").then(arg("player", FriendArgumentType.create()).executes((context) -> {
         String nickname = (String)context.getArgument("player", String.class);
         Pain.getInstance().getStaffManager().remove(nickname);
         MessageUtil.displayMessage(MessageUtil.LogLevel.INFO, nickname + " удален из стаффа");
         return 1;
      })));
      builder.then(literal("list").executes((commandContext) -> {
         MessageUtil.displayMessage(MessageUtil.LogLevel.INFO, Pain.getInstance().getStaffManager().getItems().toString());
         return 1;
      }));
   }
}
