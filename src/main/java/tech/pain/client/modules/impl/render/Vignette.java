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

import tech.pain.base.events.impl.render.EventRender2D;

@ModuleAnnotation(
   name = "Vignette",
   category = Category.RENDER,
   description = "Затемнение краёв экрана, красный пульс при низком здоровье и вспышка при уроне"
)
public final class Vignette extends Module {
   public static final Vignette INSTANCE = new Vignette();
   private final NumberSetting dark = new NumberSetting("Затемнение краёв", 0.35F, 0.0F, 0.9F, 0.05F);
   private final NumberSetting width = new NumberSetting("Ширина", 0.28F, 0.1F, 0.5F, 0.02F);
   private final BooleanSetting lowHealth = new BooleanSetting("Пульс при низком здоровье", true);
   private final NumberSetting threshold = new NumberSetting("Порог здоровья", 8.0F, 2.0F, 16.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.lowHealth.isEnabled());
   private final BooleanSetting hurtFlash = new BooleanSetting("Вспышка при уроне", true);
   private final ColorSetting danger = new ColorSetting("Цвет опасности", new ColorRGBA(255, 30, 45));
   private float red;
   private long lastFrame;

   private Vignette() {
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      if (mc.player == null || mc.options.hudHidden) {
         return;
      }

      long now = System.currentTimeMillis();
      float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      float want = 0.0F;
      float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      if (this.lowHealth.isEnabled() && hp <= this.threshold.getCurrent() && !mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.isDead()) {
         float lack = 1.0F - hp / this.threshold.getCurrent();
         // чем меньше здоровья, тем чаще и сильнее бьётся
         float beat = 0.5F + 0.5F * (float)Math.sin((double)now / (double)(260.0F - 140.0F * lack));
         want = 0.18F + 0.32F * lack * beat + 0.1F * lack;
      }

      if (this.hurtFlash.isEnabled() && mc.player.hurtTime > 0 && !mc.player.isDead()) {
         want = Math.max(want, 0.5F * (float)mc.player.hurtTime / 10.0F);
      }

      this.red += (want - this.red) * Math.min(1.0F, dt * (want > this.red ? 22.0F : 5.0F));
      Matrix4f mat = event.getContext().getMatrices().peek().getPositionMatrix();
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      if (this.dark.getCurrent() > 0.01F) {
         frame(bb, mat, sw, sh, this.width.getCurrent(), 0, this.dark.getCurrent());
      }

      if (this.red > 0.01F) {
         frame(bb, mat, sw, sh, this.width.getCurrent() * 0.8F, this.danger.getColor().getRGB() & 0xFFFFFF, this.red);
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   /** Рамка-градиент: по краю экрана цвет, к центру — прозрачно. Углы сходятся под 45°, без швов. */
   private static void frame(BufferBuilder bb, Matrix4f m, float sw, float sh, float part, int rgb, float alpha) {
      float in = Math.min(sw, sh) * part;
      int edge = ParticleShapes.withAlpha(rgb, alpha);
      int clear = ParticleShapes.withAlpha(rgb, 0.0F);
      float[][] outer = new float[][]{{0.0F, 0.0F}, {sw, 0.0F}, {sw, sh}, {0.0F, sh}};
      float[][] inner = new float[][]{{in, in}, {sw - in, in}, {sw - in, sh - in}, {in, sh - in}};

      for (int i = 0; i < 4; ++i) {
         int j = (i + 1) % 4;
         bb.vertex(m, outer[i][0], outer[i][1], 0.0F).color(edge);
         bb.vertex(m, outer[j][0], outer[j][1], 0.0F).color(edge);
         bb.vertex(m, inner[j][0], inner[j][1], 0.0F).color(clear);
         bb.vertex(m, outer[i][0], outer[i][1], 0.0F).color(edge);
         bb.vertex(m, inner[j][0], inner[j][1], 0.0F).color(clear);
         bb.vertex(m, inner[i][0], inner[i][1], 0.0F).color(clear);
      }
   }
}
