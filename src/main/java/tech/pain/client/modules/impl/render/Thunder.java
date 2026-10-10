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
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Гроза: пока модуль включён, по кругу играет запись дождя с громом, а в моменты раскатов
 * в небе бьёт молния. Моменты грома найдены в записи заранее (резкие всплески низких частот),
 * сила раската задаёт яркость и близость молнии. Всё видно и слышно только тебе.
 */
@ModuleAnnotation(
   name = "Thunder",
   category = Category.RENDER,
   description = "Гроза: зацикленный звук дождя с громом, на каждый раскат — молния"
)
public final class Thunder extends Module {
   public static final Thunder INSTANCE = new Thunder();
   private static final Identifier SOUND = Identifier.of("pain", "thunder_rain");
   /** Длина записи в секундах. */
   private static final double LENGTH = 501.3936;
   /** Раскаты в записи: {секунда, сила}. Сила — громкость низких частот, у близкого грома 0.1+. */
   private static final float[][] STRIKES = new float[][]{
      {5.0F, 0.033F}, {15.9F, 0.033F}, {24.0F, 0.033F}, {52.1F, 0.023F}, {55.0F, 0.020F}, {126.5F, 0.046F}, {221.0F, 0.020F},
      {233.0F, 0.041F}, {265.3F, 0.028F}, {287.6F, 0.026F}, {292.5F, 0.050F}, {302.3F, 0.020F}, {328.8F, 0.027F}, {331.4F, 0.036F},
      {337.8F, 0.106F}, {341.0F, 0.121F}, {353.3F, 0.216F}, {357.0F, 0.201F}, {376.0F, 0.047F}, {443.9F, 0.027F}, {468.2F, 0.023F}
   };
   private final NumberSetting volume = new NumberSetting("Громкость", 1.0F, 0.05F, 1.0F, 0.05F);
   private final BooleanSetting lightning = new BooleanSetting("Молнии", true);
   private final NumberSetting minStrength = new NumberSetting("Молния от силы грома", 0.02F, 0.02F, 0.15F, 0.005F, (java.util.function.Supplier<Boolean>)() -> this.lightning.isEnabled());
   private final BooleanSetting skyFlash = new BooleanSetting("Вспышка неба", true, () -> this.lightning.isEnabled());
   private final NumberSetting shift = new NumberSetting("Сдвиг молнии (мс)", -200.0F, -1500.0F, 1500.0F, 50.0F, (java.util.function.Supplier<Boolean>)() -> this.lightning.isEnabled());
   private final List<Bolt> bolts = new ArrayList<>();
   private Loop loop;
   private double position;
   private long lastFrame;
   private long lastCheck;

   private Thunder() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.position = 0.0;
      this.lastFrame = 0L;
      this.start();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.stop();
      this.bolts.clear();
   }

   private void start() {
      this.stop();
      if (mc.world == null) {
         return;
      }

      // счёт времени здесь не трогаем: если звук не играет (громкость на нуле, нет устройства),
      // перезапуск каждые полторы секунды обнулял бы его, и молнии не били бы никогда
      this.loop = new Loop();
      mc.getSoundManager().play(this.loop);
   }

   private void stop() {
      if (this.loop != null) {
         this.loop.finish();
         mc.getSoundManager().stop(this.loop);
         this.loop = null;
      }
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      // звук мог оборваться (смена мира, перезагрузка звука) — запускаем заново
      if (now - this.lastCheck > 5000L) {
         this.lastCheck = now;
         if (this.loop == null || !mc.getSoundManager().isPlaying(this.loop)) {
            this.start();
         }
      }

      float dt = this.lastFrame == 0L ? 0.0F : Math.min(0.25F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      // на паузе игра останавливает и звук, поэтому своё время тоже не идёт
      if (!mc.isPaused()) {
         double before = this.position;
         this.position = (this.position + (double)dt) % LENGTH;
         if (this.lightning.isEnabled()) {
            double lead = (double)this.shift.getCurrent() / 1000.0;

            for (float[] strike : STRIKES) {
               double at = ((double)strike[0] + lead + LENGTH) % LENGTH;
               boolean crossed = before <= this.position ? at > before && at <= this.position : at > before || at <= this.position;
               if (crossed && strike[1] >= this.minStrength.getCurrent()) {
                  this.strike(strike[1]);
               }
            }
         }
      }

      this.drawBolts(event, dt);
   }

   /** Молния: чем сильнее раскат, тем она ближе, толще и ярче. */
   private void strike(float strength) {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      float power = MathHelper.clamp((strength - 0.02F) / 0.12F, 0.0F, 1.0F);
      double distance = 80.0 - 40.0 * (double)power + rnd.nextDouble(-8.0, 8.0);
      // чаще перед глазами, чтобы её было видно
      double angle = Math.toRadians((double)mc.player.getYaw() + 90.0 + rnd.nextDouble(-38.0, 38.0));
      double x = mc.player.getX() + Math.cos(angle) * distance;
      double z = mc.player.getZ() + Math.sin(angle) * distance;
      double ground = (double)mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
      if (ground <= (double)mc.world.getBottomY() + 1.0) {
         // чанк не прогружен — бьём на уровне игрока
         ground = mc.player.getY() - 2.0;
      }

      Bolt bolt = new Bolt();
      bolt.power = power;
      bolt.life = 0.9F + 0.6F * power;
      bolt.width = (float)distance / 45.0F * (0.75F + 0.6F * power);
      Vec3d top = new Vec3d(x + rnd.nextDouble(-12.0, 12.0), Math.max(ground + 70.0, mc.player.getY() + 90.0), z + rnd.nextDouble(-12.0, 12.0));
      Vec3d bottom = new Vec3d(x, ground, z);
      this.branch(bolt, top, bottom, 16, distance * 0.07, 1.0F, 2, rnd);
      if (this.bolts.size() > 6) {
         this.bolts.remove(0);
      }

      this.bolts.add(bolt);
      if (this.skyFlash.isEnabled()) {
         mc.world.setLightningTicksLeft(2 + (int)(power * 2.0F));
      }
   }

   /** Ломаная от from до to со случайными изломами; от некоторых узлов отходят ветки потоньше. */
   private void branch(Bolt bolt, Vec3d from, Vec3d to, int segments, double jitter, float thickness, int depth, ThreadLocalRandom rnd) {
      Vec3d prev = from;

      for (int i = 1; i <= segments; ++i) {
         double t = (double)i / (double)segments;
         Vec3d point = from.lerp(to, t);
         if (i < segments) {
            point = point.add(rnd.nextDouble(-jitter, jitter), rnd.nextDouble(-jitter * 0.3, jitter * 0.3), rnd.nextDouble(-jitter, jitter));
         }

         bolt.segments.add(new Segment(prev, point, thickness * (float)(1.0 - 0.35 * t)));
         if (depth > 0 && i > 2 && i < segments - 1 && rnd.nextFloat() < 0.22F) {
            Vec3d dir = to.subtract(from).normalize();
            double len = from.distanceTo(to) * rnd.nextDouble(0.15, 0.35);
            Vec3d end = point.add(dir.multiply(len)).add(rnd.nextDouble(-len, len) * 0.7, 0.0, rnd.nextDouble(-len, len) * 0.7);
            this.branch(bolt, point, end, 6, jitter * 0.6, thickness * 0.5F, depth - 1, rnd);
         }

         prev = point;
      }
   }

   private void drawBolts(EventRender3D event, float dt) {
      if (this.bolts.isEmpty()) {
         return;
      }

      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = this.bolts.size() - 1; i >= 0; --i) {
         Bolt bolt = this.bolts.get(i);
         if (!mc.isPaused()) {
            bolt.age += dt;
         }

         if (bolt.age >= bolt.life) {
            this.bolts.remove(i);
            continue;
         }

         float t = bolt.age / bolt.life;
         // две вспышки подряд и затухание — молния мерцает
         float flicker = Math.max(0.0F, 1.0F - t * 4.0F) + (t > 0.3F ? Math.max(0.0F, 1.0F - (t - 0.3F) * 1.6F) * 0.8F : 0.0F);
         float alpha = Math.min(1.0F, flicker) * (0.6F + 0.4F * bolt.power);
         if (alpha <= 0.01F) {
            continue;
         }

         for (Segment s : bolt.segments) {
            float w = bolt.width * s.thickness;
            quad(bb, mat, s, cam, w * 4.5F, ParticleShapes.argb(120, 150, 255, alpha * 0.22F));
            quad(bb, mat, s, cam, w * 1.8F, ParticleShapes.argb(190, 205, 255, alpha * 0.5F));
            quad(bb, mat, s, cam, w * 0.6F, ParticleShapes.argb(255, 255, 255, alpha));
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
   }

   /** Отрезок молнии — полоска, развёрнутая ребром к камере. */
   private static void quad(BufferBuilder bb, Matrix4f mat, Segment s, Vec3d cam, float width, int color) {
      float ax = (float)(s.from.x - cam.x);
      float ay = (float)(s.from.y - cam.y);
      float az = (float)(s.from.z - cam.z);
      float bx = (float)(s.to.x - cam.x);
      float by = (float)(s.to.y - cam.y);
      float bz = (float)(s.to.z - cam.z);
      float dx = bx - ax;
      float dy = by - ay;
      float dz = bz - az;
      // поперёк отрезка и поперёк взгляда
      float sx = dy * az - dz * ay;
      float sy = dz * ax - dx * az;
      float sz = dx * ay - dy * ax;
      float len = (float)Math.sqrt((double)(sx * sx + sy * sy + sz * sz));
      if (len < 1.0E-4F) {
         return;
      }

      sx = sx / len * width;
      sy = sy / len * width;
      sz = sz / len * width;
      bb.vertex(mat, ax - sx, ay - sy, az - sz).color(color);
      bb.vertex(mat, ax + sx, ay + sy, az + sz).color(color);
      bb.vertex(mat, bx + sx, by + sy, bz + sz).color(color);
      bb.vertex(mat, ax - sx, ay - sy, az - sz).color(color);
      bb.vertex(mat, bx + sx, by + sy, bz + sz).color(color);
      bb.vertex(mat, bx - sx, by - sy, bz - sz).color(color);
   }

   private record Segment(Vec3d from, Vec3d to, float thickness) {
   }

   private static final class Bolt {
      final List<Segment> segments = new ArrayList<>();
      float age;
      float life;
      float width;
      float power;
   }

   /** Зацикленная запись: звучит «в голове», громкость берётся из настройки на лету. */
   private final class Loop extends MovingSoundInstance {
      Loop() {
         super(SoundEvent.of(SOUND), SoundCategory.WEATHER, SoundInstance.createRandom());
         this.repeat = true;
         this.repeatDelay = 0;
         this.relative = true;
         this.attenuationType = SoundInstance.AttenuationType.NONE;
         this.volume = Thunder.this.volume.getCurrent();
      }

      @Override
      public void tick() {
         this.volume = Thunder.this.volume.getCurrent();
      }

      void finish() {
         this.setDone();
      }
   }
}
