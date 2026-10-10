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
   name = "Halo",
   category = Category.RENDER,
   description = "Светящиеся кольца, бегущие вверх-вниз по игроку"
)
public final class Halo extends Module {
   public static final Halo INSTANCE = new Halo();
   private final BooleanSetting others = new BooleanSetting("На других игроках", false);
   private final BooleanSetting rainbow = new BooleanSetting("Радуга", false);
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(120, 200, 255), () -> !this.rainbow.isEnabled());
   private final NumberSetting rings = new NumberSetting("Колец", 2.0F, 1.0F, 4.0F, 1.0F);
   private final NumberSetting speed = new NumberSetting("Скорость", 1.0F, 0.2F, 3.0F, 0.1F);
   private final NumberSetting radius = new NumberSetting("Радиус", 0.65F, 0.4F, 1.2F, 0.05F);

   private Halo() {
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      float pt = event.getPartialTicks();
      double time = (double)(System.currentTimeMillis() % 100000L) / 1000.0 * (double)this.speed.getCurrent();
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
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
         float y = (float)(MathHelper.lerp((double)pt, p.lastRenderY, p.getY()) - cam.y);
         float z = (float)(MathHelper.lerp((double)pt, p.lastRenderZ, p.getZ()) - cam.z);
         int count = (int)this.rings.getCurrent();

         for (int n = 0; n < count; ++n) {
            double phase = time * 2.0 + (double)n * Math.PI * 2.0 / (double)count;
            float wave = (float)Math.sin(phase);
            // хвост тянется в сторону, откуда кольцо пришло
            float dir = (float)Math.cos(phase) > 0.0F ? -1.0F : 1.0F;
            float ry = y + p.getHeight() * (0.5F + 0.5F * wave);
            float tail = 0.45F * (float)Math.abs(Math.cos(phase));
            float r = this.radius.getCurrent();
            int steps = 56;

            for (int i = 0; i < steps; ++i) {
               float a0 = (float)i / (float)steps;
               float a1 = (float)(i + 1) / (float)steps;
               int c0 = this.tint(a0 + (float)time * 0.1F);
               int c1 = this.tint(a1 + (float)time * 0.1F);
               float x0 = x + MathHelper.cos(a0 * 6.2831855F) * r;
               float z0 = z + MathHelper.sin(a0 * 6.2831855F) * r;
               float x1 = x + MathHelper.cos(a1 * 6.2831855F) * r;
               float z1 = z + MathHelper.sin(a1 * 6.2831855F) * r;
               float ty = ry + dir * tail;
               bb.vertex(mat, x0, ry, z0).color(ParticleShapes.withAlpha(c0, 0.8F));
               bb.vertex(mat, x1, ry, z1).color(ParticleShapes.withAlpha(c1, 0.8F));
               bb.vertex(mat, x1, ty, z1).color(ParticleShapes.withAlpha(c1, 0.0F));
               bb.vertex(mat, x0, ry, z0).color(ParticleShapes.withAlpha(c0, 0.8F));
               bb.vertex(mat, x1, ty, z1).color(ParticleShapes.withAlpha(c1, 0.0F));
               bb.vertex(mat, x0, ty, z0).color(ParticleShapes.withAlpha(c0, 0.0F));
               // яркая кромка
               float e = 0.025F;
               bb.vertex(mat, x0, ry - e, z0).color(ParticleShapes.withAlpha(0xFFFFFF, 0.9F));
               bb.vertex(mat, x1, ry - e, z1).color(ParticleShapes.withAlpha(0xFFFFFF, 0.9F));
               bb.vertex(mat, x1, ry + e, z1).color(ParticleShapes.withAlpha(0xFFFFFF, 0.9F));
               bb.vertex(mat, x0, ry - e, z0).color(ParticleShapes.withAlpha(0xFFFFFF, 0.9F));
               bb.vertex(mat, x1, ry + e, z1).color(ParticleShapes.withAlpha(0xFFFFFF, 0.9F));
               bb.vertex(mat, x0, ry + e, z0).color(ParticleShapes.withAlpha(0xFFFFFF, 0.9F));
            }
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
   }

   private int tint(float hue) {
      return this.rainbow.isEnabled() ? java.awt.Color.HSBtoRGB(hue, 0.6F, 1.0F) & 0xFFFFFF : this.color.getColor().getRGB() & 0xFFFFFF;
   }
}
