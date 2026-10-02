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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Своя погода без ванильных частиц: частицы считаются и рисуются модулем.
 * Дождь — вытянутые струи с кругами брызг на земле, снег — мягкие хлопья с покачиванием,
 * листопад — листья-полигоны, которые кувыркаются, планируют и остаются лежать на земле.
 */
@ModuleAnnotation(
   name = "Weather",
   category = Category.RENDER,
   description = "Реалистичные снег, дождь и осенний листопад (только у тебя)"
)
public final class Weather extends Module {
   public static final Weather INSTANCE = new Weather();
   private static final int SNOW = 0;
   private static final int RAIN = 1;
   private static final int LEAF = 2;
   private static final int SPLASH = 3;
   private static final int[][] AUTUMN = new int[][]{{214, 96, 28}, {231, 146, 35}, {201, 54, 35}, {244, 196, 48}, {150, 84, 40}, {176, 128, 44}, {188, 70, 30}};
   private static final int[][] SAKURA = new int[][]{{255, 183, 213}, {255, 160, 200}, {250, 205, 225}, {240, 140, 185}};
   // контур листа: (позиция вдоль, полуширина)
   private static final float[][] LEAF_SHAPE = new float[][]{{0.0F, 0.0F}, {0.12F, 0.2F}, {0.3F, 0.36F}, {0.5F, 0.4F}, {0.7F, 0.3F}, {0.88F, 0.14F}, {1.0F, 0.0F}};
   private final ModeSetting type = new ModeSetting("Погода", "Снег", "Метель", "Дождь", "Листопад");
   private final ModeSetting leaves = new ModeSetting("Листья", () -> this.type.is("Листопад"), "Осенние", "Сакура");
   private final NumberSetting density = new NumberSetting("Плотность", 5.0F, 1.0F, 10.0F, 0.5F);
   private final NumberSetting radius = new NumberSetting("Радиус", 18.0F, 8.0F, 32.0F, 1.0F);
   private final NumberSetting wind = new NumberSetting("Ветер", 0.35F, 0.0F, 1.0F, 0.05F);
   private final BooleanSetting underRoof = new BooleanSetting("И под крышей", false);
   private final BooleanSetting darkSky = new BooleanSetting("Пасмурное небо", false);
   private final List<Particle> particles = new ArrayList<>();
   private long lastFrame;
   private boolean skyChanged;
   private String lastType = "";

   private Weather() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.particles.clear();
      if (this.skyChanged && mc.world != null) {
         mc.world.setRainGradient(0.0F);
         this.skyChanged = false;
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.world == null) {
         return;
      }

      if (this.darkSky.isEnabled()) {
         mc.world.setRainGradient(1.0F);
         this.skyChanged = true;
      } else if (this.skyChanged) {
         mc.world.setRainGradient(0.0F);
         this.skyChanged = false;
      }
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.05F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      if (mc.isPaused()) {
         dt = 0.0F;
      }

      if (!this.type.get().equals(this.lastType)) {
         this.lastType = this.type.get();
         this.particles.clear();
      }

      Camera cam = mc.gameRenderer.getCamera();
      Vec3d camPos = cam.getPos();
      int kind = this.type.is("Дождь") ? RAIN : (this.type.is("Листопад") ? LEAF : SNOW);
      boolean blizzard = this.type.is("Метель");
      float r = this.radius.getCurrent();
      float d = this.density.getCurrent();
      int target = (int)(d * (kind == RAIN ? 170.0F : (kind == LEAF ? 26.0F : (blizzard ? 260.0F : 150.0F))) * (r / 18.0F) * (r / 18.0F));
      double time = (double)now / 1000.0;
      // ветер плавно гуляет по направлению и силе
      double windAngle = time * 0.07 + Math.sin(time * 0.31) * 0.6;
      double gust = 0.7 + 0.3 * Math.sin(time * 0.9) + 0.15 * Math.sin(time * 2.3);
      double windPower = (double)this.wind.getCurrent() * gust * (blizzard ? 7.0 : (kind == RAIN ? 4.5 : (kind == LEAF ? 2.2 : 1.4)));
      double wx = Math.cos(windAngle) * windPower;
      double wz = Math.sin(windAngle) * windPower;
      int main = 0;

      for (Particle p : this.particles) {
         if (p.kind != SPLASH) {
            ++main;
         }
      }

      for (int i = main; i < target; ++i) {
         Particle p = new Particle();
         p.kind = kind;
         if (this.spawn(p, camPos, r, true, blizzard)) {
            this.particles.add(p);
         }
      }

      List<Particle> splashes = new ArrayList<>();

      for (int i = this.particles.size() - 1; i >= 0; --i) {
         Particle p = this.particles.get(i);
         boolean alive = this.update(p, dt, time, wx, wz, camPos, r, blizzard, splashes);
         if (!alive) {
            if (p.kind == SPLASH || main > target) {
               this.particles.remove(i);
               if (p.kind != SPLASH) {
                  --main;
               }
            } else if (!this.spawn(p, camPos, r, false, blizzard)) {
               this.particles.remove(i);
               --main;
            }
         }
      }

      this.particles.addAll(splashes);
      this.draw(event, cam, camPos, r, time);
   }

   /** Ставит частицу в цилиндр вокруг камеры. false — место не подошло (под крышей и т.п.). */
   private boolean spawn(Particle p, Vec3d cam, float r, boolean anywhere, boolean blizzard) {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      double angle = rnd.nextDouble(Math.PI * 2.0);
      double dist = Math.sqrt(rnd.nextDouble()) * (double)r;
      p.x = cam.x + Math.cos(angle) * dist;
      p.z = cam.z + Math.sin(angle) * dist;
      p.ground = (float)mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(p.x), MathHelper.floor(p.z));
      double top = cam.y + (p.kind == RAIN ? 14.0 : 11.0);
      // первый залп — по всей высоте, дальше частицы приходят сверху
      p.y = anywhere ? cam.y + rnd.nextDouble(-5.0, p.kind == RAIN ? 14.0 : 11.0) : top + rnd.nextDouble(0.0, 3.0);
      if (this.underRoof.isEnabled()) {
         p.ground = (float)Math.min((double)p.ground, cam.y - 2.5);
      }

      if (p.y <= (double)p.ground + 0.3) {
         if ((double)p.ground > top) {
            return false;
         }

         p.y = Math.max(p.y, (double)p.ground + rnd.nextDouble(1.0, 6.0));
      }

      p.landed = false;
      p.age = 0.0F;
      p.phase = rnd.nextFloat() * 6.2832F;
      p.phase2 = rnd.nextFloat() * 6.2832F;
      if (p.kind == RAIN) {
         p.speed = rnd.nextFloat() * 7.0F + 21.0F;
         p.size = rnd.nextFloat() * 0.5F + 0.55F;
         p.alpha = rnd.nextFloat() * 0.3F + 0.35F;
      } else if (p.kind == LEAF) {
         int[][] palette = this.leaves.is("Сакура") ? SAKURA : AUTUMN;
         int[] c = palette[rnd.nextInt(palette.length)];
         float tone = rnd.nextFloat() * 0.25F + 0.85F;
         p.red = Math.min(255.0F, (float)c[0] * tone);
         p.green = Math.min(255.0F, (float)c[1] * tone);
         p.blue = Math.min(255.0F, (float)c[2] * tone);
         p.speed = rnd.nextFloat() * 0.6F + 0.75F;
         p.size = this.leaves.is("Сакура") ? rnd.nextFloat() * 0.05F + 0.07F : rnd.nextFloat() * 0.11F + 0.13F;
         p.spinA = rnd.nextFloat() * 4.0F - 2.0F;
         p.spinB = rnd.nextFloat() * 5.0F + 2.0F;
         p.alpha = 1.0F;
      } else {
         // хлопья разного размера: мелких много, крупных мало
         float s = rnd.nextFloat();
         p.size = 0.025F + s * s * 0.075F;
         p.speed = (blizzard ? 2.2F : 0.85F) + rnd.nextFloat() * 0.7F + p.size * 5.0F;
         p.alpha = rnd.nextFloat() * 0.35F + 0.55F;
      }

      return true;
   }

   private boolean update(Particle p, float dt, double time, double wx, double wz, Vec3d cam, float r, boolean blizzard, List<Particle> splashes) {
      p.age += dt;
      if (p.kind == SPLASH) {
         return p.age < 0.32F;
      }

      double dx = p.x - cam.x;
      double dz = p.z - cam.z;
      if (dx * dx + dz * dz > (double)((r + 3.0F) * (r + 3.0F)) || p.y < cam.y - 24.0) {
         return false;
      }

      if (p.landed) {
         return p.age < (p.kind == LEAF ? 6.0F : 1.6F);
      }

      if (p.kind == RAIN) {
         p.vx = wx;
         p.vz = wz;
         p.vy = (double)(-p.speed);
      } else if (p.kind == LEAF) {
         // планирование: лист качается маятником и то тормозит, то проваливается
         double sway = Math.sin(time * 1.7 + (double)p.phase);
         double sway2 = Math.cos(time * 1.1 + (double)p.phase2);
         p.vx = wx + sway * 1.3;
         p.vz = wz + sway2 * 1.1;
         p.vy = (double)(-p.speed) * (0.55 + 0.45 * Math.abs(Math.cos(time * 1.7 + (double)p.phase)));
         p.rotA += p.spinA * dt * (float)(0.6 + Math.abs(sway));
         p.rotB += p.spinB * dt * 0.5F;
      } else {
         double turb = blizzard ? 1.6 : 0.45;
         p.vx = wx + Math.sin(time * 0.9 + (double)p.phase) * turb;
         p.vz = wz + Math.cos(time * 0.7 + (double)p.phase2) * turb;
         p.vy = (double)(-p.speed) + Math.sin(time * 1.3 + (double)p.phase) * 0.15;
      }

      p.x += p.vx * (double)dt;
      p.y += p.vy * (double)dt;
      p.z += p.vz * (double)dt;
      if (p.y <= (double)p.ground + 0.02) {
         p.y = (double)p.ground + 0.02;
         if (p.kind == RAIN) {
            if (ThreadLocalRandom.current().nextFloat() < 0.6F) {
               Particle s = new Particle();
               s.kind = SPLASH;
               s.x = p.x;
               s.y = p.y + 0.01;
               s.z = p.z;
               s.size = ThreadLocalRandom.current().nextFloat() * 0.12F + 0.14F;
               s.alpha = p.alpha;
               splashes.add(s);
            }

            return false;
         }

         p.landed = true;
         p.age = 0.0F;
      }

      return true;
   }

   private void draw(EventRender3D event, Camera cam, Vec3d camPos, float r, double time) {
      if (this.particles.isEmpty()) {
         return;
      }

      float light = MathHelper.clamp(mc.world.getSkyBrightness(event.getPartialTicks()), 0.3F, 1.0F);
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
         float dist = (float)Math.sqrt((double)(x * x + y * y + z * z));
         float flat = (float)Math.sqrt((double)(x * x + z * z));
         // плавно гаснет у границы радиуса и прямо перед лицом
         float fade = MathHelper.clamp((r - flat) / (r * 0.3F), 0.0F, 1.0F) * MathHelper.clamp((dist - 0.4F) / 1.2F, 0.0F, 1.0F);
         if (fade <= 0.01F) {
            continue;
         }

         if (p.kind == RAIN) {
            this.drawRain(bb, mat, p, x, y, z, fade, light);
         } else if (p.kind == SPLASH) {
            this.drawSplash(bb, mat, p, x, y, z, fade, light);
         } else if (p.kind == LEAF) {
            this.drawLeaf(bb, mat, p, x, y, z, fade, light);
         } else {
            this.drawSnow(bb, mat, p, x, y, z, fade, light, right, up, dist);
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   /** Снежинка — мягкий кружок: яркий центр, прозрачный край. */
   private void drawSnow(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float fade, float light, Vector3f right, Vector3f up, float dist) {
      float a = p.alpha * fade;
      if (p.landed) {
         a *= 1.0F - p.age / 1.6F;
      }

      // дальние хлопья чуть крупнее на вид, чтобы не превращались в точки
      float s = p.size * (1.0F + Math.min(dist, 20.0F) * 0.035F);
      int c = (int)(235.0F * light + 20.0F);
      int center = color(c, c, Math.min(255, c + 8), a);
      int edge = color(c, c, Math.min(255, c + 8), 0.0F);
      int segments = 7;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2832F;
         float a1 = (float)(i + 1) / (float)segments * 6.2832F;
         float c0 = MathHelper.cos(a0) * s;
         float s0 = MathHelper.sin(a0) * s;
         float c1 = MathHelper.cos(a1) * s;
         float s1 = MathHelper.sin(a1) * s;
         bb.vertex(mat, x, y, z).color(center);
         bb.vertex(mat, x + right.x * c0 + up.x * s0, y + right.y * c0 + up.y * s0, z + right.z * c0 + up.z * s0).color(edge);
         bb.vertex(mat, x + right.x * c1 + up.x * s1, y + right.y * c1 + up.y * s1, z + right.z * c1 + up.z * s1).color(edge);
      }
   }

   /** Капля — тонкая струя вдоль скорости: хвост прозрачный, голова светлая. */
   private void drawRain(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float fade, float light) {
      float speed = (float)Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
      if (speed < 0.01F) {
         return;
      }

      float len = p.size * (0.6F + speed * 0.02F);
      float dx = (float)(p.vx / (double)speed);
      float dy = (float)(p.vy / (double)speed);
      float dz = (float)(p.vz / (double)speed);
      // ширина — поперёк струи и поперёк взгляда, чтобы струя всегда была видна ребром
      float sx = dy * z - dz * y;
      float sy = dz * x - dx * z;
      float sz = dx * y - dy * x;
      float sl = (float)Math.sqrt((double)(sx * sx + sy * sy + sz * sz));
      if (sl < 1.0E-4F) {
         return;
      }

      float w = 0.011F;
      sx = sx / sl * w;
      sy = sy / sl * w;
      sz = sz / sl * w;
      float tx = x - dx * len;
      float ty = y - dy * len;
      float tz = z - dz * len;
      int r = (int)(175.0F * light + 20.0F);
      int g = (int)(195.0F * light + 20.0F);
      int b = (int)(225.0F * light + 25.0F);
      int head = color(r, g, b, p.alpha * fade);
      int tail = color(r, g, b, 0.0F);
      bb.vertex(mat, tx - sx, ty - sy, tz - sz).color(tail);
      bb.vertex(mat, tx + sx, ty + sy, tz + sz).color(tail);
      bb.vertex(mat, x + sx, y + sy, z + sz).color(head);
      bb.vertex(mat, tx - sx, ty - sy, tz - sz).color(tail);
      bb.vertex(mat, x + sx, y + sy, z + sz).color(head);
      bb.vertex(mat, x - sx, y - sy, z - sz).color(head);
   }

   /** Брызги — кольцо на земле, которое расходится и гаснет. */
   private void drawSplash(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float fade, float light) {
      float t = p.age / 0.32F;
      float outer = p.size * (0.25F + 0.75F * t);
      float inner = outer * 0.72F;
      float a = p.alpha * fade * (1.0F - t) * 0.9F;
      int r = (int)(190.0F * light + 20.0F);
      int c = color(r, r + 8, Math.min(255, r + 30), a);
      int clear = color(r, r + 8, Math.min(255, r + 30), 0.0F);
      int segments = 8;

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

   /** Лист — заострённый полигон с тёмной жилкой; в полёте кувыркается, на земле лежит плашмя. */
   private void drawLeaf(BufferBuilder bb, Matrix4f mat, Particle p, float x, float y, float z, float fade, float light) {
      float a = fade;
      if (p.landed) {
         a *= MathHelper.clamp((6.0F - p.age) / 1.5F, 0.0F, 1.0F);
      }

      Quaternionf q = p.landed ? new Quaternionf().rotationY(p.rotB) : new Quaternionf().rotationYXZ(p.rotB, p.rotA, p.rotA * 0.6F);
      Vector3f along = new Vector3f(1.0F, 0.0F, 0.0F).rotate(q).mul(p.size);
      Vector3f across = new Vector3f(0.0F, 0.0F, 1.0F).rotate(q).mul(p.size);
      Vector3f n = new Vector3f(0.0F, 1.0F, 0.0F).rotate(q);
      // свет сверху: повёрнутый ребром лист темнее — так видно, что он вертится
      float shade = (0.55F + 0.45F * Math.abs(n.y * 0.9F + n.x * 0.3F)) * light;
      int body = color((int)(p.red * shade), (int)(p.green * shade), (int)(p.blue * shade), a);
      int rim = color((int)(p.red * shade * 0.78F), (int)(p.green * shade * 0.74F), (int)(p.blue * shade * 0.7F), a);
      int vein = color((int)(p.red * shade * 0.55F), (int)(p.green * shade * 0.5F), (int)(p.blue * shade * 0.45F), a);
      float ox = x - along.x * 0.5F;
      float oy = y - along.y * 0.5F;
      float oz = z - along.z * 0.5F;

      for (int side = -1; side <= 1; side += 2) {
         for (int i = 0; i < LEAF_SHAPE.length - 1; ++i) {
            float t0 = LEAF_SHAPE[i][0];
            float w0 = LEAF_SHAPE[i][1] * (float)side;
            float t1 = LEAF_SHAPE[i + 1][0];
            float w1 = LEAF_SHAPE[i + 1][1] * (float)side;
            // полоса от жилки к краю
            bb.vertex(mat, ox + along.x * t0, oy + along.y * t0, oz + along.z * t0).color(body);
            bb.vertex(mat, ox + along.x * t0 + across.x * w0, oy + along.y * t0 + across.y * w0, oz + along.z * t0 + across.z * w0).color(rim);
            bb.vertex(mat, ox + along.x * t1 + across.x * w1, oy + along.y * t1 + across.y * w1, oz + along.z * t1 + across.z * w1).color(rim);
            bb.vertex(mat, ox + along.x * t0, oy + along.y * t0, oz + along.z * t0).color(body);
            bb.vertex(mat, ox + along.x * t1 + across.x * w1, oy + along.y * t1 + across.y * w1, oz + along.z * t1 + across.z * w1).color(rim);
            bb.vertex(mat, ox + along.x * t1, oy + along.y * t1, oz + along.z * t1).color(body);
         }
      }

      // жилка и черешок
      float vw = 0.035F;
      float lift = 0.004F;
      bb.vertex(mat, ox - along.x * 0.18F + n.x * lift, oy - along.y * 0.18F + n.y * lift, oz - along.z * 0.18F + n.z * lift).color(vein);
      bb.vertex(mat, ox + along.x * 0.5F + across.x * vw + n.x * lift, oy + along.y * 0.5F + across.y * vw + n.y * lift, oz + along.z * 0.5F + across.z * vw + n.z * lift).color(vein);
      bb.vertex(mat, ox + along.x * 0.5F - across.x * vw + n.x * lift, oy + along.y * 0.5F - across.y * vw + n.y * lift, oz + along.z * 0.5F - across.z * vw + n.z * lift).color(vein);
      bb.vertex(mat, ox + along.x * 0.5F + across.x * vw + n.x * lift, oy + along.y * 0.5F + across.y * vw + n.y * lift, oz + along.z * 0.5F + across.z * vw + n.z * lift).color(vein);
      bb.vertex(mat, ox + along.x * 0.95F + n.x * lift, oy + along.y * 0.95F + n.y * lift, oz + along.z * 0.95F + n.z * lift).color(vein);
      bb.vertex(mat, ox + along.x * 0.5F - across.x * vw + n.x * lift, oy + along.y * 0.5F - across.y * vw + n.y * lift, oz + along.z * 0.5F - across.z * vw + n.z * lift).color(vein);
   }

   private static int color(int r, int g, int b, float alpha) {
      int a = MathHelper.clamp((int)(alpha * 255.0F), 0, 255);
      return a << 24 | MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(b, 0, 255);
   }

   private static final class Particle {
      int kind;
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      float ground;
      float size;
      float speed;
      float alpha;
      float age;
      float phase;
      float phase2;
      float rotA;
      float rotB;
      float spinA;
      float spinB;
      float red;
      float green;
      float blue;
      boolean landed;
   }
}
