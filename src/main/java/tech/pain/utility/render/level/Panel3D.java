package tech.pain.utility.render.level;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import tech.pain.Pain;
import tech.pain.base.animations.base.Animation;
import tech.pain.base.animations.base.Easing;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.base.theme.Theme;
import tech.pain.utility.interfaces.IMinecraft;
import tech.pain.utility.render.display.StencilUtil;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Экран в мире: прямоугольник со скруглёнными углами, на который натянута текстура.
 * Появляется и пропадает тем же «растворением», что и обычное меню (шейдер metanoise),
 * при закрытии остаётся на месте, пока не растворится.
 */
public final class Panel3D implements IMinecraft {
   private final Animation dissolve = new Animation(1400L, Easing.CIRC_OUT);
   private Vec3d center;
   private Vec3d right;
   private Vec3d normal;
   private boolean open;

   /** Ставит экран перед игроком на уровне глаз, лицом к нему. */
   public void place(double distance) {
      Camera cam = mc.gameRenderer.getCamera();
      double yaw = Math.toRadians(cam.getYaw());
      Vec3d dir = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
      this.center = cam.getPos().add(dir.multiply(distance));
      this.normal = dir.multiply(-1.0);
      this.right = new Vec3d(-Math.cos(yaw), 0.0, -Math.sin(yaw));
   }

   public void show() {
      this.open = true;
      this.dissolve.setDuration(1400L);
   }

   public void hide() {
      this.open = false;
      this.dissolve.setDuration(2200L);
   }

   public boolean isOpen() {
      return this.open;
   }

   /** Ещё рисуется: открыт или дорастворяется. */
   public boolean isVisible() {
      return this.open || this.dissolve.getValue() > 0.27F;
   }

   public boolean isPlaced() {
      return this.center != null;
   }

   public double distanceTo(Vec3d pos) {
      return this.center == null ? 0.0 : this.center.distanceTo(pos);
   }

   /** Куда на экране смотрит игрок: {u, v} в 0..1 или null. */
   public float[] aim(double width, double height) {
      if (this.center == null || !this.open) {
         return null;
      }

      Camera cam = mc.gameRenderer.getCamera();
      Vec3d camPos = cam.getPos();
      Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
      double denom = look.dotProduct(this.normal);
      if (denom >= -1.0E-4) {
         return null;
      }

      double t = this.center.subtract(camPos).dotProduct(this.normal) / denom;
      if (t <= 0.0 || t > 64.0) {
         return null;
      }

      Vec3d local = camPos.add(look.multiply(t)).subtract(this.center);
      double u = local.dotProduct(this.right) / width + 0.5;
      double v = 0.5 - local.y / height;
      return u >= 0.0 && u <= 1.0 && v >= 0.0 && v <= 1.0 ? new float[]{(float)u, (float)v} : null;
   }

   /**
    * @param texture GL-текстура; (u0,vTop)-(u1,vBottom) — какой её кусок показывать
    * @param pxW,pxH размер экрана в «пикселях» интерфейса (в них считаются скругление и шум)
    */
   public void draw(EventRender3D event, int texture, float u0, float vTop, float u1, float vBottom, float pxW, float pxH, double widthBlocks, float radius, float opacity, boolean glow) {
      if (this.center == null) {
         return;
      }

      this.dissolve.setEasing(Easing.CIRC_OUT);
      this.dissolve.update(this.open);
      float time = this.dissolve.getValue();
      if (!this.open && time <= 0.27F) {
         return;
      }

      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      Vec3d c = this.center.subtract(mc.gameRenderer.getCamera().getPos());
      float unit = (float)(widthBlocks / (double)pxW);
      MatrixStack ms = event.getMatrix();
      ms.push();
      ms.translate(c.x, c.y, c.z);
      // локальные оси: X — вправо по экрану, Y — вниз, Z — к зрителю; единица — пиксель интерфейса
      ms.multiplyPositionMatrix(new Matrix4f((float)this.right.x * unit, (float)this.right.y * unit, (float)this.right.z * unit, 0.0F, 0.0F, -unit, 0.0F, 0.0F, (float)this.normal.x * unit, (float)this.normal.y * unit, (float)this.normal.z * unit, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F));
      ms.translate(-pxW / 2.0F, -pxH / 2.0F, 0.0F);
      BorderRadius br = BorderRadius.all(radius);
      RenderSystem.enableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.depthMask(false);
      if (glow) {
         DrawUtil.drawShadow(ms, 0.0F, 0.0F, pxW, pxH, 26.0F, br, theme.getColor().withAlpha(120.0F * Math.min(1.0F, time)));
      }

      // маска: скруглённый прямоугольник, «проеденный» шумом — отсюда и растворение
      StencilUtil.push();
      DrawUtil.drawMetanoise(ms, 0.0F, 0.0F, pxW, pxH, time, radius, new ColorRGBA(0, 0, 0, 255), theme.getColor());
      StencilUtil.read(1);
      GL11.glEnable(GL11.GL_DEPTH_TEST);
      RenderSystem.disableCull();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.depthMask(true);
      Matrix4f mat = ms.peek().getPositionMatrix();
      int color = new ColorRGBA(255, 255, 255, 255.0F * opacity).getRGB();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
      RenderSystem.setShaderTexture(0, texture);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      bb.vertex(mat, 0.0F, 0.0F, 0.0F).texture(u0, vTop).color(color);
      bb.vertex(mat, 0.0F, pxH, 0.0F).texture(u0, vBottom).color(color);
      bb.vertex(mat, pxW, pxH, 0.0F).texture(u1, vBottom).color(color);
      bb.vertex(mat, pxW, 0.0F, 0.0F).texture(u1, vTop).color(color);
      BufferRenderer.drawWithGlobalProgram(bb.end());
      RenderSystem.setShaderTexture(0, 0);
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      // светящаяся кромка растворения и тонкая рамка
      DrawUtil.drawMetanoise(ms, 0.0F, 0.0F, pxW, pxH, time, radius, new ColorRGBA(0, 0, 0, 0), theme.getColor());
      DrawUtil.drawRoundedBorder(ms, 0.0F, 0.0F, pxW, pxH, -1.0F, br, theme.getColor().withAlpha(150));
      StencilUtil.pop();
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      ms.pop();
   }
}
