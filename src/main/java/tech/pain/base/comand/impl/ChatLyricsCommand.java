package tech.pain.base.comand.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;
import tech.pain.base.comand.api.CommandAbstract;
import tech.pain.base.comand.impl.args.PlayerArgumentType;
import tech.pain.client.modules.impl.misc.ChatLyrics;
import tech.pain.utility.game.other.MessageUtil;

/** ^chatlyrics nick <ник> — кому слать строки песни в режиме Whisper. */
public class ChatLyricsCommand extends CommandAbstract {
   public ChatLyricsCommand() {
      super("chatlyrics");
   }

   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("nick").then(arg("player", PlayerArgumentType.create()).executes((context) -> {
         String name = (String)context.getArgument("player", String.class);
         ChatLyrics.INSTANCE.setNick(name);
         MessageUtil.displayInfo(String.valueOf(Formatting.GRAY) + "ChatLyrics: шёпот будет идти игроку " + String.valueOf(Formatting.WHITE) + name);
         return 1;
      })));
      builder.then(literal("nick").executes((context) -> {
         String nick = ChatLyrics.INSTANCE.getNick();
         MessageUtil.displayInfo(String.valueOf(Formatting.GRAY) + (nick.isEmpty() ? "ChatLyrics: ник не задан" : "ChatLyrics: ник " + String.valueOf(Formatting.WHITE) + nick));
         return 1;
      }));
   }
}
