package tech.pain.base.discord.callbacks;

import com.sun.jna.Callback;
import tech.pain.base.discord.utils.DiscordUser;

public interface ReadyCallback extends Callback {
   void apply(DiscordUser var1);
}
