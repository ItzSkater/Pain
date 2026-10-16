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
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Косметика своего персонажа. Плащ — своя текстура вместо плаща из скина, поэтому он развевается
 * с ванильной физикой. Крылья — светящиеся перья цвета темы, медленно дышат и машут сильнее в прыжке.
 * Видно только тебе: у других игроков нет Pain, чтобы это нарисовать.
 */
@ModuleAnnotation(
   name = "Cosmetics",
   category = Category.RENDER,
   description = "Свой плащ (Pain, перелив темы, неон) и светящиеся крылья"
)
public final class Cosmetics extends Module {
   public static final Cosmetics INSTANCE = new Cosmetics();
   private static final Identifier CAPE = Identifier.of("pain", "cosmetics/cape");
   private final ModeSetting cape = new ModeSetting("Плащ", "Pain", "Тема", "Неон", "Выкл");
   private final BooleanSetting wings = new BooleanSetting("Крылья", true);
   private final NumberSetting wingSize = new NumberSetting("Размер крыльев", 1.0F, 0.5F, 1.6F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.wings.isEnabled());
   private final NumberSetting wingAlpha = new NumberSetting("Яркость крыльев", 0.7F, 0.2F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.wings.isEnabled());
   private NativeImage image;
   private NativeImageBackedTexture texture;
   private String painted = "";
   private long paintedAt;

   private Cosmetics() {
   }

   /** Подменяет плащ своему игроку прямо в состоянии отрисовки. */
   public void applyCape(PlayerEntityRenderState state) {
      if (!this.isEnabled() || this.cape.is("Выкл") || mc.player == null || state.id != mc.player.getId() || state.skinTextures == null) {
         return;
      }

      this.paint();
      SkinTextures old = state.skinTextures;
      state.skinTextures = new SkinTextures(old.texture(), old.textureUrl(), CAPE, old.elytraTexture(), old.model(), old.secure());
      state.capeVisible = true;
   }

   /** Рисует текстуру плаща 64×32: перед — 10×16 пикселей в (1,1), зад — в (12,1), края вокруг. */
   private void paint() {
      boolean animated = !this.cape.is("Pain");
      long now = System.currentTimeMillis();
      if (this.cape.get().equals(this.painted) && (!animated || now - this.paintedAt < 80L) && this.texture != null) {
         return;
      }

      if (this.image == null) {
         this.image = new NativeImage(64, 32, true);
         this.texture = new NativeImageBackedTexture(this.image);
         mc.getTextureManager().registerTexture(CAPE, this.texture);
      }

      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      int c1 = theme.getColor().getRGB() & 0xFFFFFF;
      int c2 = theme.getSecondColor().getRGB() & 0xFFFFFF;
      float time = (float)(now % 100000L) / 1000.0F;

      for (int y = 0; y < 32; ++y) {
         for (int x = 0; x < 64; ++x) {
            int color = 0;
            if (x < 23 && y < 17) {
               boolean back = x >= 12;
               float u = back ? (float)(x - 12) / 10.0F : (float)(x - 1) / 10.0F;
               float v = (float)(y - 1) / 16.0F;
               color = this.capePixel(u, v, c1, c2, time);
               if (back) {
                  color = scale(color, 0.6F);
               }

               color |= 0xFF000000;
            }

            this.image.setColorArgb(x, y, color);
         }
      }

      this.texture.upload();
      this.painted = this.cape.get();
      this.paintedAt = now;
   }

   private int capePixel(float u, float v, int c1, int c2, float time) {
      if (this.cape.is("Pain")) {
         // чёрный сверху, красный снизу, по центру лого: шестиугольник из шести клиньев
         int base = mix(0x0D0D10, 0xB0121A, MathHelper.clamp(v * 1.2F - 0.1F, 0.0F, 1.0F));
         float lx = (u - 0.5F) * 2.0F;
         float ly = (v - 0.42F) * 2.0F * 1.6F;
         float r = (float)Math.sqrt((double)(lx * lx + ly * ly));
         float ang = (float)Math.atan2((double)ly, (double)lx);
         float sector = ang / 1.0472F - (float)Math.floor((double)(ang / 1.0472F));
         boolean wedge = r < 0.82F && r > 0.22F && Math.abs(sector - 0.5F) < 0.4F;
         return wedge ? mix(0xE01414, 0xFF5A5A, MathHelper.clamp(1.0F - v, 0.0F, 1.0F) * 0.5F) : base;
      } else if (this.cape.is("Тема")) {
         // перелив двух цветов темы с бегущими диагональными полосами
         float wave = 0.5F + 0.5F * MathHelper.sin((u + v) * 6.0F - time * 2.0F);
         int base = mix(c1, c2, MathHelper.clamp(v * 0.7F + wave * 0.3F, 0.0F, 1.0F));
         return scale(base, 0.55F + 0.45F * wave);
      } else {
         // неон: тёмный плащ, светящаяся рамка и полоса, по которой бежит свет
         float edge = Math.min(Math.min(u, 1.0F - u), Math.min(v, 1.0F - v));
         float run = 0.5F + 0.5F * MathHelper.sin(v * 10.0F - time * 5.0F);
         int base = 0x0A0A0E;
         if (edge < 0.09F) {
            return mix(base, mix(c1, c2, v), 0.6F + 0.4F * run);
         } else {
            return Math.abs(u - 0.5F) < 0.06F ? mix(base, c1, 0.35F + 0.5F * run) : base;
         }
      }
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (!this.wings.isEnabled() || mc.player == null || mc.options.getPerspective().isFirstPerson() || mc.player.isInvisible()) {
         return;
      }

      float pt = event.getPartialTicks();
      net.minecraft.util.math.Vec3d cam = mc.gameRenderer.getCamera().getPos();
      double px = MathHelper.lerp((double)pt, mc.player.lastRenderX, mc.player.getX()) - cam.x;
      double py = MathHelper.lerp((double)pt, mc.player.lastRenderY, mc.player.getY()) - cam.y;
      double pz = MathHelper.lerp((double)pt, mc.player.lastRenderZ, mc.player.getZ()) - cam.z;
      float bodyYaw = MathHelper.lerpAngleDegrees(pt, mc.player.prevBodyYaw, mc.player.bodyYaw);
      float yaw = (float)Math.toRadians((double)bodyYaw);
      // оси тела: вправо, вверх, назад
      Vector3f right = new Vector3f(-MathHelper.cos(yaw), 0.0F, -MathHelper.sin(yaw));
      Vector3f back = new Vector3f(MathHelper.sin(yaw), 0.0F, -MathHelper.cos(yaw));
      float shoulder = mc.player.isInSneakingPose() ? 1.1F : 1.32F;
      Vector3f root = new Vector3f((float)px, (float)py + shoulder, (float)pz).add(new Vector3f(back).mul(0.2F));
      float time = (float)(System.currentTimeMillis() % 100000L) / 1000.0F;
      boolean air = !mc.player.isOnGround();
      float flap = air ? 0.55F + 0.35F * MathHelper.sin(time * 9.0F) : 0.35F + 0.08F * MathHelper.sin(time * 2.2F);
      float size = this.wingSize.getCurrent();
      float alpha = this.wingAlpha.getCurrent();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      int c1 = theme.getColor().getRGB() & 0xFFFFFF;
      int c2 = theme.getSecondColor().getRGB() & 0xFFFFFF;
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int side = -1; side <= 1; side += 2) {
         // крыло в своей плоскости: в сторону и вверх, отведено назад на угол взмаха
         Vector3f out = new Vector3f(right).mul((float)side * MathHelper.cos(flap)).add(new Vector3f(back).mul(MathHelper.sin(flap)));
         Vector3f base = new Vector3f(root).add(new Vector3f(right).mul((float)side * 0.1F));
         int feathers = 7;

         for (int i = 0; i < feathers; ++i) {
            float f = (float)i / (float)(feathers - 1);
            float elev = 0.25F + f * 1.25F;
            float len = (0.75F + 0.55F * MathHelper.sin(f * 3.1416F)) * size;
            Vector3f dir = new Vector3f(out).mul(MathHelper.cos(elev)).add(0.0F, MathHelper.sin(elev), 0.0F).normalize();
            Vector3f across = new Vector3f(out).mul(-MathHelper.sin(elev)).add(0.0F, MathHelper.cos(elev), 0.0F).normalize();
            int color = mix(c1, c2, f);
            this.feather(bb, mat, base, dir, across, len, 0.11F * size, color, alpha);
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

   /** Перо: сужается к кончику, у основания ярче; поверх тонкая светлая сердцевина. */
   private void feather(BufferBuilder bb, Matrix4f m, Vector3f base, Vector3f dir, Vector3f across, float len, float width, int rgb, float alpha) {
      int seg = 4;

      for (int s = 0; s < seg; ++s) {
         float t0 = (float)s / (float)seg;
         float t1 = (float)(s + 1) / (float)seg;
         float w0 = width * (1.0F - t0 * 0.85F);
         float w1 = width * (1.0F - t1 * 0.85F);
         Vector3f p0 = new Vector3f(dir).mul(len * t0).add(base);
         Vector3f p1 = new Vector3f(dir).mul(len * t1).add(base);
         int a0 = ParticleShapes.withAlpha(rgb, alpha * (1.0F - t0 * 0.7F) * 0.55F);
         int a1 = ParticleShapes.withAlpha(rgb, alpha * (1.0F - t1 * 0.7F) * 0.55F);
         quad(bb, m, new Vector3f(across).mul(-w0).add(p0), a0, new Vector3f(across).mul(w0).add(p0), a0, new Vector3f(across).mul(w1).add(p1), a1, new Vector3f(across).mul(-w1).add(p1), a1);
         int k0 = ParticleShapes.withAlpha(mix(rgb, 0xFFFFFF, 0.6F), alpha * (1.0F - t0) * 0.8F);
         int k1 = ParticleShapes.withAlpha(mix(rgb, 0xFFFFFF, 0.6F), alpha * (1.0F - t1) * 0.8F);
         float c0 = w0 * 0.18F;
         float c1 = w1 * 0.18F;
         quad(bb, m, new Vector3f(across).mul(-c0).add(p0), k0, new Vector3f(across).mul(c0).add(p0), k0, new Vector3f(across).mul(c1).add(p1), k1, new Vector3f(across).mul(-c1).add(p1), k1);
      }
   }

   private static void quad(BufferBuilder bb, Matrix4f m, Vector3f a, int ca, Vector3f b, int cb, Vector3f c, int cc, Vector3f d, int cd) {
      bb.vertex(m, a.x, a.y, a.z).color(ca);
      bb.vertex(m, b.x, b.y, b.z).color(cb);
      bb.vertex(m, c.x, c.y, c.z).color(cc);
      bb.vertex(m, a.x, a.y, a.z).color(ca);
      bb.vertex(m, c.x, c.y, c.z).color(cc);
      bb.vertex(m, d.x, d.y, d.z).color(cd);
   }

   private static int scale(int rgb, float k) {
      return (int)Math.min(255.0F, (float)(rgb >> 16 & 0xFF) * k) << 16 | (int)Math.min(255.0F, (float)(rgb >> 8 & 0xFF) * k) << 8 | (int)Math.min(255.0F, (float)(rgb & 0xFF) * k);
   }

   private static int mix(int a, int b, float k) {
      int r = (int)((float)(a >> 16 & 0xFF) * (1.0F - k) + (float)(b >> 16 & 0xFF) * k);
      int g = (int)((float)(a >> 8 & 0xFF) * (1.0F - k) + (float)(b >> 8 & 0xFF) * k);
      int bl = (int)((float)(a & 0xFF) * (1.0F - k) + (float)(b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }
}
