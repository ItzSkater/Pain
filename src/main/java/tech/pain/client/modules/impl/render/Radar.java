package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender2D;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.combat.AntiBot;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.utility.math.MathUtil;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Радар: круг, который поворачивается вместе с тобой — твой взгляд всегда вверх. Конус показывает
 * поле зрения. Игроки — точки (друзья зелёные, цель Aura пульсирует), мобы — мельче. Если сущность
 * заметно выше или ниже тебя, рядом с точкой маленькая стрелка вверх или вниз.
 */
@ModuleAnnotation(
   name = "Radar",
   category = Category.RENDER,
   description = "Круглый радар, поворачивается за взглядом: игроки, мобы, конус обзора, стрелки выше/ниже"
)
public final class Radar extends Module {
   public static final Radar INSTANCE = new Radar();
   private final NumberSetting size = new NumberSetting("Размер", 96.0F, 50.0F, 200.0F, 2.0F);
   private final NumberSetting range = new NumberSetting("Дальность (блоков)", 48.0F, 8.0F, 160.0F, 4.0F);
   private final NumberSetting posX = new NumberSetting("Позиция X (%)", 2.0F, 0.0F, 100.0F, 0.5F);
   private final NumberSetting posY = new NumberSetting("Позиция Y (%)", 38.0F, 0.0F, 100.0F, 0.5F);
   private final BooleanSetting mobs = new BooleanSetting("Мобы", false);
   private final BooleanSetting fovCone = new BooleanSetting("Конус обзора", true);
   private final BooleanSetting north = new BooleanSetting("Север", true);
   private final NumberSetting heightMark = new NumberSetting("Стрелка выше/ниже от (блоков)", 3.0F, 1.0F, 20.0F, 0.5F);

   private Radar() {
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      if (mc.player == null || mc.world == null || mc.options.hudHidden) {
         return;
      }

      CustomDrawContext ctx = event.getContext();
      Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
      float w = (float)mc.getWindow().getScaledWidth();
      float h = (float)mc.getWindow().getScaledHeight();
      float d = this.size.getCurrent();
      float r = d / 2.0F;
      float left = MathHelper.clamp(w * this.posX.getCurrent() / 100.0F, 0.0F, w - d);
      float top = MathHelper.clamp(h * this.posY.getCurrent() / 100.0F, 0.0F, h - d);
      float cx = left + r;
      float cy = top + r;
      ColorRGBA accent = Pain.getInstance().getThemeManager().getCurrentTheme().getColor();
      int acc = accent.getRGB() & 0xFFFFFF;
      // подложка — круг
      DrawUtil.drawShadow(ctx.getMatrices(), left, top, d, d, 14.0F, BorderRadius.all(r), new ColorRGBA(0, 0, 0, 120));
      DrawUtil.drawRoundedRect(ctx.getMatrices(), left, top, d, d, BorderRadius.all(r), new ColorRGBA(14, 14, 20, 170));
      DrawUtil.drawRoundedBorder(ctx.getMatrices(), left, top, d, d, -0.6F, BorderRadius.all(r), accent.withAlpha(120));
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cam = MathUtil.interpolate(mc.player);
      float yaw = (float)Math.toRadians((double)camera.getYaw());
      float sin = MathHelper.sin(yaw);
      float cos = MathHelper.cos(yaw);
      double time = (double)System.currentTimeMillis() / 1000.0;
      float scale = r / this.range.getCurrent();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      if (this.fovCone.isEnabled()) {
         // горизонтальный угол обзора из вертикального и пропорций экрана
         double vfov = Math.toRadians((double)(Integer)mc.options.getFov().getValue());
         float half = (float)Math.atan(Math.tan(vfov / 2.0) * (double)(w / h));
         int steps = 18;

         for (int i = 0; i < steps; ++i) {
            float a0 = -half + 2.0F * half * (float)i / (float)steps - 1.5708F;
            float a1 = -half + 2.0F * half * (float)(i + 1) / (float)steps - 1.5708F;
            tri(bb, mat, cx, cy, ParticleShapes.withAlpha(acc, 0.28F), cx + MathHelper.cos(a0) * r * 0.97F, cy + MathHelper.sin(a0) * r * 0.97F, ParticleShapes.withAlpha(acc, 0.02F), cx + MathHelper.cos(a1) * r * 0.97F, cy + MathHelper.sin(a1) * r * 0.97F, ParticleShapes.withAlpha(acc, 0.02F));
         }
      }

      // перекрестье и кольца дальности
      int line = ParticleShapes.withAlpha(0xFFFFFF, 0.08F);
      rect(bb, mat, cx - r + 4.0F, cy - 0.3F, d - 8.0F, 0.6F, line);
      rect(bb, mat, cx - 0.3F, cy - r + 4.0F, 0.6F, d - 8.0F, line);
      ringLine(bb, mat, cx, cy, r * 0.5F, 0.5F, line);

      for (Entity e : mc.world.getEntities()) {
         if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive()) {
            continue;
         }

         boolean player = e instanceof PlayerEntity;
         if (!player && !this.mobs.isEnabled()) {
            continue;
         }

         if (player && (((PlayerEntity)e).isSpectator() || AntiBot.INSTANCE.isEnabled() && AntiBot.INSTANCE.isBot((PlayerEntity)e))) {
            continue;
         }

         Vec3d pos = MathUtil.interpolate(e);
         double dx = pos.x - cam.x;
         double dz = pos.z - cam.z;
         float fwd = (float)(-dx * (double)sin + dz * (double)cos) * scale;
         float side = (float)(-dx * (double)cos - dz * (double)sin) * scale;
         float px = cx + side;
         float py = cy - fwd;
         float len = (float)Math.sqrt((double)(side * side + fwd * fwd));
         float alpha = 1.0F;
         if (len > r - 4.0F) {
            // за пределами дальности — прижимаем к краю и делаем бледнее
            if (len > r * 1.6F) {
               continue;
            }

            px = cx + side / len * (r - 4.0F);
            py = cy - fwd / len * (r - 4.0F);
            alpha = 0.45F;
         }

         int rgb;
         float dot;
         if (player) {
            boolean friend = Pain.getInstance().getFriendManager().isFriend(e.getName().getString());
            boolean target = Aura.INSTANCE.isEnabled() && Aura.INSTANCE.getTarget() == e;
            rgb = target ? 0xFF3B4A : friend ? 0x6EE89A : mix(acc, 0xFFFFFF, 0.25F);
            dot = 2.6F;
            if (target) {
               dot *= 1.0F + 0.35F * (0.5F + 0.5F * (float)Math.sin(time * 9.0));
            }
         } else {
            rgb = e instanceof Monster ? 0xFF9A3C : 0xB4B8C4;
            dot = 1.7F;
         }

         circle(bb, mat, px, py, dot * 2.4F, ParticleShapes.withAlpha(rgb, 0.25F * alpha), ParticleShapes.withAlpha(rgb, 0.0F));
         circle(bb, mat, px, py, dot, ParticleShapes.withAlpha(rgb, alpha), ParticleShapes.withAlpha(rgb, alpha));
         double dy = pos.y - cam.y;
         float mark = this.heightMark.getCurrent();
         if (Math.abs(dy) >= (double)mark) {
            float ox = px + dot + 2.2F;
            float s = 2.0F;
            int c = ParticleShapes.withAlpha(0xFFFFFF, 0.85F * alpha);
            if (dy > 0.0) {
               tri(bb, mat, ox, py - s, c, ox - s, py + s * 0.6F, c, ox + s, py + s * 0.6F, c);
            } else {
               tri(bb, mat, ox, py + s, c, ox + s, py - s * 0.6F, c, ox - s, py - s * 0.6F, c);
            }
         }
      }

      // ты — стрелка в центре, всегда смотрит вверх
      int me = ParticleShapes.withAlpha(0xFFFFFF, 0.95F);
      tri(bb, mat, cx, cy - 4.0F, me, cx - 3.0F, cy + 3.0F, me, cx, cy + 1.5F, me);
      tri(bb, mat, cx, cy - 4.0F, me, cx, cy + 1.5F, me, cx + 3.0F, cy + 3.0F, me);
      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      if (this.north.isEnabled()) {
         // север — это -Z
         float fwd = cos * -1.0F;
         float side = sin;
         float len = Math.max(1.0E-4F, (float)Math.sqrt((double)(fwd * fwd + side * side)));
         float nx = cx + side / len * (r - 8.0F);
         float ny = cy + fwd / len * (r - 8.0F) * -1.0F;
         Font font = Fonts.MEDIUM.getFont(6.5F);
         ctx.drawText(font, "N", nx - font.width("N") / 2.0F, ny - font.height() / 2.0F, accent.withAlpha(230));
      }
   }

   private static void tri(BufferBuilder bb, Matrix4f m, float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc) {
      bb.vertex(m, ax, ay, 0.0F).color(ca);
      bb.vertex(m, bx, by, 0.0F).color(cb);
      bb.vertex(m, cx, cy, 0.0F).color(cc);
   }

   private static void rect(BufferBuilder bb, Matrix4f m, float x, float y, float w, float h, int c) {
      tri(bb, m, x, y, c, x, y + h, c, x + w, y + h, c);
      tri(bb, m, x, y, c, x + w, y + h, c, x + w, y, c);
   }

   private static void circle(BufferBuilder bb, Matrix4f m, float x, float y, float r, int center, int edge) {
      for (int i = 0; i < 14; ++i) {
         float a0 = (float)i / 14.0F * 6.2832F;
         float a1 = (float)(i + 1) / 14.0F * 6.2832F;
         tri(bb, m, x, y, center, x + MathHelper.cos(a0) * r, y + MathHelper.sin(a0) * r, edge, x + MathHelper.cos(a1) * r, y + MathHelper.sin(a1) * r, edge);
      }
   }

   private static void ringLine(BufferBuilder bb, Matrix4f m, float x, float y, float r, float t, int c) {
      for (int i = 0; i < 48; ++i) {
         float a0 = (float)i / 48.0F * 6.2832F;
         float a1 = (float)(i + 1) / 48.0F * 6.2832F;
         float c0 = MathHelper.cos(a0);
         float s0 = MathHelper.sin(a0);
         float c1 = MathHelper.cos(a1);
         float s1 = MathHelper.sin(a1);
         tri(bb, m, x + c0 * (r - t), y + s0 * (r - t), c, x + c0 * (r + t), y + s0 * (r + t), c, x + c1 * (r + t), y + s1 * (r + t), c);
         tri(bb, m, x + c0 * (r - t), y + s0 * (r - t), c, x + c1 * (r + t), y + s1 * (r + t), c, x + c1 * (r - t), y + s1 * (r - t), c);
      }
   }

   private static int mix(int a, int b, float k) {
      int r = (int)((float)(a >> 16 & 0xFF) * (1.0F - k) + (float)(b >> 16 & 0xFF) * k);
      int g = (int)((float)(a >> 8 & 0xFF) * (1.0F - k) + (float)(b >> 8 & 0xFF) * k);
      int bl = (int)((float)(a & 0xFF) * (1.0F - k) + (float)(b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }
}
