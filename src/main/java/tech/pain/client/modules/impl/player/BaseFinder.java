package tech.pain.client.modules.impl.player;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.darkmagician6.eventapi.EventTarget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.render.StorageESP;

/**
 * Поиск баз: Baritone копает прямой туннель туда, куда ты смотришь, а модуль следит за хранилищами
 * в прогруженных чанках. Когда рядом набирается подозрительно много сундуков, шалкеров и печей —
 * звучит сигнал, координаты пишутся в уведомление, в чат (только тебе) и в файл Pain/bases.txt.
 */
@ModuleAnnotation(
   name = "BaseFinder",
   category = Category.PLAYER,
   description = "Копает туннель Baritone'ом и сигналит звуком, когда рядом находит базу"
)
public final class BaseFinder extends Module {
   public static final BaseFinder INSTANCE = new BaseFinder();
   private final BooleanSetting tunnel = new BooleanSetting("Копать туннель (Baritone)", true);
   private final NumberSetting radius = new NumberSetting("Радиус поиска (чанки)", 6.0F, 2.0F, 12.0F, 1.0F);
   private final NumberSetting threshold = new NumberSetting("Сколько хранилищ считать базой", 8.0F, 3.0F, 40.0F, 1.0F);
   private final BooleanSetting stopOnFind = new BooleanSetting("Останавливаться при находке", true);
   private final BooleanSetting slowHead = new BooleanSetting("Медленный поворот головы", true);
   private final NumberSetting headTicks = new NumberSetting("Плавность поворота (тики)", 12.0F, 3.0F, 40.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.slowHead.isEnabled());
   private final NumberSetting stopHealth = new NumberSetting("Стоп при здоровье ниже", 8.0F, 0.0F, 20.0F, 1.0F);
   private final List<BlockPos> reported = new ArrayList<>();
   private boolean savedSmooth;
   private int savedTicks;
   private boolean settingsSaved;
   private boolean walking;
   private int tick;
   private int alarm;

   private BaseFinder() {
   }

   private void say(String text) {
      Pain.getInstance().getNotifyManager().addNotification("", Text.of("BaseFinder: " + text));
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.reported.clear();
      this.tick = 0;
      this.alarm = 0;
      this.walking = false;
      if (mc.player == null) {
         return;
      }

      if (!this.settingsSaved) {
         this.savedSmooth = BaritoneAPI.getSettings().smoothLook.value;
         this.savedTicks = BaritoneAPI.getSettings().smoothLookTicks.value;
         this.settingsSaved = true;
      }

      this.applyHead();
      if (this.tunnel.isEnabled()) {
         this.startTunnel();
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.stopTunnel();
      if (this.settingsSaved) {
         BaritoneAPI.getSettings().smoothLook.value = this.savedSmooth;
         BaritoneAPI.getSettings().smoothLookTicks.value = this.savedTicks;
         this.settingsSaved = false;
      }
   }

   /** Плавный поворот головы — настройка самого Baritone: камера доворачивается за несколько тиков, а не рывком. */
   private void applyHead() {
      BaritoneAPI.getSettings().smoothLook.value = this.slowHead.isEnabled();
      BaritoneAPI.getSettings().smoothLookTicks.value = (int)this.headTicks.getCurrent();
   }

   private void startTunnel() {
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      // «tunnel» без аргументов — прямой туннель 1×2 в сторону взгляда, пока не остановят
      baritone.getCommandManager().execute("tunnel");
      this.walking = true;
   }

   private void stopTunnel() {
      if (this.walking) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
         this.walking = false;
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      // сигнал — несколько ударов колокола подряд, чтобы точно услышать
      if (this.alarm > 0 && this.tick % 6 == 0) {
         --this.alarm;
         mc.player.playSound(SoundEvents.BLOCK_BELL_USE, 2.0F, 1.0F);
         mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
      }

      ++this.tick;
      if (this.tick % 20 != 0) {
         return;
      }

      this.applyHead();
      if (this.walking && this.stopHealth.getCurrent() > 0.0F && mc.player.getHealth() < this.stopHealth.getCurrent()) {
         this.stopTunnel();
         this.say("мало здоровья — туннель остановлен");
         mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 2.0F, 0.6F);
      }

      // если Baritone сам встал (упёрся, упал в пещеру) — запускаем туннель снова
      if (this.walking && !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && this.tick % 100 == 0) {
         this.startTunnel();
      }

      this.detect();
   }

   /**
    * База — это много хранилищ рядом. Считаем их по участкам 48×48 блоков; спавнеры не считаем
    * (это данжи), одиночные сундуки шахт и данжей до порога не дотягивают.
    */
   private void detect() {
      Map<Long, List<StorageESP.Found>> cells = new HashMap<>();

      for (StorageESP.Found f : StorageESP.scan((int)this.radius.getCurrent())) {
         if (f.type().equals("Спавнеры")) {
            continue;
         }

         long key = (long)Math.floorDiv(f.pos().getX(), 48) << 32 ^ (long)Math.floorDiv(f.pos().getZ(), 48) & 0xFFFFFFFFL;
         cells.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
      }

      for (List<StorageESP.Found> cell : cells.values()) {
         int score = 0;

         for (StorageESP.Found f : cell) {
            // шалкеры и эндер-сундуки в природе не встречаются — они весят больше
            score += f.type().equals("Шалкеры") ? 4 : (f.type().equals("Эндер-сундуки") ? 3 : 1);
         }

         if ((float)score < this.threshold.getCurrent()) {
            continue;
         }

         BlockPos at = cell.get(0).pos();
         boolean known = false;

         for (BlockPos old : this.reported) {
            if (old.getSquaredDistance(at) < 96.0 * 96.0) {
               known = true;
               break;
            }
         }

         if (known) {
            continue;
         }

         this.reported.add(at);
         this.found(at, cell.size(), score);
      }
   }

   private void found(BlockPos at, int storages, int score) {
      String where = at.getX() + " " + at.getY() + " " + at.getZ();
      this.alarm = 5;
      this.say("база рядом: " + where + " (хранилищ: " + storages + ")");
      mc.inGameHud.getChatHud().addMessage(Text.of("§c[BaseFinder] §fБаза: §e" + where + " §7· хранилищ " + storages + ", до неё " + (int)Math.sqrt(mc.player.getBlockPos().getSquaredDistance(at)) + " блоков"));

      try {
         Path file = mc.runDirectory.toPath().resolve("Pain").resolve("bases.txt");
         Files.createDirectories(file.getParent());
         String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "одиночный мир";
         String line = java.time.LocalDateTime.now().withNano(0) + " | " + server + " | " + mc.world.getRegistryKey().getValue() + " | " + where + " | хранилищ " + storages + " (вес " + score + ")\n";
         Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception e) {
         e.printStackTrace();
      }

      if (this.stopOnFind.isEnabled()) {
         this.stopTunnel();
      }
   }
}
