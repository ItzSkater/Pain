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
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Эффект удара: из точки попадания вылетают частицы — конфетти, снежинки, разлетающиеся капли,
 * торнадо — или по экрану расходится волна искажения с центром в точке удара.
 * Частицы считаются и рисуются модулем, видны только тебе.
 */
@ModuleAnnotation(
   name = "HitEffect",
   category = Category.RENDER,
   description = "Эффект удара: конфетти, снежинки, капли, торнадо или волна искажения из точки попадания"
)
public final class HitEffect extends Module {
   public static final HitEffect INSTANCE = new HitEffect();
   private static final int CONFETTI = 0;
   private static final int SNOW = 1;
   private static final int DROP = 2;
   private static final int SWIRL = 3;
   private static final int SPLASH = 4;
   private static final int MAX = 1500;
   private final ModeSetting effect = new ModeSetting("Эффект", "Конфетти", "Снежинки", "Капли", "Вихрь", "Искажение");
   private final BooleanSetting distort = new BooleanSetting("Искажать картинку", true, () -> !this.effect.is("Искажение"));
   private final NumberSetting distortPower = new NumberSetting("Сила искажения", 1.0F, 0.2F, 3.0F, 0.1F);
   private final NumberSetting amount = new NumberSetting("Количество", 30.0F, 5.0F, 100.0F, 1.0F);
   private final NumberSetting size = new NumberSetting("Размер", 1.0F, 0.4F, 3.0F, 0.1F);
   private final NumberSetting life = new NumberSetting("Время жизни (сек)", 1.6F, 0.5F, 5.0F, 0.1F);
   private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Радуга", "Свой");
   private final ColorSetting color = new ColorSetting("Свой цвет", new ColorRGBA(120, 200, 255), () -> this.colorMode.is("Свой"));
   private final BooleanSetting onlyLiving = new BooleanSetting("Только по живым", true);
   private final List<Particle> particles = new ArrayList<>();
   private final List<Wave> waves = new ArrayList<>();
   private long lastFrame;

   private HitEffect() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.particles.clear();
      this.waves.clear();
   }

   private int pickColor(ThreadLocalRandom rnd) {
      if (this.colorMode.is("Радуга")) {
         return ParticleShapes.rainbow(rnd.nextFloat(), 0.75F, 1.0F);
      }

      if (this.colorMode.is("Свой")) {
         ColorRGBA c = this.color.getColor();
         float tone = 0.75F + rnd.nextFloat() * 0.25F;
         return ParticleShapes.argb((int)((float)c.getRed() * tone), (int)((float)c.getGreen() * tone), (int)((float)c.getBlue() * tone), 0.0F) & 0xFFFFFF;
      }

      return Pain.getInstance().getThemeManager().getClientColor(rnd.nextInt(360)).getRGB() & 0xFFFFFF;
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      Entity target = event.getTarget();
      if (mc.player == null || target == null || this.onlyLiving.isEnabled() && !(target instanceof LivingEntity)) {
         return;
      }

      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      boolean onlyDistort = this.effect.is("Искажение");
      int kind = this.effect.is("Снежинки") ? SNOW : (this.effect.is("Капли") ? DROP : (this.effect.is("Вихрь") ? SWIRL : CONFETTI));
      float scale = this.size.getCurrent();
      // точка удара: где луч от глаз к цели входит в её хитбокс
      Vec3d eye = mc.player.getEyePos();
      Vec3d aim = target.getBoundingBox().getCenter().add(0.0, (double)target.getHeight() * 0.1, 0.0);
      Vec3d hit = target.getBoundingBox().raycast(eye, aim).orElse(aim);
      double cx = hit.x;
      double cy = hit.y;
      double cz = hit.z;
      if (onlyDistort || this.distort.isEnabled()) {
         // новая волна заменяет прежнюю того же вида: волны накладываются друг на друга,
         // и при частых ударах картинка превращалась в кашу
         boolean newTwist = kind == SWIRL && !onlyDistort;
         this.waves.removeIf(old -> old.twist == newTwist);

         Wave w = new Wave();
         w.x = cx;
         w.y = kind == SWIRL && !onlyDistort ? target.getY() + (double)(1.2F * scale) : cy;
         w.z = cz;
         w.twist = kind == SWIRL && !onlyDistort;
         w.life = w.twist ? this.life.getCurrent() * 1.5F : 0.55F;
         w.scale = scale;
         this.waves.add(w);
      }

      if (onlyDistort) {
         return;
      }

      // торнадо состоит из множества мелких клочьев, остальным эффектам хватает меньшего
      int count = (int)this.amount.getCurrent() * (kind == SWIRL ? 3 : 1);

      for (int i = 0; i < count && this.particles.size() < MAX; ++i) {
         Particle p = new Particle();
         p.kind = kind;
         p.rgb = this.pickColor(rnd);
         p.ground = target.getY();
         p.life = this.life.getCurrent() * (0.7F + rnd.nextFloat() * 0.6F);
         // случайное направление по сфере
         double yaw = rnd.nextDouble(Math.PI * 2.0);
         double up = rnd.nextDouble(-0.3, 1.0);
         double flat = Math.sqrt(Math.max(0.0, 1.0 - up * up));
         double dx = Math.cos(yaw) * flat;
         double dz = Math.sin(yaw) * flat;
         p.x = cx + dx * 0.12;
         p.y = cy + up * 0.12;
         p.z = cz + dz * 0.12;
         p.rotA = rnd.nextFloat() * 6.2832F;
         p.rotB = rnd.nextFloat() * 6.2832F;
         p.spinA = rnd.nextFloat() * 14.0F - 7.0F;
         p.spinB = rnd.nextFloat() * 10.0F - 5.0F;
         if (kind == CONFETTI) {
            double speed = rnd.nextDouble(2.5, 6.0);
            p.vx = dx * speed;
            p.vy = up * speed + 2.5;
            p.vz = dz * speed;
            p.size = (0.05F + rnd.nextFloat() * 0.04F) * scale;
         } else if (kind == SNOW) {
            double speed = rnd.nextDouble(1.0, 2.6);
            p.vx = dx * speed;
            p.vy = up * speed + 1.0;
            p.vz = dz * speed;
            p.size = (0.07F + rnd.nextFloat() * 0.07F) * scale;
            p.spinA *= 0.25F;
         } else if (kind == DROP) {
            double speed = rnd.nextDouble(3.5, 8.0);
            p.vx = dx * speed;
            p.vy = up * speed + 2.0;
            p.vz = dz * speed;
            p.size = (0.035F + rnd.nextFloat() * 0.03F) * scale;
         } else {
            // торнадо: воронка стоит там, куда пришёлся удар; клочья поднимаются по ней и крутятся
            p.cx = cx;
            p.cy = target.getY();
            p.cz = cz;
            p.angle = (float)yaw;
            p.height = rnd.nextFloat();
            p.rise = 0.25F + rnd.nextFloat() * 0.35F;
            p.radius = 0.75F + rnd.nextFloat() * 0.5F;
            p.life = this.life.getCurrent() * 1.5F;
            p.size = (0.07F + rnd.nextFloat() * 0.1F) * scale;
            p.scale = scale;
            // дымка серая, часть клочьев подкрашена выбранным цветом
            if (rnd.nextFloat() < 0.6F) {
               int g = 120 + rnd.nextInt(80);
               p.rgb = g << 16 | g << 8 | g + 6;
            }

            p.x = cx;
            p.y = target.getY();
            p.z = cz;
         }

         this.particles.add(p);
      }
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null || this.particles.isEmpty() && this.waves.isEmpty()) {
         this.lastFrame = System.currentTimeMillis();
         return;
      }

      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.05F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      if (mc.isPaused()) {
         dt = 0.0F;
      }

      List<Particle> splashes = new ArrayList<>();

      for (int i = this.particles.size() - 1; i >= 0; --i) {
         if (!this.update(this.particles.get(i), dt, event.getPartialTicks(), splashes)) {
            this.particles.remove(i);
         }
      }

      this.particles.addAll(splashes);
      Camera cam = mc.gameRenderer.getCamera();
      Vec3d camPos = cam.getPos();
      if (this.particles.isEmpty()) {
         this.drawWaves(event, camPos, dt);
         return;
      }

      Quaternionf rotation = cam.getRotation();
      Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
      Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (Particle p : this.particles) {
         float x = (float)(p.x - camPos.x);
         float y = (float)(p.y - camPos.y);
         float z = (float)(p.z - camPos.z);
         float t = p.age / p.life;
         // быстро появляется, плавно гаснет в последней трети жизни
         float alpha = MathHelper.clamp(p.age / 0.08F, 0.0F, 1.0F) * MathHelper.clamp((1.0F - t) / 0.35F, 0.0F, 1.0F);
         if (alpha <= 0.01F) {
            continue;
         }

         if (p.kind == CONFETTI) {
            this.drawConfetti(bb, mat, p, x, y, z, alpha);
         } else if (p.kind == SNOW) {
            ParticleShapes.snowflake(bb, mat, x, y, z, right, up, p.size, p.rotA, p.rgb, alpha);
         } else if (p.kind == DROP) {
            this.drawDrop(bb, mat, p, x, y, z, alpha, right, up);
         } else if (p.kind == SPLASH) {
            this.drawSplash(bb, mat, p, x, y, z, alpha);
         } else {
            // клочья дыма: к верху воронки крупнее и прозрачнее
            float grow = 1.0F + p.height * 2.6F;
            ParticleShapes.softDot(bb, mat, x, y, z, right, up, p.size * grow * 1.8F, p.rgb, alpha * (0.32F - 0.17F * p.height));
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      // искажение — последним, чтобы оно гнуло и сами частицы
      this.drawWaves(event, camPos, dt);
   }

   /** Искажение кадра: точку удара переводим в координаты экрана и гнём картинку вокруг неё. */
   private void drawWaves(EventRender3D event, Vec3d camPos, float dt) {
      if (this.waves.isEmpty()) {
         return;
      }

      Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
      Matrix4f view = new Matrix4f(event.getMatrix().peek().getPositionMatrix());
      float power = this.distortPower.getCurrent();

      for (int i = this.waves.size() - 1; i >= 0; --i) {
         Wave w = this.waves.get(i);
         w.age += dt;
         if (w.age >= w.life) {
            this.waves.remove(i);
            continue;
         }

         org.joml.Vector4f clip = new org.joml.Vector4f((float)(w.x - camPos.x), (float)(w.y - camPos.y), (float)(w.z - camPos.z), 1.0F);
         view.transform(clip);
         projection.transform(clip);
         if (clip.w <= 0.3F) {
            continue;
         }

         float u = clip.x / clip.w * 0.5F + 0.5F;
         float v = clip.y / clip.w * 0.5F + 0.5F;
         // сколько долей высоты экрана занимает один блок на этой дистанции
         float perBlock = projection.m11() / (2.0F * clip.w);
         float t = w.age / w.life;
         if (w.twist) {
            // закручивание плавно набирает силу и отпускает
            float envelope = (float)Math.sin((double)t * Math.PI);
            // вблизи точка удара занимает пол-экрана, поэтому размер и сила ограничены в долях экрана
            float radius = Math.min(0.26F, 1.6F * w.scale * perBlock);
            tech.pain.utility.render.display.shader.DrawUtil.drawDistortion(u, v, radius, 0.0F, 0.0F, 0.55F * envelope * power);
         } else {
            float grow = 1.0F - (1.0F - t) * (1.0F - t);
            float radius = Math.min(0.42F, (0.15F + 2.4F * grow) * w.scale * perBlock);
            float width = Math.min(0.05F, 0.38F * w.scale * perBlock);
            float strength = Math.min(0.022F, 0.1F * perBlock) * power * (1.0F - t) * (1.0F - t);
            tech.pain.utility.render.display.shader.DrawUtil.drawDistortion(u, v, radius, width, strength, 0.0F);
         }
      }
   }

   private boolean update(Particle p, float dt, float partialTicks, List<Particle> splashes) {
      p.age += dt;
      if (p.age >= p.life) {
         return false;
      }

      if (p.kind == SPLASH) {
         return true;
      }

      if (p.kind == SWIRL) {
         p.height += p.rise * dt;
         if (p.height > 1.0F) {
            p.height -= 1.0F;
         }

         float h = p.height;
         // внизу узко и быстро, наверху широко и медленнее — как у настоящего смерча
         float r = (0.1F + 1.25F * (float)Math.pow((double)h, 1.6)) * p.radius * p.scale;
         p.angle += (11.0F / (0.35F + h * 1.4F)) * dt;
         // воронка изгибается и покачивается
         float bend = h * 0.45F * p.scale;
         double ox = Math.sin((double)(p.age * 1.9F + h * 2.4F)) * (double)bend;
         double oz = Math.cos((double)(p.age * 1.4F + h * 2.0F)) * (double)bend;
         p.x = p.cx + ox + (double)(MathHelper.cos(p.angle) * r);
         p.y = p.cy + (double)(h * 3.4F * p.scale);
         p.z = p.cz + oz + (double)(MathHelper.sin(p.angle) * r);
         return true;
      }

      if (p.landed) {
         return true;
      }

      float gravity = p.kind == CONFETTI ? 7.0F : (p.kind == SNOW ? 1.6F : 16.0F);
      float drag = p.kind == CONFETTI ? 2.2F : (p.kind == SNOW ? 1.8F : 0.4F);
      p.vy -= (double)(gravity * dt);
      double keep = Math.max(0.0, 1.0 - (double)(drag * dt));
      p.vx *= keep;
      p.vz *= keep;
      if (p.kind != DROP) {
         // конфетти и снежинки планируют: падение тормозится и появляется лёгкое покачивание
         p.vy = Math.max(p.vy, p.kind == SNOW ? -0.9 : -2.2);
         p.vx += Math.sin((double)(p.age * 5.0F + p.rotB)) * (double)dt * 1.2;
         p.vz += Math.cos((double)(p.age * 4.0F + p.rotB)) * (double)dt * 1.2;
      }

      p.x += p.vx * (double)dt;
      p.y += p.vy * (double)dt;
      p.z += p.vz * (double)dt;
      p.rotA += p.spinA * dt;
      p.rotB += p.spinB * dt;
      if (p.y <= p.ground + 0.02) {
         p.y = p.ground + 0.02;
         if (p.kind == DROP) {
            Particle s = new Particle();
            s.kind = SPLASH;
            s.x = p.x;
            s.y = p.y;
            s.z = p.z;
            s.rgb = p.rgb;
            s.size = p.size * 4.0F;
            s.life = 0.35F;
            splashes.add(s);
            return false;
         }

         p.landed = true;
         // на земле лежит недолго и гаснет
         p.life = Math.min(p.life, p.age + 0.8F);
      }

      return true;
   }

   /** Конфетти — прямоугольник, который кувыркается; ребром к свету он темнее. */
   private void drawConfetti(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float alpha) {
      Quaternionf q = p.landed ? new Quaternionf().rotationY(p.rotB) : new Quaternionf().rotationYXZ(p.rotB, p.rotA, p.rotA * 0.7F);
      Vector3f a = new Vector3f(1.0F, 0.0F, 0.0F).rotate(q).mul(p.size);
      Vector3f b = new Vector3f(0.0F, 0.0F, 1.0F).rotate(q).mul(p.size * 0.6F);
      Vector3f n = new Vector3f(0.0F, 1.0F, 0.0F).rotate(q);
      float shade = 0.6F + 0.4F * Math.abs(n.y);
      int c = ParticleShapes.argb((int)((float)(p.rgb >> 16 & 255) * shade), (int)((float)(p.rgb >> 8 & 255) * shade), (int)((float)(p.rgb & 255) * shade), alpha);
      bb.vertex(mat, x - a.x - b.x, y - a.y - b.y, z - a.z - b.z).color(c);
      bb.vertex(mat, x + a.x - b.x, y + a.y - b.y, z + a.z - b.z).color(c);
      bb.vertex(mat, x + a.x + b.x, y + a.y + b.y, z + a.z + b.z).color(c);
      bb.vertex(mat, x - a.x - b.x, y - a.y - b.y, z - a.z - b.z).color(c);
      bb.vertex(mat, x + a.x + b.x, y + a.y + b.y, z + a.z + b.z).color(c);
      bb.vertex(mat, x - a.x + b.x, y - a.y + b.y, z - a.z + b.z).color(c);
   }

   /** Капля — круглая голова и хвост вдоль скорости. */
   private void drawDrop(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float alpha, Vector3f right, Vector3f up) {
      ParticleShapes.softDot(bb, mat, x, y, z, right, up, p.size * 1.3F, p.rgb, alpha);
      float speed = (float)Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
      if (speed < 0.2F) {
         return;
      }

      float len = Math.min(0.5F, speed * 0.045F);
      float dx = (float)(p.vx / (double)speed);
      float dy = (float)(p.vy / (double)speed);
      float dz = (float)(p.vz / (double)speed);
      float sx = dy * z - dz * y;
      float sy = dz * x - dx * z;
      float sz = dx * y - dy * x;
      float sl = (float)Math.sqrt((double)(sx * sx + sy * sy + sz * sz));
      if (sl < 1.0E-4F) {
         return;
      }

      float w = p.size * 0.55F;
      sx = sx / sl * w;
      sy = sy / sl * w;
      sz = sz / sl * w;
      int head = ParticleShapes.withAlpha(p.rgb, alpha * 0.9F);
      int tail = ParticleShapes.withAlpha(p.rgb, 0.0F);
      bb.vertex(mat, x - dx * len, y - dy * len, z - dz * len).color(tail);
      bb.vertex(mat, x + sx, y + sy, z + sz).color(head);
      bb.vertex(mat, x - sx, y - sy, z - sz).color(head);
   }

   /** Брызги на земле — расходящееся кольцо. */
   private void drawSplash(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float alpha) {
      float t = p.age / p.life;
      float outer = p.size * (0.25F + 0.75F * t);
      float inner = outer * 0.7F;
      int c = ParticleShapes.withAlpha(p.rgb, alpha * 0.8F);
      int clear = ParticleShapes.withAlpha(p.rgb, 0.0F);
      int segments = 10;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2832F;
         float a1 = (float)(i + 1) / (float)segments * 6.2832F;
         float c0 = MathHelper.cos(a0);
         float s0 = MathHelper.sin(a0);
         float c1 = MathHelper.cos(a1);
         float s1 = MathHelper.sin(a1);
         bb.vertex(mat, x + c0 * inner, y, z + s0 * inner).color(clear);
         bb.vertex(mat, x + c0 * outer, y, z + s0 * outer).color(c);
         bb.vertex(mat, x + c1 * outer, y, z + s1 * outer).color(c);
         bb.vertex(mat, x + c0 * inner, y, z + s0 * inner).color(clear);
         bb.vertex(mat, x + c1 * outer, y, z + s1 * outer).color(c);
         bb.vertex(mat, x + c1 * inner, y, z + s1 * inner).color(clear);
      }
   }

   private static final class Wave {
      double x;
      double y;
      double z;
      float age;
      float life;
      float scale;
      boolean twist;
   }

   private static final class Particle {
      float scale = 1.0F;
      int kind;
      int rgb;
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      double cx;
      double cy;
      double cz;
      double ground;
      float size;
      float age;
      float life;
      float rotA;
      float rotB;
      float spinA;
      float spinB;
      float angle;
      float radius;
      float height;
      float rise;
      boolean landed;
      Entity owner;
   }
}
