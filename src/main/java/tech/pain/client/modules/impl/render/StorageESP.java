package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.Render3DUtil;

/**
 * Подсветка хранилищ сквозь стены: сундуки, бочки, шалкеры, эндер-сундуки, воронки, печи, раздатчики, спавнеры.
 * Каждый тип — своим цветом.
 */
@ModuleAnnotation(
   name = "StorageESP",
   category = Category.RENDER,
   description = "Подсвечивает сундуки, бочки, шалкеры и другие хранилища сквозь стены"
)
public final class StorageESP extends Module {
   public static final StorageESP INSTANCE = new StorageESP();
   private final MultiBooleanSetting types = new MultiBooleanSetting("Что показывать",
      MultiBooleanSetting.Value.of("Сундуки", true), MultiBooleanSetting.Value.of("Сундуки-ловушки", true), MultiBooleanSetting.Value.of("Эндер-сундуки", true),
      MultiBooleanSetting.Value.of("Бочки", true), MultiBooleanSetting.Value.of("Шалкеры", true), MultiBooleanSetting.Value.of("Воронки", false),
      MultiBooleanSetting.Value.of("Печи", false), MultiBooleanSetting.Value.of("Раздатчики", false), MultiBooleanSetting.Value.of("Спавнеры", true));
   private final NumberSetting radius = new NumberSetting("Радиус (чанки)", 6.0F, 1.0F, 16.0F, 1.0F);
   private final BooleanSetting fill = new BooleanSetting("Заливка", true);
   private List<Found> found = new ArrayList<>();
   private long lastScan;

   private StorageESP() {
   }

   public record Found(BlockPos pos, int color, String type) {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.found = new ArrayList<>();
   }

   /** Название типа хранилища или null, если это не хранилище. */
   public static String typeOf(BlockEntity entity) {
      if (entity instanceof TrappedChestBlockEntity) {
         return "Сундуки-ловушки";
      } else if (entity instanceof ChestBlockEntity) {
         return "Сундуки";
      } else if (entity instanceof EnderChestBlockEntity) {
         return "Эндер-сундуки";
      } else if (entity instanceof BarrelBlockEntity) {
         return "Бочки";
      } else if (entity instanceof ShulkerBoxBlockEntity) {
         return "Шалкеры";
      } else if (entity instanceof HopperBlockEntity) {
         return "Воронки";
      } else if (entity instanceof AbstractFurnaceBlockEntity) {
         return "Печи";
      } else if (entity instanceof DispenserBlockEntity) {
         return "Раздатчики";
      } else {
         return entity instanceof MobSpawnerBlockEntity ? "Спавнеры" : null;
      }
   }

   private static int colorOf(String type) {
      return switch (type) {
         case "Сундуки" -> 0xFFFFA63C;
         case "Сундуки-ловушки" -> 0xFFFF4B4B;
         case "Эндер-сундуки" -> 0xFFB44BFF;
         case "Бочки" -> 0xFFC98C4A;
         case "Шалкеры" -> 0xFFFF6FD0;
         case "Воронки" -> 0xFF9AA0A8;
         case "Печи" -> 0xFFC8C8C8;
         case "Раздатчики" -> 0xFF8FB0C8;
         default -> 0xFF46E08A;
      };
   }

   /** Все хранилища в прогруженных чанках вокруг игрока. */
   public static List<Found> scan(int chunkRadius) {
      List<Found> result = new ArrayList<>();
      if (mc.world == null || mc.player == null) {
         return result;
      }

      int pcx = mc.player.getChunkPos().x;
      int pcz = mc.player.getChunkPos().z;

      for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; ++cx) {
         for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; ++cz) {
            WorldChunk chunk = (WorldChunk)mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
               continue;
            }

            for (BlockEntity entity : chunk.getBlockEntities().values()) {
               String type = typeOf(entity);
               if (type != null) {
                  result.add(new Found(entity.getPos(), colorOf(type), type));
               }
            }
         }
      }

      return result;
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      if (now - this.lastScan > 700L) {
         this.lastScan = now;
         List<Found> all = scan((int)this.radius.getCurrent());
         all.removeIf(f -> !this.types.isEnable(f.type));
         this.found = all;
      }

      boolean filled = this.fill.isEnabled();

      for (Found f : this.found) {
         // сундук уже блока, рамку делаем по его размеру
         boolean chest = f.type.startsWith("Сундуки") || f.type.equals("Эндер-сундуки");
         Box box = chest ? new Box(f.pos).contract(0.0625, 0.0, 0.0625).withMaxY((double)f.pos.getY() + 0.875) : new Box(f.pos);
         Render3DUtil.drawBox(box, f.color, 1.2F, true, filled, false);
      }
   }
}
