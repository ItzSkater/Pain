package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.text.Text;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Пишет в чат строку, которая сейчас звучит в песне (берётся из MusicLyrics).
 * Local — как есть, Global — с «!» в начале, Whisper — личным сообщением через /msg.
 */
@ModuleAnnotation(
   name = "ChatLyrics",
   category = Category.MISC,
   description = "Пишет строки играющей песни в чат: Local, Global (!) или Whisper (/msg)"
)
public final class ChatLyrics extends Module {
   public static final ChatLyrics INSTANCE = new ChatLyrics();
   private final ModeSetting mode = new ModeSetting("Режим", "Local", "Global", "Whisper");
   private final tech.pain.client.modules.api.setting.impl.StringSetting nickSetting = new tech.pain.client.modules.api.setting.impl.StringSetting("Ник для шёпота", "", () -> this.mode.is("Whisper"));
   private final NumberSetting delay = new NumberSetting("Мин. пауза (сек)", 3.0F, 1.0F, 15.0F, 0.5F);
   private final BooleanSetting announce = new BooleanSetting("Сначала название песни", true);
   private String lastTrack = "";
   private final BooleanSetting translated = new BooleanSetting("Отправлять перевод", false);
   private final BooleanSetting onlySynced = new BooleanSetting("Только точный текст (LRC)", true);
   private String lastLine = "";
   private long lastSent;
   private boolean warned;

   private ChatLyrics() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.lastLine = "";
      this.lastTrack = "";
      this.warned = false;
      if (!MusicLyrics.INSTANCE.isEnabled()) {
         this.notify("включи MusicLyrics — строки берутся оттуда");
      }
   }

   /** Ник для режима Whisper: поле в настройках модуля или команда ^chatlyrics nick. */
   public String getNick() {
      return this.nickSetting.get().trim();
   }

   public void setNick(String value) {
      this.nickSetting.set(value == null ? "" : value.trim());
      this.warned = false;
   }

   private void notify(String text) {
      Pain.getInstance().getNotifyManager().addNotification("", Text.of("ChatLyrics: " + text));
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.getNetworkHandler() == null) {
         return;
      }

      MusicLyrics lyrics = MusicLyrics.INSTANCE;
      if (!lyrics.isEnabled()) {
         return;
      }

      // распознавание со звука часто ошибается — по умолчанию в чат идёт только точный текст из базы
      if (this.onlySynced.isEnabled() && !tech.pain.base.music.LyricsService.hasSynced()) {
         return;
      }

      long now = System.currentTimeMillis();
      // новая песня: сначала объявляем название, строки пойдут следующими сообщениями
      String track = tech.pain.base.music.LyricsService.getTrack();
      track = track == null ? "" : track.replaceAll("[\\n\\r§]", " ").trim();
      if (this.announce.isEnabled() && !track.isEmpty() && !track.equals(this.lastTrack)) {
         if ((float)(now - this.lastSent) < this.delay.getCurrent() * 1000.0F) {
            return;
         }

         if (this.send("♪ " + track)) {
            this.lastTrack = track;
            this.lastLine = "";
            this.lastSent = now;
         }

         return;
      }

      String[] lines = lyrics.displayLines();
      if (lines == null) {
         return;
      }

      String line = this.translated.isEnabled() && lines[1] != null ? lines[1] : lines[0];
      line = line == null ? "" : line.replaceAll("[\\n\\r§]", " ").trim();
      if (line.isEmpty() || line.equals(this.lastLine)) {
         return;
      }

      if ((float)(now - this.lastSent) < this.delay.getCurrent() * 1000.0F) {
         return;
      }

      if (this.send(line)) {
         this.lastLine = line;
         this.lastSent = now;
      }
   }

   /** Отправляет текст в выбранном режиме. false — отправить не удалось (например, не задан ник). */
   private boolean send(String text) {
      if (this.mode.is("Whisper")) {
         String target = this.getNick();
         if (target.isEmpty()) {
            if (!this.warned) {
               this.warned = true;
               this.notify("впиши ник в настройках модуля (поле «Ник для шёпота»)");
            }

            return false;
         }

         mc.getNetworkHandler().sendChatCommand(cut("msg " + target + " " + text));
      } else if (this.mode.is("Global")) {
         mc.getNetworkHandler().sendChatMessage(cut("!" + text));
      } else {
         // строка, начинающаяся с «/», ушла бы как команда — такое в Local не отправляем
         if (text.startsWith("/")) {
            return false;
         }

         mc.getNetworkHandler().sendChatMessage(cut(text));
      }

      return true;
   }

   /** Сервер не принимает сообщения длиннее 256 символов. */
   private static String cut(String text) {
      return text.length() > 250 ? text.substring(0, 250) : text;
   }
}
