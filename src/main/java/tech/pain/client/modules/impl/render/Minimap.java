package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.Heightmap;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender2D;
import tech.pain.base.font.Fonts;
import tech.pain.client.hud.HudStyle;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Мини-карта в углу экрана. Местность рисуется цветами настоящих блоков, как на карте из игры,
 * с тенями по высоте; под крышей и в Незере показывается «срез» на уровне игрока.
 * Поверх — игроки, мобы и предметы точками, стрелка игрока, стороны света и координаты.
 */
@ModuleAnnotation(
   name = "Minimap",
   category = Category.RENDER,
   description = "Мини-карта: рельеф цветами блоков, игроки и мобы точками, координаты"
)
public final class Minimap extends Module {
   public static final Minimap INSTANCE = new Minimap();
   private static final Identifier TEXTURE = Identifier.of("pain", "minimap");
   /** Сторона текстуры в блоках: карта снимается с запасом, чтобы не пересчитывать её на каждом шаге. */
   private static final int TEX = 160;
   private final ModeSetting corner = new ModeSetting("Угол", "Справа сверху", "Слева сверху", "Справа снизу", "Слева снизу");
   private final ModeSetting shape = new ModeSetting("Форма", "Круг", "Квадрат");
   private final NumberSetting size = new NumberSetting("Размер", 92.0F, 60.0F, 200.0F, 2.0F);
   private final NumberSetting range = new NumberSetting("Обзор (блоков от центра)", 44.0F, 16.0F, 64.0F, 2.0F);
   private final BooleanSetting rotate = new BooleanSetting("Вращать за взглядом", true);
   private final MultiBooleanSetting show = new MultiBooleanSetting("Показывать",
      MultiBooleanSetting.Value.of("Игроки", true), MultiBooleanSetting.Value.of("Враждебные мобы", true), MultiBooleanSetting.Value.of("Остальные существа", false),
      MultiBooleanSetting.Value.of("Предметы", false), MultiBooleanSetting.Value.of("Стороны света", true), MultiBooleanSetting.Value.of("Координаты", true));
   private final ModeSetting cave = new ModeSetting("Под землёй", "Авто", "Всегда поверхность", "Всегда срез");
   private NativeImageBackedTexture texture;
   private NativeImage image;
   private int centerX = Integer.MIN_VALUE;
   private int centerZ;
   private long lastScan;
   private boolean lastCave;
   private Object lastWorld;

   private Minimap() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.centerX = Integer.MIN_VALUE;
   }

   /** Верхний блок для карты: с неба — по карте высот, под крышей — первый твёрдый блок вниз от игрока. */
   private int surfaceY(int x, int z, boolean caveMode, int playerY, BlockPos.Mutable pos) {
      if (!caveMode) {
         return mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
      }

      int bottom = Math.max(mc.world.getBottomY(), playerY - 28);
      // сначала выходим из потолка над головой, потом ищем пол
      int y = playerY + 3;

      for (; y > bottom; --y) {
         if (mc.world.getBlockState(pos.set(x, y, z)).isAir()) {
            break;
         }
      }

      for (; y > bottom; --y) {
         if (!mc.world.getBlockState(pos.set(x, y, z)).isAir()) {
            return y;
         }
      }

      return Integer.MIN_VALUE;
   }

   private void scan(boolean caveMode) {
      if (this.image == null) {
         this.image = new NativeImage(TEX, TEX, false);
         this.texture = new NativeImageBackedTexture(this.image);
         mc.getTextureManager().registerTexture(TEXTURE, this.texture);
      }

      int px = MathHelper.floor(mc.player.getX());
      int pz = MathHelper.floor(mc.player.getZ());
      int py = MathHelper.floor(mc.player.getY());
      this.centerX = px;
      this.centerZ = pz;
      BlockPos.Mutable pos = new BlockPos.Mutable();
      int half = TEX / 2;
      int[] previousRow = new int[TEX];
      java.util.Arrays.fill(previousRow, Integer.MIN_VALUE);

      for (int iz = 0; iz < TEX; ++iz) {
         for (int ix = 0; ix < TEX; ++ix) {
            int x = px - half + ix;
            int z = pz - half + iz;
            int color = 0xFF0B0B0F;
            int y = Integer.MIN_VALUE;
            if (mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
               y = this.surfaceY(x, z, caveMode, py, pos);
               if (y != Integer.MIN_VALUE) {
                  BlockState state = mc.world.getBlockState(pos.set(x, y, z));
                  MapColor map = state.getMapColor(mc.world, pos);
                  int rgb = map == MapColor.CLEAR ? 0x101014 : map.color;
                  // тень по высоте, как на игровой карте: выше северного соседа — светлее, ниже — темнее
                  int north = previousRow[ix];
                  float shade = north == Integer.MIN_VALUE || north == y ? 0.86F : (y > north ? 1.0F : 0.7F);
                  if (!state.getFluidState().isEmpty()) {
                     shade = 0.9F;
                  }

                  if (caveMode) {
                     // в срезе всё, что ниже игрока, уходит в темноту — видно, где обрыв
                     shade *= MathHelper.clamp(1.0F - (float)(py - y - 1) * 0.045F, 0.35F, 1.0F);
                  }

                  int r = (int)((float)(rgb >> 16 & 255) * shade);
                  int g = (int)((float)(rgb >> 8 & 255) * shade);
                  int b = (int)((float)(rgb & 255) * shade);
                  color = 0xFF000000 | r << 16 | g << 8 | b;
               }
            }

            previousRow[ix] = y;
            this.image.setColorArgb(ix, iz, color);
         }
      }

      this.texture.upload();
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      if (mc.player == null || mc.world == null || mc.options.hudHidden) {
         return;
      }

      boolean caveMode = this.cave.is("Всегда срез") || this.cave.is("Авто") && (mc.world.getDimension().hasCeiling() || !mc.world.isSkyVisible(mc.player.getBlockPos().up()));
      int px = MathHelper.floor(mc.player.getX());
      int pz = MathHelper.floor(mc.player.getZ());
      float r = this.range.getCurrent();
      long now = System.currentTimeMillis();
      float margin = (float)TEX / 2.0F - r * 1.42F;
      boolean movedFar = Math.abs(px - this.centerX) > margin || Math.abs(pz - this.centerZ) > margin;
      // карту пересчитываем раз в полсекунды, при смене мира, режима или когда игрок ушёл к краю снятой области
      if (this.centerX == Integer.MIN_VALUE || movedFar || caveMode != this.lastCave || mc.world != this.lastWorld || now - this.lastScan > 500L) {
         this.lastScan = now;
         this.lastCave = caveMode;
         this.lastWorld = mc.world;
         this.scan(caveMode);
      }

      CustomDrawContext ctx = event.getContext();
      MatrixStack m = ctx.getMatrices();
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      float s = this.size.getCurrent();
      boolean coords = this.show.isEnable("Координаты");
      float pad = 10.0F;
      float x = this.corner.get().startsWith("Справа") ? sw - s - pad : pad;
      float y = this.corner.get().endsWith("сверху") ? pad + 26.0F : sh - s - pad - (coords ? 16.0F : 0.0F) - 30.0F;
      boolean circle = this.shape.is("Круг");
      BorderRadius radius = BorderRadius.all(circle ? s / 2.0F : 8.0F);
      float cx = x + s / 2.0F;
      float cy = y + s / 2.0F;
      // вращается только круглая карта: у квадратной при повороте углы вылезали бы за рамку
      boolean spin = this.rotate.isEnabled() && circle;
      float yaw = mc.gameRenderer.getCamera().getYaw();
      float angle = spin ? -(yaw + 180.0F) : 0.0F;
      HudStyle.glass(m, x - 3.0F, y - 3.0F, s + 6.0F, s + 6.0F, BorderRadius.all(circle ? (s + 6.0F) / 2.0F : 10.0F), 1.0F, true);
      // окно в снятой текстуре вокруг точного положения игрока — карта едет плавно, а не рывками по блоку
      double fx = mc.player.getX() - (double)this.centerX;
      double fz = mc.player.getZ() - (double)this.centerZ;
      float u = 0.5F + (float)fx / (float)TEX;
      float v = 0.5F + (float)fz / (float)TEX;
      float du = r / (float)TEX;
      m.push();
      m.translate(cx, cy, 0.0F);
      m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
      m.translate(-cx, -cy, 0.0F);
      DrawUtil.drawRoundedTextureWithUV(m, TEXTURE, x, y, s, s, radius, new ColorRGBA(255, 255, 255, 255), u - du, v - du, u + du, v + du);
      float scale = s / (r * 2.0F);
      float limit = s / 2.0F - 3.0F;

      for (Entity entity : mc.world.getEntities()) {
         if (entity == mc.player) {
            continue;
         }

         ColorRGBA color;
         float dot;
         if (entity instanceof PlayerEntity player) {
            if (!this.show.isEnable("Игроки")) {
               continue;
            }

            boolean friend = Pain.getInstance().getFriendManager().isFriend(player.getName().getString());
            color = friend ? new ColorRGBA(80, 230, 120) : new ColorRGBA(255, 70, 80);
            dot = 3.2F;
         } else if (entity instanceof HostileEntity) {
            if (!this.show.isEnable("Враждебные мобы")) {
               continue;
            }

            color = new ColorRGBA(255, 190, 60);
            dot = 2.4F;
         } else if (entity instanceof ItemEntity) {
            if (!this.show.isEnable("Предметы")) {
               continue;
            }

            color = new ColorRGBA(120, 200, 255);
            dot = 1.8F;
         } else if (entity instanceof LivingEntity) {
            if (!this.show.isEnable("Остальные существа")) {
               continue;
            }

            color = new ColorRGBA(220, 220, 220);
            dot = 2.0F;
         } else {
            continue;
         }

         float ex = (float)(MathHelper.lerp((double)event.getTickDelta(), entity.lastRenderX, entity.getX()) - mc.player.getX()) * scale;
         float ez = (float)(MathHelper.lerp((double)event.getTickDelta(), entity.lastRenderZ, entity.getZ()) - mc.player.getZ()) * scale;
         // за краем карты точку не рисуем: у круга — по радиусу, у квадрата — по сторонам
         if (circle ? ex * ex + ez * ez > limit * limit : Math.abs(ex) > limit || Math.abs(ez) > limit) {
            continue;
         }

         DrawUtil.drawRoundedRect(m, cx + ex - dot / 2.0F - 0.6F, cy + ez - dot / 2.0F - 0.6F, dot + 1.2F, dot + 1.2F, BorderRadius.all((dot + 1.2F) / 2.0F), new ColorRGBA(0, 0, 0, 170));
         DrawUtil.drawRoundedRect(m, cx + ex - dot / 2.0F, cy + ez - dot / 2.0F, dot, dot, BorderRadius.all(dot / 2.0F), color);
      }

      m.pop();
      // рамка и стрелка игрока поверх карты
      DrawUtil.drawRoundedBorder(m, x, y, s, s, -0.5F, radius, new ColorRGBA(255, 255, 255, 70));
      m.push();
      m.translate(cx, cy, 0.0F);
      m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin ? 0.0F : yaw + 180.0F));
      // стрелка — ромб, вытянутый вперёд: два скруглённых прямоугольника разного размера
      DrawUtil.drawRoundedRect(m, -2.6F, -2.6F, 5.2F, 5.2F, BorderRadius.all(2.6F), new ColorRGBA(0, 0, 0, 190));
      DrawUtil.drawRoundedRect(m, -1.9F, -1.9F, 3.8F, 3.8F, BorderRadius.all(1.9F), new ColorRGBA(255, 255, 255, 255));
      DrawUtil.drawRoundedRect(m, -0.8F, -5.6F, 1.6F, 4.0F, BorderRadius.all(0.8F), new ColorRGBA(255, 255, 255, 255));
      m.pop();
      if (this.show.isEnable("Стороны света")) {
         String[] names = new String[]{"С", "В", "Ю", "З"};

         for (int i = 0; i < 4; ++i) {
            // север на карте вверху (−Z); при вращении буквы едут по краю вместе с картой
            double a = Math.toRadians((double)(i * 90) + (double)angle - 90.0);
            float edge = s / 2.0F - 7.0F;
            float lx = cx + (float)Math.cos(a) * edge;
            float ly = cy + (float)Math.sin(a) * edge;
            float w = Fonts.MEDIUM.getWidth(names[i], 6.5F);
            DrawUtil.drawRoundedRect(m, lx - w / 2.0F - 2.0F, ly - 4.5F, w + 4.0F, 9.0F, BorderRadius.all(4.5F), new ColorRGBA(0, 0, 0, 120));
            ctx.drawText(Fonts.MEDIUM.getFont(6.5F), names[i], lx - w / 2.0F, ly - 3.2F, i == 0 ? new ColorRGBA(255, 90, 100) : new ColorRGBA(240, 240, 245));
         }
      }

      if (coords) {
         String text = MathHelper.floor(mc.player.getX()) + "  " + MathHelper.floor(mc.player.getY()) + "  " + MathHelper.floor(mc.player.getZ());
         float w = Fonts.MEDIUM.getWidth(text, 7.0F) + 14.0F;
         float tx = cx - w / 2.0F;
         float ty = y + s + 6.0F;
         HudStyle.glass(m, tx, ty, w, 14.0F, BorderRadius.all(7.0F), 1.0F, true);
         ctx.drawText(Fonts.MEDIUM.getFont(7.0F), text, tx + 7.0F, ty + 3.6F, new ColorRGBA(245, 245, 250));
      }

      ctx.draw();
   }
}
