package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;
import tech.pain.utility.render.level.ParticleShapes;
import tech.pain.utility.render.level.Render3DUtil;

/**
 * Эффект прыжка: в момент прыжка (и по желанию приземления) под ногами срабатывает эффект —
 * светящиеся кубы, которые разлетаются и меняют размер, кольцо по земле, искры, столб света
 * или волна искажения. Искажение можно добавить к любому эффекту.
 */
@ModuleAnnotation(
   name = "JumpEffect",
   category = Category.RENDER,
   description = "Эффект при прыжке: кубы, кольцо, искры, столб света, искажение"
)
public final class JumpEffect extends Module {
   public static final JumpEffect INSTANCE = new JumpEffect();
   private static final int CUBE = 0;
   private static final int SPARK = 1;
   private final ModeSetting effect = new ModeSetting("Эффект", "Кубы", "Кольцо", "Искры", "Столб света", "Искажение");
   private final BooleanSetting distort = new BooleanSetting("Добавить искажение", true, () -> !this.effect.is("Искажение"));
   private final NumberSetting distortPower = new NumberSetting("Сила искажения", 1.0F, 0.2F, 3.0F, 0.1F);
   private final BooleanSetting onLand = new BooleanSetting("И при приземлении", true);
   private final NumberSetting amount = new NumberSetting("Количество", 16.0F, 6.0F, 48.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.effect.is("Кубы") || this.effect.is("Искры"));
   private final NumberSetting size = new NumberSetting("Размер", 1.0F, 0.4F, 3.0F, 0.1F);
   private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Радуга", "Свой");
   private final ColorSetting color = new ColorSetting("Свой цвет", new ColorRGBA(120, 200, 255), () -> this.colorMode.is("Свой"));
   private final List<Particle> particles = new ArrayList<>();
   private final List<Burst> bursts = new ArrayList<>();
   private boolean wasOnGround = true;
   private double lastFall;
   private long lastFrame;

   private JumpEffect() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.particles.clear();
      this.bursts.clear();
   }

   private int pick(float phase) {
      if (this.colorMode.is("Радуга")) {
         return ParticleShapes.rainbow(phase, 0.7F, 1.0F);
      }

      if (this.colorMode.is("Свой")) {
         return this.color.getColor().getRGB() & 0xFFFFFF;
      }

      return Pain.getInstance().getThemeManager().getClientColor((int)(phase * 360.0F)).getRGB() & 0xFFFFFF;
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null) {
         return;
      }

      boolean ground = mc.player.isOnGround();
      if (this.wasOnGround && !ground && mc.player.getVelocity().y > 0.2) {
         this.trigger(1.0F);
      } else if (!this.wasOnGround && ground && this.onLand.isEnabled() && this.lastFall > 0.6) {
         // приземление — эффект поменьше, после совсем мелких шагов вниз не срабатывает
         this.trigger(0.7F);
      }

      this.lastFall = ground ? 0.0 : Math.max(this.lastFall, (double)mc.player.fallDistance);
      this.wasOnGround = ground;
   }

   private void trigger(float power) {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      Vec3d feet = mc.player.getPos();
      float scale = this.size.getCurrent() * power;
      boolean onlyDistort = this.effect.is("Искажение");
      if (this.bursts.size() > 8) {
         this.bursts.remove(0);
      }

      Burst burst = new Burst();
      burst.pos = feet;
      burst.scale = scale;
      burst.kind = onlyDistort ? "" : this.effect.get();
      burst.wave = onlyDistort || this.distort.isEnabled();
      burst.life = 0.75F;
      burst.phase = rnd.nextFloat();
      this.bursts.add(burst);
      if (!this.effect.is("Кубы") && !this.effect.is("Искры")) {
         return;
      }

      int count = (int)this.amount.getCurrent();
      boolean cubes = this.effect.is("Кубы");

      for (int i = 0; i < count && this.particles.size() < 600; ++i) {
         Particle p = new Particle();
         p.kind = cubes ? CUBE : SPARK;
         double angle = (double)i / (double)count * Math.PI * 2.0 + rnd.nextDouble(-0.15, 0.15);
         p.x = feet.x + Math.cos(angle) * 0.3;
         p.y = feet.y + 0.05;
         p.z = feet.z + Math.sin(angle) * 0.3;
         p.ground = feet.y;
         p.rgb = this.pick((float)i / (float)count);
         if (cubes) {
            double speed = rnd.nextDouble(2.2, 3.6) * (double)power;
            p.vx = Math.cos(angle) * speed;
            p.vz = Math.sin(angle) * speed;
            p.vy = rnd.nextDouble(1.2, 2.6);
            p.size = (0.07F + rnd.nextFloat() * 0.06F) * scale;
            p.life = 0.8F + rnd.nextFloat() * 0.5F;
         } else {
            double speed = rnd.nextDouble(1.5, 4.5) * (double)power;
            p.vx = Math.cos(angle) * speed;
            p.vz = Math.sin(angle) * speed;
            p.vy = rnd.nextDouble(2.5, 6.0) * (double)power;
            p.size = (0.03F + rnd.nextFloat() * 0.03F) * scale;
            p.life = 0.5F + rnd.nextFloat() * 0.6F;
         }

         this.particles.add(p);
      }
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.05F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      if (mc.player == null || mc.world == null || this.particles.isEmpty() && this.bursts.isEmpty()) {
         return;
      }

      if (mc.isPaused()) {
         dt = 0.0F;
      }

      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Quaternionf rotation = mc.gameRenderer.getCamera().getRotation();
      Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
      Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = this.particles.size() - 1; i >= 0; --i) {
         Particle p = this.particles.get(i);
         p.age += dt;
         if (p.age >= p.life) {
            this.particles.remove(i);
            continue;
         }

         float t = p.age / p.life;
         if (p.kind == CUBE) {
            // разлёт с торможением, лёгкий подскок и падение обратно на землю
            double keep = Math.max(0.0, 1.0 - 2.4 * (double)dt);
            p.vx *= keep;
            p.vz *= keep;
            p.vy -= 6.5 * (double)dt;
            p.x += p.vx * (double)dt;
            p.y = Math.max(p.ground + 0.02, p.y + p.vy * (double)dt);
            p.z += p.vz * (double)dt;
            // размер: быстро вырастает, пульсирует и сжимается в точку
            float grow = Math.min(1.0F, t / 0.2F);
            float pulse = 1.0F + 0.25F * MathHelper.sin(p.age * 14.0F + (float)p.rgb);
            float s = p.size * grow * pulse * (1.0F - t * t);
            if (s > 0.004F) {
               int alpha = (int)(230.0F * (1.0F - t * 0.5F));
               Box box = new Box(p.x - (double)s, p.y, p.z - (double)s, p.x + (double)s, p.y + (double)(s * 2.0F), p.z + (double)s);
               Render3DUtil.drawBox(box, alpha << 24 | p.rgb, 1.2F);
               // свечение вокруг куба
               ParticleShapes.softDot(bb, mat, (float)(p.x - cam.x), (float)(p.y - cam.y) + s, (float)(p.z - cam.z), right, up, s * 4.5F, p.rgb, 0.3F * (1.0F - t));
            }
         } else {
            p.vy -= 13.0 * (double)dt;
            p.x += p.vx * (double)dt;
            p.y += p.vy * (double)dt;
            p.z += p.vz * (double)dt;
            if (p.y < p.ground) {
               p.y = p.ground;
               p.vy = -p.vy * 0.35;
               p.vx *= 0.6;
               p.vz *= 0.6;
            }

            float a = 1.0F - t;
            float x = (float)(p.x - cam.x);
            float y = (float)(p.y - cam.y);
            float z = (float)(p.z - cam.z);
            ParticleShapes.softDot(bb, mat, x, y, z, right, up, p.size * 3.0F, p.rgb, a * 0.45F);
            ParticleShapes.softDot(bb, mat, x, y, z, right, up, p.size, 0xFFFFFF, a);
         }
      }

      for (Burst b : this.bursts) {
         float t = b.age / b.life;
         if (t >= 1.0F) {
            continue;
         }

         float x = (float)(b.pos.x - cam.x);
         float y = (float)(b.pos.y - cam.y) + 0.03F;
         float z = (float)(b.pos.z - cam.z);
         if (b.kind.equals("Кольцо")) {
            // два кольца друг за другом расходятся по земле
            for (int k = 0; k < 2; ++k) {
               float tt = MathHelper.clamp(t * 1.25F - (float)k * 0.25F, 0.0F, 1.0F);
               if (tt <= 0.0F || tt >= 1.0F) {
                  continue;
               }

               float ease = 1.0F - (1.0F - tt) * (1.0F - tt);
               float outer = (0.3F + 2.1F * ease) * b.scale;
               float inner = outer - (0.28F - 0.2F * tt) * b.scale;
               this.ring(bb, mat, x, y, z, inner, outer, b.phase, (1.0F - tt) * (k == 0 ? 0.9F : 0.5F));
            }
         } else if (b.kind.equals("Столб света")) {
            float radius = (0.5F + 0.25F * t) * b.scale;
            float height = (1.2F + 2.6F * (1.0F - (1.0F - t) * (1.0F - t))) * b.scale;
            float a = (1.0F - t) * 0.75F;
            int segments = 28;

            for (int i = 0; i < segments; ++i) {
               float a0 = (float)i / (float)segments * 6.2832F;
               float a1 = (float)(i + 1) / (float)segments * 6.2832F;
               int rgb = this.pick(b.phase + (float)i / (float)segments);
               int bottom = ParticleShapes.withAlpha(rgb, a);
               int top = ParticleShapes.withAlpha(rgb, 0.0F);
               float x0 = x + MathHelper.cos(a0) * radius;
               float z0 = z + MathHelper.sin(a0) * radius;
               float x1 = x + MathHelper.cos(a1) * radius;
               float z1 = z + MathHelper.sin(a1) * radius;
               bb.vertex(mat, x0, y, z0).color(bottom);
               bb.vertex(mat, x1, y, z1).color(bottom);
               bb.vertex(mat, x1, y + height, z1).color(top);
               bb.vertex(mat, x0, y, z0).color(bottom);
               bb.vertex(mat, x1, y + height, z1).color(top);
               bb.vertex(mat, x0, y + height, z0).color(top);
            }

            this.ring(bb, mat, x, y, z, radius * 0.75F, radius * 1.1F, b.phase, a);
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.blendFunc(770, 771);
      RenderSystem.disableBlend();
      // искажение — последним, оно гнёт уже готовую картинку
      Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

      for (int i = this.bursts.size() - 1; i >= 0; --i) {
         Burst b = this.bursts.get(i);
         b.age += dt;
         if (b.age >= b.life) {
            this.bursts.remove(i);
            continue;
         }

         if (!b.wave) {
            continue;
         }

         Vector4f clip = new Vector4f((float)(b.pos.x - cam.x), (float)(b.pos.y - cam.y), (float)(b.pos.z - cam.z), 1.0F);
         mat.transform(clip);
         projection.transform(clip);
         if (clip.w <= 0.3F) {
            continue;
         }

         float t = b.age / b.life;
         float perBlock = projection.m11() / (2.0F * clip.w);
         // размер и сила ограничены в долях экрана: под ногами точка совсем близко к камере
         float radius = Math.min(0.42F, (0.2F + 2.6F * (1.0F - (1.0F - t) * (1.0F - t))) * b.scale * perBlock);
         float width = Math.min(0.05F, 0.4F * b.scale * perBlock);
         float strength = Math.min(0.022F, 0.1F * perBlock) * this.distortPower.getCurrent() * (1.0F - t) * (1.0F - t);
         DrawUtil.drawDistortion(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F, radius, width, strength, 0.0F);
      }
   }

   /** Плоское кольцо на земле с мягкими краями. */
   private void ring(BufferBuilder bb, Matrix4f mat, float x, float y, float z, float inner, float outer, float phase, float alpha) {
      int segments = 40;
      float mid = (inner + outer) * 0.5F;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2832F;
         float a1 = (float)(i + 1) / (float)segments * 6.2832F;
         int rgb = this.pick(phase + (float)i / (float)segments);
         int bright = ParticleShapes.withAlpha(rgb, alpha);
         int clear = ParticleShapes.withAlpha(rgb, 0.0F);
         float c0 = MathHelper.cos(a0);
         float s0 = MathHelper.sin(a0);
         float c1 = MathHelper.cos(a1);
         float s1 = MathHelper.sin(a1);

         // две полосы: от внутреннего края к середине и от середины к внешнему — середина самая яркая
         for (int half = 0; half < 2; ++half) {
            float r0 = half == 0 ? inner : mid;
            float r1 = half == 0 ? mid : outer;
            int col0 = half == 0 ? clear : bright;
            int col1 = half == 0 ? bright : clear;
            bb.vertex(mat, x + c0 * r0, y, z + s0 * r0).color(col0);
            bb.vertex(mat, x + c0 * r1, y, z + s0 * r1).color(col1);
            bb.vertex(mat, x + c1 * r1, y, z + s1 * r1).color(col1);
            bb.vertex(mat, x + c0 * r0, y, z + s0 * r0).color(col0);
            bb.vertex(mat, x + c1 * r1, y, z + s1 * r1).color(col1);
            bb.vertex(mat, x + c1 * r0, y, z + s1 * r0).color(col0);
         }
      }
   }

   private static final class Burst {
      Vec3d pos;
      String kind = "";
      float age;
      float life;
      float scale;
      float phase;
      boolean wave;
   }

   private static final class Particle {
      int kind;
      int rgb;
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      double ground;
      float size;
      float age;
      float life;
   }
}
