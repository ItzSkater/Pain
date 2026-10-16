package tech.pain.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.pain.Pain;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.impl.render.Emotes;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.level.ParticleShapes;

/** Колесо эмоций: восемь секторов вокруг центра, наведение подсвечивает, клик запускает. */
public class EmoteWheelScreen extends Screen {
   private final long openedAt = System.currentTimeMillis();
   private int hoveredIndex = -1;
   private final float[] glow = new float[Emotes.NAMES.length];

   public EmoteWheelScreen() {
      super(Text.of("Эмоции"));
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      CustomDrawContext ctx = CustomDrawContext.of(context);
      float cx = (float)this.width / 2.0F;
      float cy = (float)this.height / 2.0F;
      float open = MathHelper.clamp((float)(System.currentTimeMillis() - this.openedAt) / 180.0F, 0.0F, 1.0F);
      float ease = 1.0F - (1.0F - open) * (1.0F - open);
      float outer = 92.0F * (0.85F + 0.15F * ease);
      float inner = 34.0F;
      int n = Emotes.NAMES.length;
      float dx = (float)mouseX - cx;
      float dy = (float)mouseY - cy;
      float dist = (float)Math.sqrt((double)(dx * dx + dy * dy));
      this.hoveredIndex = -1;
      if (dist > inner * 0.8F) {
         float ang = (float)Math.atan2((double)dy, (double)dx) + 1.5708F + (float)Math.PI / (float)n;
         ang = (ang % 6.2832F + 6.2832F) % 6.2832F;
         this.hoveredIndex = (int)(ang / (6.2832F / (float)n)) % n;
      }

      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      int acc = theme.getColor().getRGB() & 0xFFFFFF;
      Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = 0; i < n; ++i) {
         this.glow[i] += ((i == this.hoveredIndex ? 1.0F : 0.0F) - this.glow[i]) * 0.3F;
         float a0 = (float)i / (float)n * 6.2832F - 1.5708F - (float)Math.PI / (float)n + 0.025F;
         float a1 = (float)(i + 1) / (float)n * 6.2832F - 1.5708F - (float)Math.PI / (float)n - 0.025F;
         float g = this.glow[i];
         float out = outer + 6.0F * g;
         int base = ParticleShapes.withAlpha(mix(0x0C0C10, acc, 0.35F * g), (0.78F + 0.1F * g) * ease);
         int edge = ParticleShapes.withAlpha(mix(0x14141A, acc, 0.6F * g), (0.85F + 0.1F * g) * ease);
         int steps = 10;

         for (int k = 0; k < steps; ++k) {
            float b0 = a0 + (a1 - a0) * (float)k / (float)steps;
            float b1 = a0 + (a1 - a0) * (float)(k + 1) / (float)steps;
            float c0 = MathHelper.cos(b0);
            float s0 = MathHelper.sin(b0);
            float c1 = MathHelper.cos(b1);
            float s1 = MathHelper.sin(b1);
            bb.vertex(mat, cx + c0 * inner, cy + s0 * inner, 0.0F).color(base);
            bb.vertex(mat, cx + c0 * out, cy + s0 * out, 0.0F).color(edge);
            bb.vertex(mat, cx + c1 * out, cy + s1 * out, 0.0F).color(edge);
            bb.vertex(mat, cx + c0 * inner, cy + s0 * inner, 0.0F).color(base);
            bb.vertex(mat, cx + c1 * out, cy + s1 * out, 0.0F).color(edge);
            bb.vertex(mat, cx + c1 * inner, cy + s1 * inner, 0.0F).color(base);
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      Font font = Fonts.MEDIUM.getFont(7.5F);

      for (int i = 0; i < n; ++i) {
         float mid = ((float)i + 0.5F) / (float)n * 6.2832F - 1.5708F - (float)Math.PI / (float)n;
         float r = (inner + outer) / 2.0F + 3.0F * this.glow[i];
         String name = Emotes.NAMES[i];
         float tx = cx + MathHelper.cos(mid) * r - font.width(name) / 2.0F;
         float ty = cy + MathHelper.sin(mid) * r - 3.5F;
         ctx.drawText(font, name, tx, ty, new ColorRGBA(200, 200, 210).mix(new ColorRGBA(255, 255, 255), this.glow[i]).withAlpha(255.0F * ease));
      }

      String center = this.hoveredIndex >= 0 ? Emotes.NAMES[this.hoveredIndex] : "Эмоции";
      Font big = Fonts.MEDIUM.getFont(8.0F);
      ctx.drawText(big, center, cx - big.width(center) / 2.0F, cy - 4.0F, theme.getColor().mix(new ColorRGBA(255, 255, 255), 0.3F).withAlpha(255.0F * ease));
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.hoveredIndex >= 0) {
         Emotes.INSTANCE.play(Emotes.NAMES[this.hoveredIndex]);
      }

      this.close();
      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private static int mix(int a, int b, float k) {
      int r = (int)((float)(a >> 16 & 0xFF) * (1.0F - k) + (float)(b >> 16 & 0xFF) * k);
      int g = (int)((float)(a >> 8 & 0xFF) * (1.0F - k) + (float)(b >> 8 & 0xFF) * k);
      int bl = (int)((float)(a & 0xFF) * (1.0F - k) + (float)(b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }
}
