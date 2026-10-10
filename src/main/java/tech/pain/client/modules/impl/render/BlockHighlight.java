package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.level.ParticleShapes;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import tech.pain.base.events.impl.render.EventRender3D;

@ModuleAnnotation(
   name = "BlockHighlight",
   category = Category.RENDER,
   description = "Плавная цветная подсветка блока под прицелом"
)
public final class BlockHighlight extends Module {
   public static final BlockHighlight INSTANCE = new BlockHighlight();
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 60, 80));
   private final NumberSetting fill = new NumberSetting("Заливка", 0.22F, 0.0F, 0.8F, 0.02F);
   private final NumberSetting line = new NumberSetting("Толщина рамки", 0.02F, 0.005F, 0.06F, 0.005F);
   private final BooleanSetting pulse = new BooleanSetting("Пульсация", true);
   private final BooleanSetting smooth = new BooleanSetting("Плавный переход", true);
   private Box shown;
   private long lastFrame;

   private BlockHighlight() {
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
         this.shown = null;
         return;
      }

      BlockPos pos = hit.getBlockPos();
      VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
      if (shape.isEmpty()) {
         this.shown = null;
         return;
      }

      Box target = shape.getBoundingBox().offset(pos).expand(0.003);
      if (this.shown == null || !this.smooth.isEnabled()) {
         this.shown = target;
      } else {
         // рамка перетекает на новый блок, а не прыгает
         double k = 1.0 - Math.exp((double)(-dt) * 18.0);
         this.shown = new Box(
            MathHelper.lerp(k, this.shown.minX, target.minX), MathHelper.lerp(k, this.shown.minY, target.minY), MathHelper.lerp(k, this.shown.minZ, target.minZ),
            MathHelper.lerp(k, this.shown.maxX, target.maxX), MathHelper.lerp(k, this.shown.maxY, target.maxY), MathHelper.lerp(k, this.shown.maxZ, target.maxZ)
         );
      }

      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      float x0 = (float)(this.shown.minX - cam.x);
      float y0 = (float)(this.shown.minY - cam.y);
      float z0 = (float)(this.shown.minZ - cam.z);
      float x1 = (float)(this.shown.maxX - cam.x);
      float y1 = (float)(this.shown.maxY - cam.y);
      float z1 = (float)(this.shown.maxZ - cam.z);
      float beat = this.pulse.isEnabled() ? 0.65F + 0.35F * (float)Math.sin((double)now / 260.0) : 1.0F;
      int rgb = this.color.getColor().getRGB() & 0xFFFFFF;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      int f = ParticleShapes.withAlpha(rgb, this.fill.getCurrent() * beat);
      if (this.fill.getCurrent() > 0.0F) {
         box(bb, mat, x0, y0, z0, x1, y1, z1, f);
      }

      // рамка: двенадцать рёбер тонкими брусками
      float w = this.line.getCurrent();
      int l = ParticleShapes.withAlpha(rgb, 0.95F);

      for (int i = 0; i < 2; ++i) {
         for (int j = 0; j < 2; ++j) {
            float ya = i == 0 ? y0 : y1;
            float za = j == 0 ? z0 : z1;
            float xa = i == 0 ? x0 : x1;
            box(bb, mat, x0 - w, ya - w, za - w, x1 + w, ya + w, za + w, l);
            box(bb, mat, xa - w, y0 - w, za - w, xa + w, y1 + w, za + w, l);
            float yb = j == 0 ? y0 : y1;
            box(bb, mat, xa - w, yb - w, z0 - w, xa + w, yb + w, z1 + w, l);
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   private static void box(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
      face(bb, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c);
      face(bb, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c);
      face(bb, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c);
      face(bb, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c);
      face(bb, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c);
      face(bb, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, c);
   }

   private static void face(BufferBuilder bb, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int c) {
      bb.vertex(m, ax, ay, az).color(c);
      bb.vertex(m, bx, by, bz).color(c);
      bb.vertex(m, cx, cy, cz).color(c);
      bb.vertex(m, ax, ay, az).color(c);
      bb.vertex(m, cx, cy, cz).color(c);
      bb.vertex(m, dx, dy, dz).color(c);
   }
}
