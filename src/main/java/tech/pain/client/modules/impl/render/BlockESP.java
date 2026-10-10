package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.api.setting.impl.StringSetting;
import tech.pain.utility.render.level.Render3DUtil;

/**
 * Подсветка выбранных блоков сквозь стены: руды, древние обломки, спавнеры, порталы и свои блоки по id.
 * Мир вокруг просматривается в фоне раз в пару секунд, поэтому на FPS поиск не влияет.
 */
@ModuleAnnotation(
   name = "BlockESP",
   category = Category.RENDER,
   description = "Подсвечивает выбранные блоки сквозь стены: руды, обломки, спавнеры, порталы, свои блоки"
)
public final class BlockESP extends Module {
   public static final BlockESP INSTANCE = new BlockESP();
   private static final int MAX = 2500;
   private final MultiBooleanSetting groups = new MultiBooleanSetting("Блоки",
      MultiBooleanSetting.Value.of("Алмазы", true), MultiBooleanSetting.Value.of("Древние обломки", true), MultiBooleanSetting.Value.of("Изумруды", false),
      MultiBooleanSetting.Value.of("Золото", false), MultiBooleanSetting.Value.of("Железо", false), MultiBooleanSetting.Value.of("Редстоун и лазурит", false),
      MultiBooleanSetting.Value.of("Спавнеры", true), MultiBooleanSetting.Value.of("Порталы", false), MultiBooleanSetting.Value.of("Кровати", false));
   private final StringSetting custom = new StringSetting("Свои блоки (id через запятую)", "", 120);
   private final NumberSetting radius = new NumberSetting("Радиус (чанки)", 4.0F, 1.0F, 10.0F, 1.0F);
   private final BooleanSetting fill = new BooleanSetting("Заливка", true);
   private volatile List<Found> found = List.of();
   private volatile boolean scanning;
   private long lastScan;

   private BlockESP() {
   }

   private record Found(BlockPos pos, int color) {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.found = List.of();
   }

   /** Какие блоки ищем и каким цветом их рисовать. */
   private Map<Block, Integer> targets() {
      Map<Block, Integer> map = new HashMap<>();
      if (this.groups.isEnable("Алмазы")) {
         map.put(Blocks.DIAMOND_ORE, 0xFF4FE3E8);
         map.put(Blocks.DEEPSLATE_DIAMOND_ORE, 0xFF4FE3E8);
      }

      if (this.groups.isEnable("Древние обломки")) {
         map.put(Blocks.ANCIENT_DEBRIS, 0xFFB5652F);
      }

      if (this.groups.isEnable("Изумруды")) {
         map.put(Blocks.EMERALD_ORE, 0xFF32D95B);
         map.put(Blocks.DEEPSLATE_EMERALD_ORE, 0xFF32D95B);
      }

      if (this.groups.isEnable("Золото")) {
         map.put(Blocks.GOLD_ORE, 0xFFFFD23C);
         map.put(Blocks.DEEPSLATE_GOLD_ORE, 0xFFFFD23C);
         map.put(Blocks.NETHER_GOLD_ORE, 0xFFFFD23C);
      }

      if (this.groups.isEnable("Железо")) {
         map.put(Blocks.IRON_ORE, 0xFFD8A98A);
         map.put(Blocks.DEEPSLATE_IRON_ORE, 0xFFD8A98A);
      }

      if (this.groups.isEnable("Редстоун и лазурит")) {
         map.put(Blocks.REDSTONE_ORE, 0xFFFF3B3B);
         map.put(Blocks.DEEPSLATE_REDSTONE_ORE, 0xFFFF3B3B);
         map.put(Blocks.LAPIS_ORE, 0xFF3D6BFF);
         map.put(Blocks.DEEPSLATE_LAPIS_ORE, 0xFF3D6BFF);
      }

      if (this.groups.isEnable("Спавнеры")) {
         map.put(Blocks.SPAWNER, 0xFFB44BFF);
         map.put(Blocks.TRIAL_SPAWNER, 0xFFB44BFF);
      }

      if (this.groups.isEnable("Порталы")) {
         map.put(Blocks.NETHER_PORTAL, 0xFFA020F0);
         map.put(Blocks.END_PORTAL, 0xFF30C0A0);
         map.put(Blocks.END_PORTAL_FRAME, 0xFF30C0A0);
         map.put(Blocks.END_GATEWAY, 0xFF30C0A0);
      }

      if (this.groups.isEnable("Кровати")) {
         for (Block block : Registries.BLOCK) {
            if (block instanceof net.minecraft.block.BedBlock) {
               map.put(block, 0xFFFF6FA8);
            }
         }
      }

      for (String raw : this.custom.get().split("[,; ]+")) {
         String id = raw.trim().toLowerCase();
         if (id.isEmpty()) {
            continue;
         }

         Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
         if (identifier != null && Registries.BLOCK.containsId(identifier)) {
            map.put(Registries.BLOCK.get(identifier), 0xFFFFFFFF);
         }
      }

      return map;
   }

   private void scan() {
      Map<Block, Integer> targets = this.targets();
      if (targets.isEmpty() || mc.world == null || mc.player == null) {
         this.found = List.of();
         return;
      }

      // список чанков берём в основном потоке, сами блоки читаем в фоне
      List<WorldChunk> chunks = new ArrayList<>();
      int r = (int)this.radius.getCurrent();
      int pcx = mc.player.getChunkPos().x;
      int pcz = mc.player.getChunkPos().z;

      for (int cx = pcx - r; cx <= pcx + r; ++cx) {
         for (int cz = pcz - r; cz <= pcz + r; ++cz) {
            WorldChunk chunk = (WorldChunk)mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk != null) {
               chunks.add(chunk);
            }
         }
      }

      BlockPos center = mc.player.getBlockPos();
      this.scanning = true;
      Thread thread = new Thread(() -> {
         try {
            List<Found> result = new ArrayList<>();

            for (WorldChunk chunk : chunks) {
               ChunkSection[] sections = chunk.getSectionArray();
               int baseX = chunk.getPos().getStartX();
               int baseZ = chunk.getPos().getStartZ();

               for (int i = 0; i < sections.length; ++i) {
                  ChunkSection section = sections[i];
                  // секции без нужных блоков пропускаем целиком — это почти все
                  if (section == null || section.isEmpty() || !section.hasAny(state -> targets.containsKey(state.getBlock()))) {
                     continue;
                  }

                  int baseY = (chunk.getBottomSectionCoord() + i) << 4;

                  for (int y = 0; y < 16; ++y) {
                     for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                           BlockState state = section.getBlockState(x, y, z);
                           Integer color = targets.get(state.getBlock());
                           if (color != null) {
                              result.add(new Found(new BlockPos(baseX + x, baseY + y, baseZ + z), color));
                           }
                        }
                     }
                  }
               }
            }

            if (result.size() > MAX) {
               // слишком много — оставляем ближайшие
               result.sort(Comparator.comparingDouble(f -> f.pos.getSquaredDistance(center)));
               result = new ArrayList<>(result.subList(0, MAX));
            }

            this.found = result;
         } catch (Throwable ignored) {
            // чанк выгрузился посреди чтения — попробуем в следующий раз
         } finally {
            this.scanning = false;
         }
      }, "Pain-BlockESP");
      thread.setDaemon(true);
      thread.start();
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      if (!this.scanning && now - this.lastScan > 2000L) {
         this.lastScan = now;
         this.scan();
      }

      boolean filled = this.fill.isEnabled();

      for (Found f : this.found) {
         Render3DUtil.drawBox(new Box(f.pos), f.color, 1.0F, true, filled, false);
      }
   }
}
