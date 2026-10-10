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

import net.minecraft.entity.player.PlayerEntity;
import tech.pain.base.events.impl.render.EventRender3D;

@ModuleAnnotation(
   name = "ChinaHat",
   category = Category.RENDER,
   description = "Светящаяся коническая шляпа над головой"
)
public final class ChinaHat extends Module {
   public static final ChinaHat INSTANCE = new ChinaHat();
   private final BooleanSetting others = new BooleanSetting("На других игроках", false);
   private final BooleanSetting rainbow = new BooleanSetting("Радуга", true);
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 60, 80), () -> !this.rainbow.isEnabled());
   private final NumberSetting radius = new NumberSetting("Радиус", 0.65F, 0.4F, 1.0F, 0.05F);
   private final NumberSetting tall = new NumberSetting("Высота", 0.3F, 0.1F, 0.6F, 0.05F);
   private final NumberSetting opacity = new NumberSetting("Прозрачность", 0.55F, 0.1F, 1.0F, 0.05F);

   private ChinaHat() {
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      float pt = event.getPartialTicks();
      float spin = (float)(System.currentTimeMillis() % 6000L) / 6000.0F;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (PlayerEntity p : mc.world.getPlayers()) {
         if (p == mc.player ? mc.options.getPerspective().isFirstPerson() : !this.others.isEnabled()) {
            continue;
         }

         if (p.isInvisible() || p.isSpectator()) {
            continue;
         }

         float x = (float)(MathHelper.lerp((double)pt, p.lastRenderX, p.getX()) - cam.x);
         float y = (float)(MathHelper.lerp((double)pt, p.lastRenderY, p.getY()) - cam.y) + p.getHeight() + 0.08F;
         float z = (float)(MathHelper.lerp((double)pt, p.lastRenderZ, p.getZ()) - cam.z);
         float r = this.radius.getCurrent();
         float h = this.tall.getCurrent();
         int steps = 48;

         for (int i = 0; i < steps; ++i) {
            float a0 = (float)i / (float)steps;
            float a1 = (float)(i + 1) / (float)steps;
            int c0 = this.rim(a0 + spin);
            int c1 = this.rim(a1 + spin);
            float x0 = x + MathHelper.cos(a0 * 6.2831855F) * r;
            float z0 = z + MathHelper.sin(a0 * 6.2831855F) * r;
            float x1 = x + MathHelper.cos(a1 * 6.2831855F) * r;
            float z1 = z + MathHelper.sin(a1 * 6.2831855F) * r;
            float o = this.opacity.getCurrent();
            // вершина светлее, край насыщенный
            bb.vertex(mat, x, y + h, z).color(ParticleShapes.withAlpha(0xFFFFFF, o * 0.75F));
            bb.vertex(mat, x0, y, z0).color(ParticleShapes.withAlpha(c0, o));
            bb.vertex(mat, x1, y, z1).color(ParticleShapes.withAlpha(c1, o));
            // светящийся ободок
            float k = 1.12F;
            float ox0 = x + (x0 - x) * k;
            float oz0 = z + (z0 - z) * k;
            float ox1 = x + (x1 - x) * k;
            float oz1 = z + (z1 - z) * k;
            bb.vertex(mat, x0, y, z0).color(ParticleShapes.withAlpha(c0, o));
            bb.vertex(mat, x1, y, z1).color(ParticleShapes.withAlpha(c1, o));
            bb.vertex(mat, ox1, y - 0.03F, oz1).color(ParticleShapes.withAlpha(c1, 0.0F));
            bb.vertex(mat, x0, y, z0).color(ParticleShapes.withAlpha(c0, o));
            bb.vertex(mat, ox1, y - 0.03F, oz1).color(ParticleShapes.withAlpha(c1, 0.0F));
            bb.vertex(mat, ox0, y - 0.03F, oz0).color(ParticleShapes.withAlpha(c0, 0.0F));
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

   private int rim(float hue) {
      return this.rainbow.isEnabled() ? java.awt.Color.HSBtoRGB(hue, 0.65F, 1.0F) & 0xFFFFFF : this.color.getColor().getRGB() & 0xFFFFFF;
   }
}
