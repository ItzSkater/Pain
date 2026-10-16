package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.pain.Pain;
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
 * Ленточный шлейф. Позиции пишутся каждый кадр (не раз в тик), поэтому лента гладкая при любом fps.
 * Точка живёт заданное время: при остановке хвост догорает, а не обрывается. Лента всегда развёрнута
 * к камере и сужается к хвосту; у игроков есть ещё вертикальная «стена» во весь рост.
 */
@ModuleAnnotation(
   name = "Trails",
   category = Category.RENDER,
   description = "Лента-шлейф за тобой, игроками и снарядами: сужается к хвосту, цвет от скорости"
)
public final class Trails extends Module {
   public static final Trails INSTANCE = new Trails();
   private static final long START = System.nanoTime();
   private final ModeSetting style = new ModeSetting("Вид", "Лента", "Стена", "Искры");
   private final NumberSetting life = new NumberSetting("Длительность (сек)", 1.0F, 0.2F, 4.0F, 0.1F);
   private final NumberSetting width = new NumberSetting("Ширина", 0.12F, 0.03F, 0.5F, 0.01F);
   private final NumberSetting wallHeight = new NumberSetting("Высота стены", 1.0F, 0.1F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.style.is("Стена"));
   private final NumberSetting opacity = new NumberSetting("Яркость", 0.7F, 0.1F, 1.0F, 0.05F);
   private final ModeSetting colorMode = new ModeSetting("Цвет", "От скорости", "Тема", "Радуга", "Свой");
   private final ColorSetting color = new ColorSetting("Свой цвет", new ColorRGBA(120, 200, 255), () -> this.colorMode.is("Свой"));
   private final BooleanSetting self = new BooleanSetting("За собой", true);
   private final BooleanSetting others = new BooleanSetting("За игроками", false);
   private final BooleanSetting projectiles = new BooleanSetting("За снарядами", true);
   private final Map<Integer, Trail> trails = new HashMap<>();
   private float[] px = new float[64];
   private float[] py = new float[64];
   private float[] pz = new float[64];
   private float[] pa = new float[64];
   private int[] pc = new int[64];

   private Trails() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.trails.clear();
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         this.trails.clear();
         return;
      }

      double now = (double)(System.nanoTime() - START) / 1.0E9;
      float pt = event.getPartialTicks();
      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      this.sample(now, pt);
      if (this.trails.isEmpty()) {
         return;
      }

      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      double maxAge = (double)this.life.getCurrent();

      for (Trail t : this.trails.values()) {
         int n = this.collect(t, now, maxAge, cam);
         if (n < 2) {
            continue;
         }

         if (this.style.is("Стена") && !t.projectile) {
            this.wall(bb, mat, n, t.height * this.wallHeight.getCurrent());
         } else if (this.style.is("Искры") && !t.projectile) {
            this.sparks(bb, mat, n, now, t.height);
         } else {
            this.ribbon(bb, mat, n, t.projectile ? this.width.getCurrent() * 0.6F : this.width.getCurrent());
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

   private boolean wants(Entity e) {
      if (e == mc.player) {
         return this.self.isEnabled();
      } else if (e instanceof PlayerEntity p) {
         return this.others.isEnabled() && !p.isInvisible() && !p.isSpectator();
      } else {
         return this.projectiles.isEnabled() && e instanceof ProjectileEntity && !(e instanceof FishingBobberEntity);
      }
   }

   private void sample(double now, float pt) {
      for (Trail t : this.trails.values()) {
         t.alive = false;
      }

      for (Entity e : mc.world.getEntities()) {
         if (!this.wants(e) || mc.player.squaredDistanceTo(e) > 128.0 * 128.0) {
            continue;
         }

         Trail t = this.trails.computeIfAbsent(e.getId(), id -> new Trail());
         t.alive = true;
         t.projectile = !(e instanceof PlayerEntity);
         t.height = e.getHeight();
         double x = MathHelper.lerp((double)pt, e.lastRenderX, e.getX());
         double y = MathHelper.lerp((double)pt, e.lastRenderY, e.getY()) + (t.projectile ? (double)e.getHeight() * 0.5 : 0.0);
         double z = MathHelper.lerp((double)pt, e.lastRenderZ, e.getZ());
         t.hx = x;
         t.hy = y;
         t.hz = z;
         double[] last = t.points.peekLast();
         double dx = last == null ? 1.0 : x - last[0];
         double dy = last == null ? 1.0 : y - last[1];
         double dz = last == null ? 1.0 : z - last[2];
         double d2 = dx * dx + dy * dy + dz * dz;
         if (d2 > 100.0) {
            // телепорт или респавн: старый хвост не тянем через полкарты
            t.points.clear();
         }

         if (last == null || d2 > 0.0064) {
            double speed = last == null ? 0.0 : Math.sqrt(d2) / Math.max(0.004, now - last[3]);
            double smooth = last == null ? speed : last[4] + (speed - last[4]) * 0.35;
            t.points.addLast(new double[]{x, y, z, now, smooth, ThreadLocalRandom.current().nextDouble()});
            if (t.points.size() > 700) {
               t.points.pollFirst();
            }
         }
      }

      Iterator<Trail> it = this.trails.values().iterator();
      double maxAge = (double)this.life.getCurrent();

      while (it.hasNext()) {
         Trail t = it.next();
         double now2 = (double)(System.nanoTime() - START) / 1.0E9;

         while (!t.points.isEmpty() && now2 - t.points.peekFirst()[3] > maxAge) {
            t.points.pollFirst();
         }

         if (t.points.isEmpty() && !t.alive) {
            it.remove();
         }
      }
   }

   /** Переводит точки в координаты от камеры, считает цвет и яркость каждой. Возвращает число точек. */
   private int collect(Trail t, double now, double maxAge, Vec3d cam) {
      int n = t.points.size() + (t.alive ? 1 : 0);
      if (this.px.length < n) {
         int size = n + 64;
         this.px = new float[size];
         this.py = new float[size];
         this.pz = new float[size];
         this.pa = new float[size];
         this.pc = new int[size];
      }

      int i = 0;
      double lastSpeed = 0.0;

      for (double[] p : t.points) {
         this.put(i++, p[0], p[1], p[2], 1.0 - (now - p[3]) / maxAge, p[4], now, cam, p[5]);
         lastSpeed = p[4];
      }

      if (t.alive) {
         this.put(i++, t.hx, t.hy, t.hz, 1.0, lastSpeed, now, cam, 0.0);
      }

      return i;
   }

   private void put(int i, double x, double y, double z, double fade, double speed, double now, Vec3d cam, double seed) {
      this.px[i] = (float)(x - cam.x);
      this.py[i] = (float)(y - cam.y);
      this.pz[i] = (float)(z - cam.z);
      float f = (float)MathHelper.clamp(fade, 0.0, 1.0);
      // у самой камеры лента гаснет, иначе от первого лица она лезет в глаза
      float near = MathHelper.clamp(((float)Math.sqrt((double)(this.px[i] * this.px[i] + this.pz[i] * this.pz[i])) - 0.35F) / 0.9F, 0.0F, 1.0F);
      this.pa[i] = this.opacity.getCurrent() * f * near;
      this.pc[i] = this.colorFor(speed, (float)(1.0 - fade) * 0.6F + (float)now * 0.15F, (float)seed);
   }

   private int colorFor(double speed, float phase, float seed) {
      if (this.colorMode.is("От скорости")) {
         // медленно — холодный синий, быстро — горячий красный
         float k = MathHelper.clamp((float)speed / 14.0F, 0.0F, 1.0F);
         return ParticleShapes.rainbow(0.62F - 0.62F * k, 0.75F, 1.0F);
      } else if (this.colorMode.is("Радуга")) {
         return ParticleShapes.rainbow(phase, 0.7F, 1.0F);
      } else if (this.colorMode.is("Свой")) {
         return this.color.getColor().getRGB() & 0xFFFFFF;
      }

      return Pain.getInstance().getThemeManager().getClientColor((int)(phase * 360.0F)).getRGB() & 0xFFFFFF;
   }

   /** Лента, развёрнутая к камере: широкое мягкое свечение и яркая сердцевина, к хвосту уже. */
   private void ribbon(BufferBuilder bb, Matrix4f mat, int n, float w) {
      float lsx = 0.0F;
      float lsy = 1.0F;
      float lsz = 0.0F;
      float[] side = new float[n * 3];

      for (int i = 0; i < n; ++i) {
         int a = Math.max(0, i - 1);
         int b = Math.min(n - 1, i + 1);
         float dx = this.px[b] - this.px[a];
         float dy = this.py[b] - this.py[a];
         float dz = this.pz[b] - this.pz[a];
         // поперёк ленты = направление × луч к камере
         float sx = dy * this.pz[i] - dz * this.py[i];
         float sy = dz * this.px[i] - dx * this.pz[i];
         float sz = dx * this.py[i] - dy * this.px[i];
         float len = (float)Math.sqrt((double)(sx * sx + sy * sy + sz * sz));
         if (len > 1.0E-6F) {
            lsx = sx / len;
            lsy = sy / len;
            lsz = sz / len;
         }

         float taper = 0.15F + 0.85F * (float)i / (float)(n - 1);
         side[i * 3] = lsx * w * taper;
         side[i * 3 + 1] = lsy * w * taper;
         side[i * 3 + 2] = lsz * w * taper;
      }

      for (int i = 0; i + 1 < n; ++i) {
         int j = i + 1;
         float a0 = this.pa[i];
         float a1 = this.pa[j];
         if (a0 <= 0.003F && a1 <= 0.003F) {
            continue;
         }

         int c0 = this.pc[i];
         int c1 = this.pc[j];
         // свечение: от центра (ярко) к краям (прозрачно), в три раза шире сердцевины
         for (int s = -1; s <= 1; s += 2) {
            float k = 1.6F * (float)s;
            this.quad(bb, mat,
               this.px[i], this.py[i], this.pz[i], ParticleShapes.withAlpha(c0, a0 * 0.45F),
               this.px[j], this.py[j], this.pz[j], ParticleShapes.withAlpha(c1, a1 * 0.45F),
               this.px[j] + side[j * 3] * k, this.py[j] + side[j * 3 + 1] * k, this.pz[j] + side[j * 3 + 2] * k, ParticleShapes.withAlpha(c1, 0.0F),
               this.px[i] + side[i * 3] * k, this.py[i] + side[i * 3 + 1] * k, this.pz[i] + side[i * 3 + 2] * k, ParticleShapes.withAlpha(c0, 0.0F));
         }

         float k = 0.35F;
         int w0 = ParticleShapes.withAlpha(mix(c0, 0xFFFFFF, 0.55F), a0);
         int w1 = ParticleShapes.withAlpha(mix(c1, 0xFFFFFF, 0.55F), a1);
         this.quad(bb, mat,
            this.px[i] - side[i * 3] * k, this.py[i] - side[i * 3 + 1] * k, this.pz[i] - side[i * 3 + 2] * k, w0,
            this.px[j] - side[j * 3] * k, this.py[j] - side[j * 3 + 1] * k, this.pz[j] - side[j * 3 + 2] * k, w1,
            this.px[j] + side[j * 3] * k, this.py[j] + side[j * 3 + 1] * k, this.pz[j] + side[j * 3 + 2] * k, w1,
            this.px[i] + side[i * 3] * k, this.py[i] + side[i * 3 + 1] * k, this.pz[i] + side[i * 3 + 2] * k, w0);
      }
   }

   /** Вертикальная полупрозрачная стена во весь рост: края ярче середины. */
   private void wall(BufferBuilder bb, Matrix4f mat, int n, float h) {
      float[] ys = new float[]{0.02F, h * 0.12F, h * 0.88F, h};
      float[] ks = new float[]{1.0F, 0.3F, 0.3F, 1.0F};

      for (int i = 0; i + 1 < n; ++i) {
         int j = i + 1;
         if (this.pa[i] <= 0.003F && this.pa[j] <= 0.003F) {
            continue;
         }

         for (int b = 0; b < 3; ++b) {
            this.quad(bb, mat,
               this.px[i], this.py[i] + ys[b], this.pz[i], ParticleShapes.withAlpha(this.pc[i], this.pa[i] * 0.6F * ks[b]),
               this.px[j], this.py[j] + ys[b], this.pz[j], ParticleShapes.withAlpha(this.pc[j], this.pa[j] * 0.6F * ks[b]),
               this.px[j], this.py[j] + ys[b + 1], this.pz[j], ParticleShapes.withAlpha(this.pc[j], this.pa[j] * 0.6F * ks[b + 1]),
               this.px[i], this.py[i] + ys[b + 1], this.pz[i], ParticleShapes.withAlpha(this.pc[i], this.pa[i] * 0.6F * ks[b + 1]));
         }
      }
   }

   /** Мерцающие искры вдоль пути, медленно всплывают. */
   private void sparks(BufferBuilder bb, Matrix4f mat, int n, double now, float height) {
      org.joml.Quaternionf rotation = mc.gameRenderer.getCamera().getRotation();
      org.joml.Vector3f right = new org.joml.Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
      org.joml.Vector3f up = new org.joml.Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
      for (int i = 0; i < n; ++i) {
         float seed = (float)((i * 0.618034) % 1.0);
         float age = 1.0F - this.pa[i] / Math.max(0.001F, this.opacity.getCurrent());
         float x = this.px[i] + (seed - 0.5F) * 0.5F;
         float y = this.py[i] + height * ((seed * 7.31F) % 1.0F) + age * 0.4F;
         float z = this.pz[i] + ((seed * 13.7F) % 1.0F - 0.5F) * 0.5F;
         float twinkle = 0.55F + 0.45F * (float)Math.sin(now * 10.0 + (double)(seed * 40.0F));
         float size = this.width.getCurrent() * (0.4F + seed) * (1.0F - age * 0.6F);
         ParticleShapes.softDot(bb, mat, x, y, z, right, up, size * 3.0F, this.pc[i], this.pa[i] * 0.35F * twinkle);
         ParticleShapes.softDot(bb, mat, x, y, z, right, up, size, 0xFFFFFF, this.pa[i] * twinkle);
      }
   }

   private void quad(BufferBuilder bb, Matrix4f m, float ax, float ay, float az, int ca, float bx, float by, float bz, int cb, float cx, float cy, float cz, int cc, float dx, float dy, float dz, int cd) {
      bb.vertex(m, ax, ay, az).color(ca);
      bb.vertex(m, bx, by, bz).color(cb);
      bb.vertex(m, cx, cy, cz).color(cc);
      bb.vertex(m, ax, ay, az).color(ca);
      bb.vertex(m, cx, cy, cz).color(cc);
      bb.vertex(m, dx, dy, dz).color(cd);
   }

   private static int mix(int a, int b, float k) {
      int r = (int)((float)(a >> 16 & 0xFF) * (1.0F - k) + (float)(b >> 16 & 0xFF) * k);
      int g = (int)((float)(a >> 8 & 0xFF) * (1.0F - k) + (float)(b >> 8 & 0xFF) * k);
      int bl = (int)((float)(a & 0xFF) * (1.0F - k) + (float)(b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }

   private static final class Trail {
      // x, y, z, время, сглаженная скорость, случайное число
      final ArrayDeque<double[]> points = new ArrayDeque<>();
      boolean alive;
      boolean projectile;
      float height;
      double hx;
      double hy;
      double hz;
   }
}
