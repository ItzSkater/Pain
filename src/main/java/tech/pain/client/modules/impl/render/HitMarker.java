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

import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.base.events.impl.render.EventRender2D;

@ModuleAnnotation(
   name = "HitMarker",
   category = Category.RENDER,
   description = "Метка попадания вокруг прицела при ударе"
)
public final class HitMarker extends Module {
   public static final HitMarker INSTANCE = new HitMarker();
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 255, 255));
   private final ColorSetting critColor = new ColorSetting("Цвет крита", new ColorRGBA(255, 70, 80));
   private final NumberSetting size = new NumberSetting("Размер", 7.5F, 3.0F, 14.0F, 0.5F);
   private final NumberSetting thickness = new NumberSetting("Толщина", 1.2F, 0.5F, 2.5F, 0.1F);
   private final NumberSetting time = new NumberSetting("Длительность (мс)", 350.0F, 150.0F, 900.0F, 50.0F);
   private long hitAt;
   private boolean crit;

   private HitMarker() {
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      if (mc.player == null || event.getTarget() == null) {
         return;
      }

      this.hitAt = System.currentTimeMillis();
      this.crit = mc.player.fallDistance > 0.0F && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater();
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      float t = (float)(System.currentTimeMillis() - this.hitAt) / this.time.getCurrent();
      if (mc.player == null || t >= 1.0F || t < 0.0F) {
         return;
      }

      Matrix4f mat = event.getContext().getMatrices().peek().getPositionMatrix();
      float cx = (float)mc.getWindow().getScaledWidth() / 2.0F;
      float cy = (float)mc.getWindow().getScaledHeight() / 2.0F;
      // метка выстреливает наружу и гаснет
      float ease = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
      float inner = 3.0F + ease * 3.0F;
      float outer = inner + this.size.getCurrent() * (this.crit ? 1.25F : 1.0F);
      float alpha = 1.0F - t * t;
      int rgb = (this.crit ? this.critColor : this.color).getColor().getRGB() & 0xFFFFFF;
      float w = this.thickness.getCurrent() * 0.5F;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = 0; i < 4; ++i) {
         float dx = (i & 1) == 0 ? 0.70710677F : -0.70710677F;
         float dy = (i & 2) == 0 ? 0.70710677F : -0.70710677F;
         float nx = -dy;
         float ny = dx;
         // тень под штрихом, потом сам штрих
         stroke(bb, mat, cx, cy, dx, dy, nx, ny, inner - 0.5F, outer + 0.5F, w + 0.5F, ParticleShapes.withAlpha(0, alpha * 0.45F), ParticleShapes.withAlpha(0, alpha * 0.45F));
         stroke(bb, mat, cx, cy, dx, dy, nx, ny, inner, outer, w, ParticleShapes.withAlpha(rgb, alpha), ParticleShapes.withAlpha(rgb, alpha * 0.35F));
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   private static void stroke(BufferBuilder bb, Matrix4f m, float cx, float cy, float dx, float dy, float nx, float ny, float from, float to, float w, int c0, int c1) {
      float ax = cx + dx * from;
      float ay = cy + dy * from;
      float bx = cx + dx * to;
      float by = cy + dy * to;
      bb.vertex(m, ax - nx * w, ay - ny * w, 0.0F).color(c0);
      bb.vertex(m, ax + nx * w, ay + ny * w, 0.0F).color(c0);
      bb.vertex(m, bx + nx * w, by + ny * w, 0.0F).color(c1);
      bb.vertex(m, ax - nx * w, ay - ny * w, 0.0F).color(c0);
      bb.vertex(m, bx + nx * w, by + ny * w, 0.0F).color(c1);
      bb.vertex(m, bx - nx * w, by - ny * w, 0.0F).color(c1);
   }
}
