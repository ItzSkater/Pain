package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import java.util.Random;
import net.minecraft.entity.LivingEntity;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.utility.math.Timer;

@ModuleAnnotation(
   name = "AutoEZ",
   category = Category.MISC,
   description = "Пишет фразу в чат после убийства цели"
)
public final class AutoEZ extends Module {
   public static final AutoEZ INSTANCE = new AutoEZ();

   private final ModeSetting language = new ModeSetting("Язык", "Русский", "English", "Espanol", "Chinese", "Ukrainian");
   private final BooleanSetting global = new BooleanSetting("Глобал (!)", false);

   private final Random rng = new Random();
   private final Timer cooldown = new Timer();
   private LivingEntity lastTarget;

   private static final String[] RU = {
      "%player% АНБРЕЙН ГЕТАЙ PAIN КЛИЕНТ",
      "%player% ТВОЯ МАТЬ БУДЕТ СЛЕДУЮЩЕЙ))))",
      "%player% БИЧАРА БЕЗ ТХ",
      "%player% ЧЕ ТАК БЫСТРО СЛИЛСЯ ТО А?",
      "%player% ПЛАЧЬ",
      "%player% УПССС ЗАБЫЛ КИЛЛКУ ВЫРУБИТЬ",
      "ОДНОКЛЕТОЧНЫЙ %player% БЫЛ ВПЕНЕН",
      "%player% ИЗИ АХАХАХАХА",
      "%player% БОЖЕ МНЕ ТЕБЯ ЖАЛКО ВГЕТАЙ ТХ",
      "%player% ОПРАВДЫВАЙСЯ ЧЕ СДОХ ТО)))",
      "%player% СПС ЗА ИГРУ)))"
   };

   private static final String[] EN = {
      "%player% get good, download PAIN client",
      "%player% cry harder))))",
      "%player% trash without totems",
      "%player% why'd you die so fast huh?",
      "%player% cry",
      "%player% oops forgot to turn off killaura",
      "single-celled %player% got destroyed",
      "%player% EZ hahahaha",
      "%player% i feel bad for you, get PAIN",
      "%player% make excuses why you died)))",
      "%player% thanks for the game)))"
   };

   private static final String[] ES = {
      "%player% mejora, descarga PAIN client",
      "%player% llora mas))))",
      "%player% basura sin totems",
      "%player% por que moriste tan rapido eh?",
      "%player% llora",
      "%player% ups olvide apagar el killaura",
      "el unicelular %player% fue destruido",
      "%player% EZ jajajaja",
      "%player% me das pena, usa PAIN",
      "%player% pon excusas de por que moriste)))",
      "%player% gracias por la partida)))"
   };

   private static final String[] ZH = {
      "%player% 菜就多练，去下载 PAIN 客户端",
      "%player% 继续哭吧))))",
      "%player% 没有图腾的垃圾",
      "%player% 怎么死这么快啊?",
      "%player% 哭吧",
      "%player% 哎呀忘了关杀戮光环",
      "单细胞的 %player% 被打爆了",
      "%player% EZ 哈哈哈哈",
      "%player% 我为你感到难过，去用 PAIN",
      "%player% 给你找个死的借口吧)))",
      "%player% 谢谢你陪我玩)))"
   };

   private static final String[] UK = {
      "%player% качайся, постав PAIN клiент",
      "%player% плач далi))))",
      "%player% бiдота без тотемiв",
      "%player% чого так швидко здох га?",
      "%player% плач",
      "%player% упс забув вимкнути кiлку",
      "одноклiтинний %player% був знищений",
      "%player% IЗI ахахаха",
      "%player% менi тебе шкода, качай PAIN",
      "%player% виправдовуйся чого здох)))",
      "%player% дякую за гру)))"
   };

   private AutoEZ() {
   }

   private String[] phrases() {
      if (this.language.is("English")) {
         return EN;
      } else if (this.language.is("Espanol")) {
         return ES;
      } else if (this.language.is("Chinese")) {
         return ZH;
      } else {
         return this.language.is("Ukrainian") ? UK : RU;
      }
   }

   @EventTarget
   private void onUpdate(EventUpdate event) {
      if (mc.player != null && mc.world != null) {
         LivingEntity target = Aura.INSTANCE.getTarget();
         if (target != null && target != mc.player) {
            this.lastTarget = target;
         } else if (this.lastTarget != null) {
            boolean dead = !this.lastTarget.isAlive() || this.lastTarget.getHealth() <= 0.0F || this.lastTarget.isRemoved();
            if (dead && this.cooldown.finished(2000L)) {
               this.say(this.lastTarget.getName().getString());
               this.cooldown.reset();
            }

            this.lastTarget = null;
         }
      }
   }

   private void say(String name) {
      if (mc.getNetworkHandler() != null) {
         String[] pool = this.phrases();
         String phrase = pool[this.rng.nextInt(pool.length)].replace("%player%", name);
         mc.getNetworkHandler().sendChatMessage(this.global.isEnabled() ? "!" + phrase : phrase);
      }
   }
}
