package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventAttack;
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

/**
 * Эффект убийства. Срабатывает, когда умирает тот, кого ты бил последние секунды (или цель Aura).
 * Осколки — тело разваливается на кубики по частям (голова, торс, руки, ноги), они разлетаются
 * с физикой, отскакивают от земли и гаснут. Молния бьёт с неба в место смерти. Душа поднимается
 * светящимся шаром со спиралями. Дым — тёмный клуб, который закручивается вверх.
 */
@ModuleAnnotation(
   name = "KillEffect",
   category = Category.RENDER,
   description = "Эффект убийства: тело разлетается на кубики, молния, душа или дым"
)
public final class KillEffect extends Module {
   public static final KillEffect INSTANCE = new KillEffect();
   private final ModeSetting effect = new ModeSetting("Эффект", "Осколки", "Молния", "Душа", "Дым");
   private final NumberSetting size = new NumberSetting("Размер", 1.0F, 0.5F, 2.5F, 0.1F);
   private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Радуга", "Свой");
   private final ColorSetting color = new ColorSetting("Свой цвет", new ColorRGBA(255, 60, 80), () -> this.colorMode.is("Свой"));
   private final BooleanSetting distort = new BooleanSetting("Волна искажения", true);
   private final BooleanSetting sound = new BooleanSetting("Звук", true);
   private final NumberSetting volume = new NumberSetting("Громкость", 0.6F, 0.05F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.sound.isEnabled());
   private final BooleanSetting anyDeath = new BooleanSetting("Любая смерть рядом", false);
   // кого бил и когда; плюс последнее здоровье, чтобы отличить смерть от ухода из прорисовки
   private final Map<Integer, long[]> hits = new HashMap<>();
   private final Map<Integer, Float> health = new HashMap<>();
   private final List<Burst> bursts = new ArrayList<>();
   private final List<Piece> pieces = new ArrayList<>();
   private long lastFrame;

   private KillEffect() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.hits.clear();
      this.health.clear();
      this.bursts.clear();
      this.pieces.clear();
   }

   private int pick(float phase) {
      if (this.colorMode.is("Радуга")) {
         return ParticleShapes.rainbow(phase, 0.7F, 1.0F);
      } else if (this.colorMode.is("Свой")) {
         return this.color.getColor().getRGB() & 0xFFFFFF;
      }

      return Pain.getInstance().getThemeManager().getClientColor((int)(phase * 360.0F)).getRGB() & 0xFFFFFF;
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      if (event.getTarget() instanceof LivingEntity) {
         this.hits.put(event.getTarget().getId(), new long[]{System.currentTimeMillis()});
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         this.hits.clear();
         this.health.clear();
         return;
      }

      long now = System.currentTimeMillis();
      LivingEntity aura = tech.pain.client.modules.impl.combat.Aura.INSTANCE.isEnabled() ? tech.pain.client.modules.impl.combat.Aura.INSTANCE.getTarget() : null;
      if (aura != null) {
         this.hits.put(aura.getId(), new long[]{now});
      }

      if (this.health.size() > 512) {
         this.health.clear();
      }

      if (this.anyDeath.isEnabled()) {
         for (Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity living && e != mc.player && mc.player.squaredDistanceTo(e) < 48.0 * 48.0) {
               this.check(living);
            }
         }
      }

      Iterator<Map.Entry<Integer, long[]>> it = this.hits.entrySet().iterator();

      while (it.hasNext()) {
         Map.Entry<Integer, long[]> entry = it.next();
         Entity e = mc.world.getEntityById(entry.getKey());
         if (now - entry.getValue()[0] > 5000L) {
            it.remove();
            this.health.remove(entry.getKey());
            continue;
         }

         if (e == null) {
            // ушёл из прорисовки — не смерть, эффект не нужен
            it.remove();
            this.health.remove(entry.getKey());
            continue;
         }

         if (e instanceof LivingEntity living && this.check(living)) {
            it.remove();
         }
      }
   }

   /** true, если сущность только что умерла (эффект уже запущен). */
   private boolean check(LivingEntity living) {
      Float before = this.health.put(living.getId(), living.getHealth());
      boolean dead = living.isDead() || living.getHealth() <= 0.0F;
      if (dead && (before == null || before > 0.0F)) {
         this.trigger(living);
         return true;
      }

      return false;
   }

   private void trigger(LivingEntity e) {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      Burst b = new Burst();
      b.pos = e.getPos();
      b.height = e.getHeight();
      b.width = e.getWidth();
      b.kind = this.effect.get();
      b.scale = this.size.getCurrent();
      b.phase = rnd.nextFloat();
      b.life = b.kind.equals("Молния") ? 1.0F : b.kind.equals("Осколки") ? 0.6F : 1.8F;
      if (b.kind.equals("Молния")) {
         b.bolts = bolt(rnd);
      }

      if (this.bursts.size() > 6) {
         this.bursts.remove(0);
      }

      this.bursts.add(b);
      if (b.kind.equals("Осколки")) {
         this.shatter(e, b, rnd);
      } else if (b.kind.equals("Дым")) {
         for (int i = 0; i < 34; ++i) {
            Piece p = new Piece();
            p.smoke = true;
            double a = rnd.nextDouble() * Math.PI * 2.0;
            double r = rnd.nextDouble() * (double)b.width * 0.7;
            p.x = b.pos.x + Math.cos(a) * r;
            p.y = b.pos.y + rnd.nextDouble() * (double)b.height;
            p.z = b.pos.z + Math.sin(a) * r;
            p.vx = Math.cos(a) * 0.4;
            p.vy = 0.6 + rnd.nextDouble() * 1.2;
            p.vz = Math.sin(a) * 0.4;
            p.size = (0.25F + rnd.nextFloat() * 0.3F) * b.scale;
            p.life = 1.4F + rnd.nextFloat() * 1.0F;
            p.spin = rnd.nextFloat() * 6.28F;
            p.rgb = this.pick(rnd.nextFloat());
            p.ground = b.pos.y;
            this.pieces.add(p);
         }
      }

      if (this.sound.isEnabled()) {
         String id = switch (b.kind) {
            case "Молния" -> "entity.lightning_bolt.impact";
            case "Душа" -> "particle.soul_escape";
            case "Дым" -> "entity.generic.extinguish_fire";
            default -> "block.amethyst_block.break";
         };
         float vol = this.volume.getCurrent() * (b.kind.equals("Душа") ? 2.0F : 1.0F);
         mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.ofVanilla(id)), 0.9F + rnd.nextFloat() * 0.2F, Math.min(1.0F, vol)));
         if (b.kind.equals("Молния")) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Identifier.ofVanilla("entity.lightning_bolt.thunder")), 1.0F, this.volume.getCurrent() * 0.5F));
         }
      }
   }

   /** Кубики по частям тела: голова 2×2×2, торс 2×3×1, руки и ноги 1×3×1. Для мобов — сетка по их размеру. */
   private void shatter(LivingEntity e, Burst b, ThreadLocalRandom rnd) {
      double yaw = Math.toRadians((double)e.getBodyYaw());
      double cos = Math.cos(yaw);
      double sin = Math.sin(yaw);
      List<double[]> parts = new ArrayList<>();
      if (e instanceof PlayerEntity) {
         // центр части по x (вбок), y, z (вперёд), размер части, деление
         parts.add(new double[]{0.0, 1.75, 0.0, 0.5, 0.5, 0.5, 2, 2, 2});
         parts.add(new double[]{0.0, 1.125, 0.0, 0.5, 0.75, 0.25, 2, 3, 1});
         parts.add(new double[]{0.375, 1.125, 0.0, 0.25, 0.75, 0.25, 1, 3, 1});
         parts.add(new double[]{-0.375, 1.125, 0.0, 0.25, 0.75, 0.25, 1, 3, 1});
         parts.add(new double[]{0.125, 0.375, 0.0, 0.25, 0.75, 0.25, 1, 3, 1});
         parts.add(new double[]{-0.125, 0.375, 0.0, 0.25, 0.75, 0.25, 1, 3, 1});
      } else {
         double w = (double)e.getWidth();
         double h = (double)e.getHeight();
         parts.add(new double[]{0.0, h / 2.0, 0.0, w, h, w, Math.max(1, Math.round(w / 0.3)), Math.max(1, Math.round(h / 0.3)), Math.max(1, Math.round(w / 0.3))});
      }

      Vec3d center = b.pos.add(0.0, (double)b.height * 0.5, 0.0);
      float k = (float)b.height / (e instanceof PlayerEntity ? 2.0F : (float)b.height);

      for (int part = 0; part < parts.size(); ++part) {
         double[] p = parts.get(part);
         int nx = (int)p[6];
         int ny = (int)p[7];
         int nz = (int)p[8];
         double sx = p[3] / (double)nx;
         double sy = p[4] / (double)ny;
         double sz = p[5] / (double)nz;
         int rgb = this.pick(b.phase + (float)part * 0.12F);

         for (int ix = 0; ix < nx; ++ix) {
            for (int iy = 0; iy < ny; ++iy) {
               for (int iz = 0; iz < nz; ++iz) {
                  double lx = p[0] - p[3] / 2.0 + sx * ((double)ix + 0.5);
                  double ly = p[1] - p[4] / 2.0 + sy * ((double)iy + 0.5);
                  double lz = p[2] - p[5] / 2.0 + sz * ((double)iz + 0.5);
                  // поворот части по телу: x — вбок, z — вперёд
                  double wx = -lx * cos - lz * sin;
                  double wz = -lx * sin + lz * cos;
                  Piece piece = new Piece();
                  piece.x = b.pos.x + wx;
                  piece.y = b.pos.y + ly * (double)k;
                  piece.z = b.pos.z + wz;
                  Vec3d out = new Vec3d(piece.x - center.x, (piece.y - center.y) * 0.5, piece.z - center.z);
                  double len = Math.max(0.15, out.length());
                  double speed = (2.0 + rnd.nextDouble() * 2.5) * (double)b.scale;
                  piece.vx = out.x / len * speed + rnd.nextDouble(-0.6, 0.6);
                  piece.vy = 2.5 + rnd.nextDouble() * 3.0;
                  piece.vz = out.z / len * speed + rnd.nextDouble(-0.6, 0.6);
                  piece.size = (float)Math.min(sx, Math.min(sy * (double)k, sz)) * 0.5F;
                  piece.life = 1.4F + rnd.nextFloat() * 0.8F;
                  piece.spin = rnd.nextFloat() * 6.28F;
                  piece.spinSpeed = (rnd.nextFloat() - 0.5F) * 14.0F;
                  piece.rgb = mix(rgb, 0xFFFFFF, rnd.nextFloat() * 0.35F);
                  piece.ground = b.pos.y;
                  this.pieces.add(piece);
               }
            }
         }
      }
   }

   /** Молния: главный ствол с неба и несколько ответвлений, как ломаные. */
   private static List<float[]> bolt(ThreadLocalRandom rnd) {
      List<float[]> lines = new ArrayList<>();
      int n = 18;
      float[] main = new float[(n + 1) * 3];
      float tx = (rnd.nextFloat() - 0.5F) * 8.0F;
      float tz = (rnd.nextFloat() - 0.5F) * 8.0F;
      float ox = 0.0F;
      float oz = 0.0F;

      for (int i = 0; i <= n; ++i) {
         float t = (float)i / (float)n;
         float amp = 1.4F * (1.0F - t * 0.7F);
         ox = ox * 0.6F + (rnd.nextFloat() - 0.5F) * amp;
         oz = oz * 0.6F + (rnd.nextFloat() - 0.5F) * amp;
         boolean end = i == n;
         main[i * 3] = end ? 0.0F : tx * (1.0F - t) + ox;
         main[i * 3 + 1] = 28.0F * (1.0F - t);
         main[i * 3 + 2] = end ? 0.0F : tz * (1.0F - t) + oz;
      }

      lines.add(main);

      for (int b = 0; b < 3; ++b) {
         int from = 4 + rnd.nextInt(9);
         int len = 4 + rnd.nextInt(4);
         float[] br = new float[(len + 1) * 3];
         float dx = (rnd.nextFloat() - 0.5F) * 2.4F;
         float dz = (rnd.nextFloat() - 0.5F) * 2.4F;
         br[0] = main[from * 3];
         br[1] = main[from * 3 + 1];
         br[2] = main[from * 3 + 2];

         for (int i = 1; i <= len; ++i) {
            br[i * 3] = br[(i - 1) * 3] + dx + (rnd.nextFloat() - 0.5F) * 1.2F;
            br[i * 3 + 1] = br[(i - 1) * 3 + 1] - 1.2F - rnd.nextFloat() * 0.8F;
            br[i * 3 + 2] = br[(i - 1) * 3 + 2] + dz + (rnd.nextFloat() - 0.5F) * 1.2F;
         }

         lines.add(br);
      }

      return lines;
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      long nowMs = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.05F, (float)(nowMs - this.lastFrame) / 1000.0F);
      this.lastFrame = nowMs;
      if (mc.player == null || mc.world == null || this.bursts.isEmpty() && this.pieces.isEmpty()) {
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
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      // дым — обычное смешивание (тёмное должно затемнять), кубики — с записью глубины, свечение — сложением
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.depthMask(false);
      BufferBuilder smoke = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = this.pieces.size() - 1; i >= 0; --i) {
         Piece p = this.pieces.get(i);
         if (!p.smoke) {
            continue;
         }

         p.age += dt;
         if (p.age >= p.life) {
            this.pieces.remove(i);
            continue;
         }

         float t = p.age / p.life;
         // клуб закручивается и расширяется
         p.spin += dt * 1.5F;
         p.x += (p.vx + Math.cos((double)p.spin) * 0.3) * (double)dt;
         p.y += p.vy * (double)dt;
         p.z += (p.vz + Math.sin((double)p.spin) * 0.3) * (double)dt;
         p.vy *= Math.max(0.0, 1.0 - (double)dt * 0.6);
         float s = p.size * (1.0F + t * 2.2F);
         float a = (t < 0.15F ? t / 0.15F : 1.0F - (t - 0.15F) / 0.85F) * 0.55F;
         int dark = mix(0x101014, p.rgb, 0.18F);
         ParticleShapes.softDot(smoke, mat, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z), right, up, s, dark, a);
      }

      BuiltBuffer smokeBuilt = smoke.endNullable();
      if (smokeBuilt != null) {
         BufferRenderer.drawWithGlobalProgram(smokeBuilt);
      }

      // кубики: плотные, с тенями по граням
      RenderSystem.depthMask(true);
      BufferBuilder cubes = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = this.pieces.size() - 1; i >= 0; --i) {
         Piece p = this.pieces.get(i);
         if (p.smoke) {
            continue;
         }

         p.age += dt;
         if (p.age >= p.life) {
            this.pieces.remove(i);
            continue;
         }

         p.vy -= 14.0 * (double)dt;
         p.x += p.vx * (double)dt;
         p.y += p.vy * (double)dt;
         p.z += p.vz * (double)dt;
         if (p.y - (double)p.size < p.ground) {
            // отскок с потерей скорости, потом трение по земле
            p.y = p.ground + (double)p.size;
            p.vy = -p.vy * 0.35;
            p.vx *= 0.6;
            p.vz *= 0.6;
            p.spinSpeed *= 0.6F;
         }

         p.spin += p.spinSpeed * dt;
         float t = p.age / p.life;
         float fade = t < 0.6F ? 1.0F : 1.0F - (t - 0.6F) / 0.4F;
         float s = p.size * (t < 0.6F ? 1.0F : fade);
         if (s > 0.003F) {
            cube(cubes, mat, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z), s, p.spin, p.rgb, fade);
         }
      }

      BuiltBuffer cubeBuilt = cubes.endNullable();
      if (cubeBuilt != null) {
         BufferRenderer.drawWithGlobalProgram(cubeBuilt);
      }

      // свечение: молния, душа, вспышки
      RenderSystem.depthMask(false);
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      BufferBuilder glow = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (Burst b : this.bursts) {
         float t = b.age / b.life;
         if (t >= 1.0F) {
            continue;
         }

         float x = (float)(b.pos.x - cam.x);
         float y = (float)(b.pos.y - cam.y);
         float z = (float)(b.pos.z - cam.z);
         int rgb = this.pick(b.phase);
         if (b.kind.equals("Молния")) {
            this.drawBolt(glow, mat, b, x, y, z, rgb);
            float flash = (float)Math.exp((double)(-b.age * 8.0F));
            ParticleShapes.softDot(glow, mat, x, y + 0.6F, z, right, up, 2.6F * b.scale, rgb, flash * 0.8F);
            ParticleShapes.softDot(glow, mat, x, y + 0.6F, z, right, up, 0.9F * b.scale, 0xFFFFFF, flash);
            ring(glow, mat, x, y + 0.03F, z, (0.3F + 3.2F * ease(t)) * b.scale, 0.35F * b.scale, rgb, (1.0F - t) * 0.8F);
         } else if (b.kind.equals("Душа")) {
            this.drawSoul(glow, mat, b, x, y, z, right, up, t);
         } else if (b.kind.equals("Осколки")) {
            ParticleShapes.softDot(glow, mat, x, y + b.height * 0.5F, z, right, up, 1.8F * b.scale, rgb, (1.0F - t) * (1.0F - t) * 0.7F);
            ring(glow, mat, x, y + 0.03F, z, (0.3F + 2.4F * ease(t)) * b.scale, 0.3F * b.scale, rgb, (1.0F - t) * 0.7F);
         }
      }

      BuiltBuffer glowBuilt = glow.endNullable();
      if (glowBuilt != null) {
         BufferRenderer.drawWithGlobalProgram(glowBuilt);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
      Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

      for (int i = this.bursts.size() - 1; i >= 0; --i) {
         Burst b = this.bursts.get(i);
         b.age += dt;
         if (b.age >= b.life) {
            this.bursts.remove(i);
            continue;
         }

         if (!this.distort.isEnabled() || b.kind.equals("Дым")) {
            continue;
         }

         Vector4f clip = new Vector4f((float)(b.pos.x - cam.x), (float)(b.pos.y - cam.y) + b.height * 0.5F, (float)(b.pos.z - cam.z), 1.0F);
         mat.transform(clip);
         projection.transform(clip);
         if (clip.w <= 0.3F) {
            continue;
         }

         float t = Math.min(1.0F, b.age / 0.7F);
         if (t >= 1.0F) {
            continue;
         }

         float perBlock = projection.m11() / (2.0F * clip.w);
         float radius = Math.min(0.45F, (0.3F + 3.0F * ease(t)) * b.scale * perBlock);
         float width = Math.min(0.06F, 0.5F * b.scale * perBlock);
         float strength = Math.min(0.025F, 0.12F * perBlock) * (1.0F - t) * (1.0F - t);
         DrawUtil.drawDistortion(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F, radius, width, strength, 0.0F);
      }
   }

   private void drawBolt(BufferBuilder bb, Matrix4f mat, Burst b, float x, float y, float z, int rgb) {
      // вспышка, гаснет, и ещё один удар через ~0.15 с — как у настоящей молнии
      float t = b.age;
      float power = (float)Math.exp((double)(-t * 7.0F)) * (0.75F + 0.25F * (float)Math.sin((double)(t * 60.0F)));
      if (t > 0.15F) {
         power = Math.max(power, 0.8F * (float)Math.exp((double)(-(t - 0.15F) * 10.0F)));
      }

      if (power < 0.02F) {
         return;
      }

      float s = b.scale;

      for (int l = 0; l < b.bolts.size(); ++l) {
         float[] line = b.bolts.get(l);
         float thick = l == 0 ? 1.0F : 0.55F;

         for (int i = 0; i + 1 < line.length / 3; ++i) {
            float ax = x + line[i * 3] * s;
            float ay = y + line[i * 3 + 1] * s;
            float az = z + line[i * 3 + 2] * s;
            float bx = x + line[(i + 1) * 3] * s;
            float by = y + line[(i + 1) * 3 + 1] * s;
            float bz = z + line[(i + 1) * 3 + 2] * s;
            streak(bb, mat, ax, ay, az, bx, by, bz, 0.32F * thick * s, ParticleShapes.withAlpha(rgb, power * 0.35F));
            streak(bb, mat, ax, ay, az, bx, by, bz, 0.06F * thick * s, ParticleShapes.withAlpha(0xFFFFFF, power));
         }
      }
   }

   private void drawSoul(BufferBuilder bb, Matrix4f mat, Burst b, float x, float y, float z, Vector3f right, Vector3f up, float t) {
      int rgb = this.pick(b.phase);
      float s = b.scale;
      // столб мягкого света
      float radius = 0.45F * s;
      float height = (1.5F + 2.5F * ease(t)) * s;
      float a = (1.0F - t) * 0.45F;

      for (int i = 0; i < 24; ++i) {
         float a0 = (float)i / 24.0F * 6.2832F;
         float a1 = (float)(i + 1) / 24.0F * 6.2832F;
         float x0 = x + MathHelper.cos(a0) * radius;
         float z0 = z + MathHelper.sin(a0) * radius;
         float x1 = x + MathHelper.cos(a1) * radius;
         float z1 = z + MathHelper.sin(a1) * radius;
         int bottom = ParticleShapes.withAlpha(rgb, a);
         int top = ParticleShapes.withAlpha(rgb, 0.0F);
         bb.vertex(mat, x0, y, z0).color(bottom);
         bb.vertex(mat, x1, y, z1).color(bottom);
         bb.vertex(mat, x1, y + height, z1).color(top);
         bb.vertex(mat, x0, y, z0).color(bottom);
         bb.vertex(mat, x1, y + height, z1).color(top);
         bb.vertex(mat, x0, y + height, z0).color(top);
      }

      // шар души поднимается и тает
      float orbY = y + (0.9F + 2.8F * ease(t)) * s;
      float orbA = 1.0F - t * t;
      ParticleShapes.softDot(bb, mat, x, orbY, z, right, up, 0.9F * s, rgb, orbA * 0.5F);
      ParticleShapes.softDot(bb, mat, x, orbY, z, right, up, 0.28F * s, 0xFFFFFF, orbA);

      // спирали вокруг столба
      for (int strand = 0; strand < 4; ++strand) {
         for (int k = 0; k < 14; ++k) {
            float u = (float)k / 14.0F;
            float hy = (t * 1.3F - u * 0.35F) * 3.6F * s;
            if (hy < 0.0F || hy > height) {
               continue;
            }

            float ang = (float)strand * 1.5708F + hy * 2.2F + b.age * 3.0F;
            float r = 0.55F * s * (1.0F - 0.4F * hy / (3.6F * s));
            ParticleShapes.softDot(bb, mat, x + MathHelper.cos(ang) * r, y + hy, z + MathHelper.sin(ang) * r, right, up, 0.08F * s * (1.0F - u), rgb, (1.0F - u) * (1.0F - t));
         }
      }
   }

   private static float ease(float t) {
      return 1.0F - (1.0F - t) * (1.0F - t);
   }

   /** Кубик, повёрнутый вокруг вертикали; грани чуть разной яркости, чтобы был виден объём. */
   private static void cube(BufferBuilder bb, Matrix4f m, float x, float y, float z, float s, float spin, int rgb, float alpha) {
      float c = MathHelper.cos(spin) * s;
      float sn = MathHelper.sin(spin) * s;
      float[][] v = new float[8][];

      for (int i = 0; i < 8; ++i) {
         float lx = (i & 1) == 0 ? -1.0F : 1.0F;
         float ly = (i & 2) == 0 ? -1.0F : 1.0F;
         float lz = (i & 4) == 0 ? -1.0F : 1.0F;
         v[i] = new float[]{x + lx * c - lz * sn, y + ly * s, z + lx * sn + lz * c};
      }

      int[][] faces = new int[][]{{2, 3, 7, 6}, {0, 4, 5, 1}, {0, 2, 6, 4}, {1, 5, 7, 3}, {0, 1, 3, 2}, {4, 6, 7, 5}};
      float[] shade = new float[]{1.0F, 0.45F, 0.7F, 0.8F, 0.6F, 0.9F};

      for (int f = 0; f < 6; ++f) {
         int col = ParticleShapes.withAlpha(scale(rgb, shade[f]), alpha);
         int[] q = faces[f];
         bb.vertex(m, v[q[0]][0], v[q[0]][1], v[q[0]][2]).color(col);
         bb.vertex(m, v[q[1]][0], v[q[1]][1], v[q[1]][2]).color(col);
         bb.vertex(m, v[q[2]][0], v[q[2]][1], v[q[2]][2]).color(col);
         bb.vertex(m, v[q[0]][0], v[q[0]][1], v[q[0]][2]).color(col);
         bb.vertex(m, v[q[2]][0], v[q[2]][1], v[q[2]][2]).color(col);
         bb.vertex(m, v[q[3]][0], v[q[3]][1], v[q[3]][2]).color(col);
      }
   }

   /** Отрезок, развёрнутый ребром к камере (камера в нуле). */
   private static void streak(BufferBuilder bb, Matrix4f mat, float ax, float ay, float az, float bx, float by, float bz, float width, int color) {
      float dx = bx - ax;
      float dy = by - ay;
      float dz = bz - az;
      float sx = dy * az - dz * ay;
      float sy = dz * ax - dx * az;
      float sz = dx * ay - dy * ax;
      float len = (float)Math.sqrt((double)(sx * sx + sy * sy + sz * sz));
      if (len < 1.0E-5F) {
         return;
      }

      sx = sx / len * width;
      sy = sy / len * width;
      sz = sz / len * width;
      int edge = color & 0xFFFFFF;
      // середина яркая, края прозрачные
      bb.vertex(mat, ax, ay, az).color(color);
      bb.vertex(mat, bx, by, bz).color(color);
      bb.vertex(mat, bx + sx, by + sy, bz + sz).color(edge);
      bb.vertex(mat, ax, ay, az).color(color);
      bb.vertex(mat, bx + sx, by + sy, bz + sz).color(edge);
      bb.vertex(mat, ax + sx, ay + sy, az + sz).color(edge);
      bb.vertex(mat, ax, ay, az).color(color);
      bb.vertex(mat, bx, by, bz).color(color);
      bb.vertex(mat, bx - sx, by - sy, bz - sz).color(edge);
      bb.vertex(mat, ax, ay, az).color(color);
      bb.vertex(mat, bx - sx, by - sy, bz - sz).color(edge);
      bb.vertex(mat, ax - sx, ay - sy, az - sz).color(edge);
   }

   private static void ring(BufferBuilder bb, Matrix4f mat, float x, float y, float z, float radius, float width, int rgb, float alpha) {
      float inner = Math.max(0.0F, radius - width);
      float mid = (inner + radius) * 0.5F;
      int bright = ParticleShapes.withAlpha(rgb, alpha);
      int clear = ParticleShapes.withAlpha(rgb, 0.0F);

      for (int i = 0; i < 40; ++i) {
         float a0 = (float)i / 40.0F * 6.2832F;
         float a1 = (float)(i + 1) / 40.0F * 6.2832F;
         float c0 = MathHelper.cos(a0);
         float s0 = MathHelper.sin(a0);
         float c1 = MathHelper.cos(a1);
         float s1 = MathHelper.sin(a1);

         for (int half = 0; half < 2; ++half) {
            float r0 = half == 0 ? inner : mid;
            float r1 = half == 0 ? mid : radius;
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

   private static int scale(int rgb, float k) {
      return (int)((float)(rgb >> 16 & 0xFF) * k) << 16 | (int)((float)(rgb >> 8 & 0xFF) * k) << 8 | (int)((float)(rgb & 0xFF) * k);
   }

   private static int mix(int a, int b, float k) {
      int r = (int)((float)(a >> 16 & 0xFF) * (1.0F - k) + (float)(b >> 16 & 0xFF) * k);
      int g = (int)((float)(a >> 8 & 0xFF) * (1.0F - k) + (float)(b >> 8 & 0xFF) * k);
      int bl = (int)((float)(a & 0xFF) * (1.0F - k) + (float)(b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }

   private static final class Burst {
      Vec3d pos;
      String kind = "";
      float height;
      float width;
      float scale;
      float phase;
      float age;
      float life;
      List<float[]> bolts;
   }

   private static final class Piece {
      boolean smoke;
      int rgb;
      double x;
      double y;
      double z;
      double vx;
      double vy;
      double vz;
      double ground;
      float size;
      float spin;
      float spinSpeed;
      float age;
      float life;
   }
}
