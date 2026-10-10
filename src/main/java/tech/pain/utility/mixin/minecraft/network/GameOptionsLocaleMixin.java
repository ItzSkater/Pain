package tech.pain.utility.mixin.minecraft.network;

import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Лаунчер может записать в options.txt язык без региона («ru» вместо «ru_ru»). Ванильный клиент
 * так не умеет, и антиботы кикают за это с «malformed packet». Серверу отправляем нормальный код.
 */
@Mixin(GameOptions.class)
public class GameOptionsLocaleMixin {
   @Inject(method = "getSyncedOptions", at = @At("RETURN"), cancellable = true)
   private void pain$fixLocale(CallbackInfoReturnable<SyncedClientOptions> cir) {
      SyncedClientOptions o = cir.getReturnValue();
      String lang = o.language();
      if (lang != null && lang.matches("[a-z]{2,3}_[a-z]{2,3}")) {
         return;
      }

      String base = lang == null ? "" : lang.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
      String fixed = base.equals("ru") ? "ru_ru" : base.equals("uk") ? "uk_ua" : base.equals("de") ? "de_de" : base.equals("es") ? "es_es" : base.equals("fr") ? "fr_fr" : "en_us";
      cir.setReturnValue(new SyncedClientOptions(fixed, o.viewDistance(), o.chatVisibility(), o.chatColorsEnabled(), o.playerModelParts(), o.mainArm(), o.filtersText(), o.allowsServerListing(), o.particleStatus()));
   }
}
