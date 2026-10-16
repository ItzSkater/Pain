package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Random;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.utility.render.level.ParticleShapes;
import net.minecraft.util.math.MathHelper;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Погода только на твоём экране: сервер и другие игроки её не видят.
 * Снег и дождь в сухих биомах подменяются в BiomeWeatherMixin.
 */
@ModuleAnnotation(
   name = "Weather",
   category = Category.RENDER,
   description = "Своя погода: ясно, дождь, гроза или снег — видно только тебе"
)
public final class WeatherControl extends Module {
   public static final WeatherControl INSTANCE = new WeatherControl();
   private final ModeSetting weather = new ModeSetting("Погода", "Дождь", "Гроза", "Снег", "Ясно");
   private final NumberSetting strength = new NumberSetting("Сила", 1.0F, 0.2F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> !this.weather.is("Ясно"));
   private final BooleanSetting everywhere = new BooleanSetting("В любом биоме", true, () -> !this.weather.is("Ясно"));
   private final BooleanSetting smooth = new BooleanSetting("Плавная смена", true);
   private final ModeSetting view = new ModeSetting("Вид", "Реалистичный", "Ванильный");
   private final NumberSetting density = new NumberSetting("Густота", 1.0F, 0.3F, 2.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.view.is("Реалистичный"));
   private final NumberSetting wind = new NumberSetting("Ветер", 0.35F, 0.0F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.view.is("Реалистичный"));
   private final BooleanSetting splashes = new BooleanSetting("Всплески на земле", true, () -> this.view.is("Реалистичный") && !this.weather.is("Снег"));
   private final BooleanSetting lens = new BooleanSetting("Капли на экране", true, () -> this.view.is("Реалистичный") && !this.weather.is("Снег"));
   private final NumberSetting mist = new NumberSetting("Дымка", 0.6F, 0.0F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.view.is("Реалистичный"));
   private long boltAt;
   private float boltAzimuth;
   private float boltSeed;
   private boolean wasStorm;
   private final BooleanSetting shine = new BooleanSetting("Блеск мокрых поверхностей", true, () -> !this.weather.is("Снег") && !this.weather.is("Ясно"));
   private final NumberSetting shineAmount = new NumberSetting("Сила блеска", 0.6F, 0.1F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.shine.isEnabled() && !this.weather.is("Снег") && !this.weather.is("Ясно"));
   private float wet;
   private long lastFrame;
   private static final int MAX = 32000;
   private static final int GRID = 64;
   /** Случайные постоянные каждой частицы: место в объёме, скорость, размер, фазы покачивания. */
   private final float[] seeds = new float[MAX * 6];
   private final int[] heights = new int[GRID * GRID];
   private int gridX = Integer.MIN_VALUE;
   private int gridZ;
   private long gridAt;
   private final long started = System.nanoTime();
   private float fx;
   private float fy;
   private float fz = -1.0F;

   private WeatherControl() {
      Random rnd = new Random(7L);

      for (int i = 0; i < this.seeds.length; ++i) {
         this.seeds[i] = rnd.nextFloat();
      }
   }

   /** Свои осадки вместо ванильных — их отключает WeatherRenderingMixin. */
   public boolean realistic() {
      return this.isEnabled() && this.view.is("Реалистичный");
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.world == null || mc.player == null || !this.view.is("Реалистичный")) {
         return;
      }

      float amount = mc.world.getRainGradient(event.getPartialTicks());
      if (amount < 0.02F) {
         return;
      }

      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cam = camera.getPos();
      this.refreshHeights(cam);
      // куда смотрит камера: капли за спиной не рисуем, их всё равно не видно
      org.joml.Vector3f forward = new org.joml.Vector3f(0.0F, 0.0F, -1.0F).rotate(camera.getRotation());
      this.fx = forward.x;
      this.fy = forward.y;
      this.fz = forward.z;
      double time = (double)(System.nanoTime() - this.started) / 1.0E9;
      float w = this.wind.getCurrent();
      // ветер дует порывами; направление медленно гуляет
      double windAngle = 0.7 + 0.35 * Math.sin(time * 0.04);
      double gust = (double)w * (0.6 + 0.4 * Math.sin(time * 0.31) + 0.2 * Math.sin(time * 0.73));
      // путь, который ветер уже «надул» — интеграл порывов, чтобы частицы не прыгали
      double blown = (double)w * (0.6 * time - 0.4 / 0.31 * Math.cos(time * 0.31) - 0.2 / 0.73 * Math.cos(time * 0.73));
      double wx = Math.cos(windAngle);
      double wz = Math.sin(windAngle);
      // ночью осадки темнее, иначе светятся
      float light = 0.4F + 0.6F * mc.world.getSkyBrightness(event.getPartialTicks());
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      if (this.weather.is("Снег")) {
         this.drawSnow(bb, mat, camera, cam, time, blown, wx, wz, amount, light);
      } else {
         // ближний слой — отдельные капли со всплесками; дальний — редкая широкая завеса, она даёт дождю глубину
         this.drawRain(bb, mat, cam, time, gust, blown, wx, wz, amount, light, 22.0, 20.0, 9500.0F, 1.0F, 0, true);
         this.drawRain(bb, mat, cam, time, gust, blown, wx, wz, amount, light, 52.0, 30.0, 6000.0F, 0.45F, 9500, false);
         if (this.shine.isEnabled()) {
            this.drawShine(bb, mat, camera, cam, time, amount, light);
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      // стекло намокает, только пока стоишь под открытым небом, и высыхает под крышей
      long now = System.currentTimeMillis();
      float dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      boolean open = cam.y > (double)this.height(cam.x, cam.z) && !this.weather.is("Снег") && this.lens.isEnabled();
      this.wet += ((open ? amount : 0.0F) - this.wet) * Math.min(1.0F, dt * (open ? 0.5F : 0.25F));
      if (this.wet > 0.02F) {
         tech.pain.utility.render.display.shader.DrawUtil.drawLensRain(this.wet, (float)(time % 3600.0));
      }
   }

   /** Вспышка молнии на мгновение высвечивает весь мир. */
   @EventTarget
   public void onRender2D(tech.pain.base.events.impl.render.EventRender2D event) {
      float flash = this.boltFlash();
      if (flash > 0.02F && this.view.is("Реалистичный") && mc.world != null) {
         event.getContext().drawRect(0.0F, 0.0F, (float)mc.getWindow().getScaledWidth(), (float)mc.getWindow().getScaledHeight(), new tech.pain.utility.render.display.base.color.ColorRGBA(205, 218, 255, (int)(flash * 34.0F)));
      }
   }

   /** Яркость текущей молнии: две вспышки подряд и затухание. */
   public float boltFlash() {
      float t = (float)(System.currentTimeMillis() - this.boltAt) / 1000.0F;
      if (!this.isEnabled() || !this.weather.is("Гроза") || t < 0.0F || t > 0.6F) {
         return 0.0F;
      }

      return Math.min(1.0F, Math.max(0.0F, 1.0F - t * 6.0F) + (t > 0.16F ? Math.max(0.0F, 1.0F - (t - 0.16F) * 3.0F) * 0.85F : 0.0F));
   }

   public float boltAzimuth() {
      return this.boltAzimuth;
   }

   public float boltSeed() {
      return this.boltSeed;
   }

   /** Насколько дождь или снег затягивает даль дымкой — спрашивает миксин тумана. */
   public float mistAmount() {
      if (!this.realistic() || mc.world == null) {
         return 0.0F;
      }

      return mc.world.getRainGradient(1.0F) * this.mist.getCurrent();
   }

   private void drawRain(BufferBuilder bb, Matrix4f mat, Vec3d cam, double time, double gust, double blown, double wx, double wz, float amount, float light, double size, double tall, float perUnit, float alphaMul, int shift, boolean near) {
      int count = Math.min(MAX - shift, (int)(perUnit * this.density.getCurrent() * amount));
      int rgb = ParticleShapes.argb((int)(185.0F * light), (int)(205.0F * light), (int)(235.0F * light), 0.0F) & 0xFFFFFF;
      double slant = 6.5;

      for (int i = 0; i < count; ++i) {
         int k = (i + shift) * 6;
         double speed = 17.0 + 7.0 * (double)this.seeds[k + 3];
         // капля привязана к миру, а не к камере: при повороте и ходьбе дождь не «едет» вместе с тобой
         double x = wrap((double)this.seeds[k] * size + wx * blown * slant - cam.x, size);
         double y = wrap((double)this.seeds[k + 1] * tall - speed * time - cam.y, tall);
         double z = wrap((double)this.seeds[k + 2] * size + wz * blown * slant - cam.z, size);
         if (x * (double)this.fx + y * (double)this.fy + z * (double)this.fz < -1.5) {
            continue;
         }

         double flat = Math.sqrt(x * x + z * z);
         float fade = (float)Math.min(1.0, (size * 0.5 - flat) / 4.0) * (float)Math.min(1.0, (tall * 0.5 - Math.abs(y)) / 2.0);
         if (fade <= 0.0F || flat < 0.5 && Math.abs(y) < 1.5) {
            continue;
         }

         int ground = this.height(cam.x + x, cam.z + z);
         double worldY = cam.y + y;
         if (worldY > (double)ground) {
            // след капли за время «выдержки» — отсюда вытянутая чёрточка с наклоном по ветру
            double exposure = 0.042 + 0.022 * (double)this.seeds[k + 4];
            float tx = (float)(x - wx * gust * slant * exposure);
            float ty = (float)(y + speed * exposure);
            float tz = (float)(z - wz * gust * slant * exposure);
            // вдали капля тоньше пикселя и пропала бы, поэтому ширина растёт с расстоянием
            float width = Math.max(0.0035F + 0.0035F * this.seeds[k + 5], (float)Math.sqrt(flat * flat + y * y) * (near ? 0.00105F : 0.0013F));
            float alpha = (0.17F + 0.2F * this.seeds[k + 4]) * alphaMul * (float)Math.min(1.0, Math.max(0.0, (Math.sqrt(flat * flat + y * y) - 1.2) / 2.5)) * fade * Math.min(1.0F, amount * 1.3F);
            streak(bb, mat, (float)x, (float)y, (float)z, tx, ty, tz, width, ParticleShapes.withAlpha(rgb, alpha), ParticleShapes.withAlpha(rgb, 0.0F));
         } else if (near && this.splashes.isEnabled()) {
            // капля только что прошла сквозь землю — значит, здесь всплеск
            double since = ((double)ground - worldY) / speed;
            if (since < 0.16 && flat < 11.0) {
               float age = (float)(since / 0.16);
               float sy = (float)((double)ground - cam.y) + 0.02F;
               ring(bb, mat, (float)x, sy, (float)z, 0.04F + 0.17F * age, 0.02F, ParticleShapes.withAlpha(rgb, (1.0F - age) * 0.45F * fade));
               // корона брызг: три капельки подлетают и падают
               for (int d = 0; d < 3; ++d) {
                  float a = (this.seeds[k + 5] + (float)d / 3.0F) * 6.2832F;
                  float out = 0.12F * age;
                  float up = 0.22F * age - 0.3F * age * age;
                  float px = (float)x + (float)Math.cos((double)a) * out;
                  float pz = (float)z + (float)Math.sin((double)a) * out;
                  streak(bb, mat, px, sy + up, pz, px, sy + up + 0.03F, pz, 0.008F, ParticleShapes.withAlpha(rgb, (1.0F - age) * 0.5F * fade), ParticleShapes.withAlpha(rgb, (1.0F - age) * 0.5F * fade));
               }
            }
         }
      }
   }

   private void drawSnow(BufferBuilder bb, Matrix4f mat, Camera camera, Vec3d cam, double time, double blown, double wx, double wz, float amount, float light) {
      final double size = 26.0;
      final double tall = 18.0;
      int count = Math.min(MAX, (int)(12000.0F * this.density.getCurrent() * amount));
      int rgb = ParticleShapes.argb((int)(245.0F * light), (int)(248.0F * light), (int)(255.0F * light), 0.0F) & 0xFFFFFF;
      Vector3f up = camera.getVerticalPlane();
      Vector3f right = camera.getDiagonalPlane();

      for (int i = 0; i < count; ++i) {
         int k = i * 6;
         double speed = 0.8 + 1.3 * (double)this.seeds[k + 3];
         // у каждой снежинки своё покачивание, мелкие летят медленнее и сильнее слушаются ветра
         // медленный общий вихрь плюс своё покачивание у каждой снежинки
         double swirl = time * 0.22 + (double)this.seeds[k] * 5.0 + (double)this.seeds[k + 2] * 5.0;
         double swayX = Math.sin(time * (0.5 + (double)this.seeds[k + 4]) + (double)this.seeds[k + 5] * 6.28) * 0.35 + Math.sin(swirl) * 0.9;
         double swayZ = Math.cos(time * (0.4 + (double)this.seeds[k + 5]) + (double)this.seeds[k + 4] * 6.28) * 0.35 + Math.cos(swirl * 0.8) * 0.9;
         double drift = blown * (2.6 - 1.2 * (double)this.seeds[k + 3]);
         double x = wrap((double)this.seeds[k] * size + wx * drift + swayX - cam.x, size);
         double y = wrap((double)this.seeds[k + 1] * tall - speed * time - cam.y, tall);
         double z = wrap((double)this.seeds[k + 2] * size + wz * drift + swayZ - cam.z, size);
         if (x * (double)this.fx + y * (double)this.fy + z * (double)this.fz < -1.0) {
            continue;
         }

         double flat = Math.sqrt(x * x + z * z);
         float fade = (float)Math.min(1.0, (size * 0.5 - flat) / 4.0) * (float)Math.min(1.0, (tall * 0.5 - Math.abs(y)) / 2.0);
         if (fade <= 0.0F || flat < 0.35 && Math.abs(y) < 0.6) {
            continue;
         }

         if (cam.y + y <= (double)this.height(cam.x + x, cam.z + z)) {
            continue;
         }

         float s4 = this.seeds[k + 3] * this.seeds[k + 3] * this.seeds[k + 3] * this.seeds[k + 3];
         float flake = Math.max(0.02F + 0.045F * s4, (float)Math.sqrt(flat * flat + y * y) * 0.0042F);
         // снежинка кувыркается — блик то ярче, то слабее
         float glint = 0.72F + 0.28F * (float)Math.sin(time * (2.0 + 3.0 * (double)this.seeds[k + 5]) + (double)this.seeds[k] * 40.0);
         // совсем близкие хлопья не в фокусе: крупнее и прозрачнее
         float nearBlur = (float)Math.max(0.0, 1.0 - Math.sqrt(flat * flat + y * y) / 2.5);
         ParticleShapes.softDot(bb, mat, (float)x, (float)y, (float)z, right, up, flake * (1.0F + nearBlur * 1.6F), rgb, (0.95F - 0.6F * nearBlur) * glint * fade * Math.min(1.0F, amount * 1.3F));
      }
   }

   /**
    * Мокрый блеск: на верхних гранях блоков вокруг вспыхивают и гаснут блики, как на мокром камне и листве.
    * Точки привязаны к миру и лежат на поверхности, поэтому при ходьбе не плывут. Под шейдерами к этому
    * добавляются настоящие лужи с отражениями — их включает модуль Shaders.
    */
   private void drawShine(BufferBuilder bb, Matrix4f mat, Camera camera, Vec3d cam, double time, float amount, float light) {
      final double size = 26.0;
      float power = this.shineAmount.getCurrent();
      int count = Math.min(MAX, (int)(2600.0F * power * amount));
      Vector3f up = camera.getVerticalPlane();
      Vector3f right = camera.getDiagonalPlane();
      int rgb = ParticleShapes.argb((int)(235.0F * light), (int)(242.0F * light), (int)(255.0F * light), 0.0F) & 0xFFFFFF;

      for (int i = 0; i < count; ++i) {
         int k = (MAX - 1 - i) * 6;
         double x = wrap((double)this.seeds[k] * size * 7.0 - cam.x, size);
         double z = wrap((double)this.seeds[k + 2] * size * 7.0 - cam.z, size);
         double flat = Math.sqrt(x * x + z * z);
         if (flat > size * 0.5 || flat < 0.6) {
            continue;
         }

         double ground = (double)this.height(cam.x + x, cam.z + z) + 0.015;
         double y = ground - cam.y;
         // блик живёт долю секунды: вспыхнул, погас, пауза
         double phase = time * (0.7 + 1.6 * (double)this.seeds[k + 3]) + (double)this.seeds[k + 4] * 40.0;
         float spark = (float)Math.pow(Math.max(0.0, Math.sin(phase)), 14.0);
         if (spark < 0.03F) {
            continue;
         }

         float fade = (float)Math.min(1.0, (size * 0.5 - flat) / 5.0);
         float dist = (float)Math.sqrt(flat * flat + y * y);
         // на пологих углах мокрое блестит сильнее, прямо под ногами — почти нет
         float grazing = (float)Math.min(1.0, flat / (Math.abs(y) + 0.6) * 0.45);
         float dot = Math.max(0.012F + 0.02F * this.seeds[k + 5], dist * 0.0028F);
         float alpha = spark * fade * grazing * (0.55F + 0.45F * power) * Math.min(1.0F, amount * 1.3F);
         ParticleShapes.softDot(bb, mat, (float)x, (float)y, (float)z, right, up, dot * 2.6F, rgb, alpha * 0.35F);
         ParticleShapes.softDot(bb, mat, (float)x, (float)y, (float)z, right, up, dot, 0xFFFFFF, alpha);
      }
   }

   /** Насколько сильно всё должно блестеть в дождь: 0 — выключено. Читает модуль Shaders для луж. */
   public float shineLevel() {
      return this.isEnabled() && this.shine.isEnabled() && !this.weather.is("Снег") && !this.weather.is("Ясно") ? this.shineAmount.getCurrent() : 0.0F;
   }

   private static double wrap(double value, double size) {
      double m = (value + size * 0.5) % size;
      return (m < 0.0 ? m + size : m) - size * 0.5;
   }

   /** Высота верхнего блока в колонке; сетку вокруг камеры пересчитываем дважды в секунду. */
   private int height(double x, double z) {
      int ix = (int)Math.floor(x) - this.gridX;
      int iz = (int)Math.floor(z) - this.gridZ;
      if (ix < 0 || iz < 0 || ix >= GRID || iz >= GRID) {
         return mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int)Math.floor(x), (int)Math.floor(z));
      }

      return this.heights[iz * GRID + ix];
   }

   private void refreshHeights(Vec3d cam) {
      long now = System.currentTimeMillis();
      int cx = (int)Math.floor(cam.x) - GRID / 2;
      int cz = (int)Math.floor(cam.z) - GRID / 2;
      if (this.gridX != Integer.MIN_VALUE && now - this.gridAt < 500L && Math.abs(cx - this.gridX) < 8 && Math.abs(cz - this.gridZ) < 8) {
         return;
      }

      this.gridX = cx;
      this.gridZ = cz;
      this.gridAt = now;

      for (int z = 0; z < GRID; ++z) {
         for (int x = 0; x < GRID; ++x) {
            this.heights[z * GRID + x] = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, cx + x, cz + z);
         }
      }
   }

   /** Полоска от головы к хвосту, развёрнутая ребром к камере (камера в начале координат). */
   private static void streak(BufferBuilder bb, Matrix4f mat, float ax, float ay, float az, float bx, float by, float bz, float width, int head, int tail) {
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
      bb.vertex(mat, ax - sx, ay - sy, az - sz).color(head);
      bb.vertex(mat, ax + sx, ay + sy, az + sz).color(head);
      bb.vertex(mat, bx + sx, by + sy, bz + sz).color(tail);
      bb.vertex(mat, ax - sx, ay - sy, az - sz).color(head);
      bb.vertex(mat, bx + sx, by + sy, bz + sz).color(tail);
      bb.vertex(mat, bx - sx, by - sy, bz - sz).color(tail);
   }

   /** Плоское кольцо на земле — круг от упавшей капли. */
   private static void ring(BufferBuilder bb, Matrix4f mat, float x, float y, float z, float radius, float thickness, int color) {
      int segments = 8;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2832F;
         float a1 = (float)(i + 1) / (float)segments * 6.2832F;
         float c0 = (float)Math.cos((double)a0);
         float s0 = (float)Math.sin((double)a0);
         float c1 = (float)Math.cos((double)a1);
         float s1 = (float)Math.sin((double)a1);
         float in = radius - thickness;
         bb.vertex(mat, x + c0 * in, y, z + s0 * in).color(color);
         bb.vertex(mat, x + c0 * radius, y, z + s0 * radius).color(color);
         bb.vertex(mat, x + c1 * radius, y, z + s1 * radius).color(color);
         bb.vertex(mat, x + c0 * in, y, z + s0 * in).color(color);
         bb.vertex(mat, x + c1 * radius, y, z + s1 * radius).color(color);
         bb.vertex(mat, x + c1 * in, y, z + s1 * in).color(color);
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.world == null) {
         return;
      }

      // в грозу время от времени бьёт молния — её рисует шейдер неба «Атмосфера»
      boolean storm = this.weather.is("Гроза");
      java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
      if (storm && (!this.wasStorm || rnd.nextInt(150) == 0)) {
         // чаще в той стороне, куда смотришь
         float yaw = (float)Math.toRadians((double)mc.gameRenderer.getCamera().getYaw());
         float view = (float)Math.atan2(Math.cos((double)yaw), -Math.sin((double)yaw));
         this.boltAzimuth = rnd.nextFloat() < 0.7F ? view + rnd.nextFloat(-0.55F, 0.55F) : rnd.nextFloat(-3.14F, 3.14F);
         this.boltSeed = rnd.nextFloat();
         this.boltAt = System.currentTimeMillis();
      }

      this.wasStorm = storm;
      float rain = this.weather.is("Ясно") ? 0.0F : this.strength.getCurrent();
      float thunder = this.weather.is("Гроза") ? this.strength.getCurrent() : 0.0F;
      float step = this.smooth.isEnabled() ? 0.02F : 1.0F;
      mc.world.setRainGradient(approach(mc.world.getRainGradient(1.0F), rain, step));
      mc.world.setThunderGradient(approach(mc.world.getThunderGradient(1.0F) / Math.max(0.01F, mc.world.getRainGradient(1.0F)), thunder, step));
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.wasStorm = false;
      if (mc.world != null) {
         // возвращаем то, что на самом деле стоит в мире
         mc.world.setRainGradient(mc.world.getLevelProperties().isRaining() ? 1.0F : 0.0F);
         mc.world.setThunderGradient(mc.world.getLevelProperties().isThundering() ? 1.0F : 0.0F);
      }
   }

   private static float approach(float from, float to, float step) {
      return from + MathHelper.clamp(to - from, -step, step);
   }

   /** Снег вместо дождя — спрашивает миксин биома. */
   public boolean forceSnow() {
      return this.isEnabled() && this.weather.is("Снег");
   }

   /** Осадки там, где их не бывает (пустыня, саванна). */
   public boolean forcePrecipitation() {
      return this.isEnabled() && this.everywhere.isEnabled() && !this.weather.is("Ясно");
   }
}
