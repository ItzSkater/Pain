package tech.pain.client.modules.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import tech.pain.Pain;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;

/**
 * Своё небо вместо ванильного: панорама 360° на куполе вокруг камеры.
 * Встроены Deep Space (8K, с планетами) и Inside Galaxy, плюс Black Void — чёрное небо со звёздами.
 * Свою картинку можно положить в Pain/sky: панорама 2:1 ложится одним оборотом, обычная — зеркальной плиткой.
 */
@ModuleAnnotation(
   name = "SkyShader",
   category = Category.RENDER,
   description = "Своё небо: Deep Space, Inside Galaxy, Black Void или твоя панорама"
)
public final class SkyShader extends Module {
   public static final SkyShader INSTANCE = new SkyShader();
   private final ModeSetting sky = new ModeSetting("Небо", "Атмосфера", "Deep Space", "Inside Galaxy", "Snow Field", "Black Void", "Свой файл");
   private final NumberSetting cloudiness = new NumberSetting("Облачность", 0.5F, 0.0F, 1.0F, 0.05F, (Supplier<Boolean>)() -> this.sky.is("Атмосфера"));
   private final ModeSetting cloudQuality = new ModeSetting("Качество облаков", (Supplier<Boolean>)() -> this.sky.is("Атмосфера"), "Среднее", "Низкое", "Высокое");
   private final BooleanSetting glare = new BooleanSetting("Блик солнца", true, () -> this.sky.is("Атмосфера"));
   private float glareNow;
   private long glareFrame;
   private final ModeSetting tint = new ModeSetting("Цвет", "Оригинал", "Тема", "Радуга", "Свой");
   private final ColorSetting custom = new ColorSetting("Свой цвет", new ColorRGBA(150, 110, 255), (Supplier<Boolean>)() -> this.tint.is("Свой"));
   private final NumberSetting brightness = new NumberSetting("Яркость", 1.0F, 0.2F, 1.0F, 0.05F);
   private final NumberSetting rotation = new NumberSetting("Вращение", 0.3F, 0.0F, 3.0F, 0.05F);
   private final BooleanSetting stars = new BooleanSetting("Мерцающие звёзды", true);
   private final BooleanSetting hideBodies = new BooleanSetting("Скрыть солнце, луну и звёзды игры", true);
   private final Map<String, Sky> skies = new HashMap<>();
   private float[] starData;
   private long customChecked;
   private String customStamp = "";
   private String lastError = "";

   private SkyShader() {
   }

   public boolean hideCelestial() {
      return this.hideBodies.isEnabled();
   }

   /** Текстура неба: картинка декодируется в фоне, в видеопамять загружается уже в потоке рендера. */
   private static final class Sky {
      volatile ByteBuffer pixels;
      volatile int width;
      volatile int height;
      volatile boolean failed;
      int texture = -1;
      boolean panorama;
   }

   private Sky bundled(String name) {
      return this.skies.computeIfAbsent(name, n -> {
         Sky s = new Sky();
         s.panorama = true;
         this.decodeAsync(s, () -> {
            try (InputStream in = SkyShader.class.getResourceAsStream("/assets/pain/textures/sky/" + n + ".jpg")) {
               return in == null ? null : in.readAllBytes();
            }
         });
         return s;
      });
   }

   private interface BytesSource {
      byte[] read() throws Exception;
   }

   private void decodeAsync(Sky sky, BytesSource source) {
      Thread t = new Thread(() -> {
         ByteBuffer file = null;

         try (MemoryStack stack = MemoryStack.stackPush()) {
            byte[] bytes = source.read();
            if (bytes == null) {
               sky.failed = true;
               return;
            }

            file = MemoryUtil.memAlloc(bytes.length);
            file.put(bytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            ByteBuffer decoded = STBImage.stbi_load_from_memory(file, w, h, c, 3);
            if (decoded == null) {
               sky.failed = true;
               return;
            }

            sky.width = w.get(0);
            sky.height = h.get(0);
            sky.pixels = decoded;
         } catch (Throwable e) {
            sky.failed = true;
         } finally {
            if (file != null) {
               MemoryUtil.memFree(file);
            }
         }
      }, "Pain-Sky-Decode");
      t.setDaemon(true);
      t.start();
   }

   /** Свой файл: первая картинка (png/jpg) из папки Pain/sky; проверяется раз в 5 секунд. */
   private Sky customSky() {
      long now = System.currentTimeMillis();
      if (now - this.customChecked > 5000L) {
         this.customChecked = now;

         try {
            Path dir = mc.runDirectory.toPath().resolve("Pain").resolve("sky");
            Files.createDirectories(dir);
            Path file;

            try (Stream<Path> files = Files.list(dir)) {
               file = files.filter(f -> {
                  String n = f.getFileName().toString().toLowerCase();
                  return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
               }).sorted().findFirst().orElse(null);
            }

            if (file == null) {
               this.report("положи панораму (png/jpg) в " + dir);
            } else {
               String stamp = file + "|" + Files.getLastModifiedTime(file);
               if (!stamp.equals(this.customStamp)) {
                  this.customStamp = stamp;
                  Sky old = this.skies.remove("custom");
                  if (old != null && old.texture != -1) {
                     GL11.glDeleteTextures(old.texture);
                  }

                  Sky s = new Sky();
                  final Path target = file;
                  this.decodeAsync(s, () -> Files.readAllBytes(target));
                  this.skies.put("custom", s);
               }
            }
         } catch (Exception e) {
            this.report(e.toString());
         }
      }

      return this.skies.get("custom");
   }

   private void report(String message) {
      if (!message.equals(this.lastError)) {
         this.lastError = message;
         Pain.getInstance().getNotifyManager().addNotification("", net.minecraft.text.Text.of("SkyShader: " + message));
      }
   }

   /** Загружает декодированную картинку в видеопамять. false — ещё не готова. */
   private boolean ready(Sky sky) {
      if (sky == null || sky.failed) {
         return false;
      }

      if (sky.texture != -1) {
         return true;
      }

      ByteBuffer pixels = sky.pixels;
      if (pixels == null) {
         return false;
      }

      int max = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
      if (sky.width > max || sky.height > max) {
         this.report("картинка больше, чем тянет видеокарта (" + max + " px)");
         sky.failed = true;
         STBImage.stbi_image_free(pixels);
         sky.pixels = null;
         return false;
      }

      // обычная картинка — зеркальная плитка; панорама 2:1 — один оборот
      float aspect = (float)sky.width / (float)sky.height;
      sky.panorama = sky.panorama || aspect > 1.9F && aspect < 2.1F;
      sky.texture = GL11.glGenTextures();
      GlStateManager._bindTexture(sky.texture);
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, sky.width, sky.height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);
      GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, sky.panorama ? GL11.GL_REPEAT : GL14.GL_MIRRORED_REPEAT);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, sky.panorama ? GL12.GL_CLAMP_TO_EDGE : GL14.GL_MIRRORED_REPEAT);
      STBImage.stbi_image_free(pixels);
      sky.pixels = null;
      return true;
   }

   /** Вызывается из миксина на месте ванильного неба; матрица вида уже повёрнута как камера. */
   public void drawDome() {
      if (this.sky.is("Атмосфера") && this.drawAtmosphere()) {
         return;
      }

      double time = (double)System.currentTimeMillis() / 1000.0;
      float spin = (float)(time * 0.012 * (double)this.rotation.getCurrent() % (Math.PI * 2.0));
      Matrix4f mat = new Matrix4f().rotateY(spin).rotateX(this.sky.is("Snow Field") ? 0.0F : 0.3F); // небо с горизонтом не наклоняем
      Sky sky = null;
      if (this.sky.is("Deep Space")) {
         sky = this.bundled("deep_space");
      } else if (this.sky.is("Inside Galaxy")) {
         sky = this.bundled("inside_galaxy");
      } else if (this.sky.is("Snow Field")) {
         sky = this.bundled("snow_field");
      } else if (this.sky.is("Свой файл")) {
         sky = this.customSky();
      }

      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      boolean textured = this.ready(sky);
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      ColorRGBA c1 = theme.getColor();
      ColorRGBA c2 = theme.getSecondColor();
      float bright = this.brightness.getCurrent();
      int rings = 32;
      int segments = 64;
      float radius = 100.0F;
      Vector3f p = new Vector3f();
      BufferBuilder bb;
      if (textured) {
         RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
         RenderSystem.setShaderTexture(0, sky.texture);
         bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      } else {
         // Black Void или картинка ещё грузится — просто чёрный купол
         RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
         bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      }

      float uRep = textured && !sky.panorama ? 2.0F : 1.0F;

      for (int i = 0; i < rings; ++i) {
         for (int j = 0; j < segments; ++j) {
            for (int k = 0; k < 4; ++k) {
               int ri = i + (k == 1 || k == 2 ? 1 : 0);
               int sj = j + (k >= 2 ? 1 : 0);
               float polar = (float)ri / (float)rings;
               float around = (float)sj / (float)segments;
               float theta = polar * 3.1415927F;
               float phi = around * 6.2831855F;
               p.set(MathHelper.sin(theta) * MathHelper.cos(phi), MathHelper.cos(theta), MathHelper.sin(theta) * MathHelper.sin(phi)).mul(radius);
               mat.transformPosition(p);
               if (textured) {
                  bb.vertex(p.x, p.y, p.z).texture(around * uRep, polar).color(this.color(polar, around, c1, c2, bright, time));
               } else {
                  bb.vertex(p.x, p.y, p.z).color(0xFF000000);
               }
            }
         }
      }

      BufferRenderer.drawWithGlobalProgram(bb.end());
      if (this.stars.isEnabled() && (this.sky.is("Black Void") || !textured)) {
         this.drawStars(mat, time, c1, bright);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
   }

   /** Блик в объективе: когда солнце в кадре и ничем не закрыто, от него по экрану расходится мягкое зарево и цепочка бликов. */
   @com.darkmagician6.eventapi.EventTarget
   public void onRender2D(tech.pain.base.events.impl.render.EventRender2D event) {
      if (mc.world == null || mc.player == null || !this.sky.is("Атмосфера") || !this.glare.isEnabled() || mc.options.hudHidden) {
         return;
      }

      long now = System.currentTimeMillis();
      float dt = Math.min(0.1F, (float)(now - this.glareFrame) / 1000.0F);
      this.glareFrame = now;
      float pt = event.getTickDelta();
      float angle = mc.world.getSkyAngle(pt) * 6.2831855F;
      net.minecraft.util.math.Vec3d sun = new net.minecraft.util.math.Vec3d((double)(-MathHelper.sin(angle)), (double)MathHelper.cos(angle), 0.0);
      net.minecraft.util.math.Vec3d cam = mc.gameRenderer.getCamera().getPos();
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      net.minecraft.util.math.Vec3d screen = tech.pain.utility.math.ProjectionUtil.worldSpaceToScreenSpace(cam.add(sun.multiply(60.0)));
      float sx = (float)screen.x;
      float sy = (float)screen.y;
      boolean inFrame = screen.z > 0.0 && screen.z < 1.0 && sx > -sw * 0.15F && sx < sw * 1.15F && sy > -sh * 0.15F && sy < sh * 1.15F;
      float want = 0.0F;
      if (inFrame && sun.y > -0.04) {
         // солнце закрыто блоком — блика нет
         net.minecraft.util.hit.BlockHitResult hit = mc.world.raycast(new net.minecraft.world.RaycastContext(cam, cam.add(sun.multiply(128.0)), net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
         if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            want = (1.0F - mc.world.getRainGradient(pt)) * smooth(-0.04F, 0.06F, (float)sun.y);
         }
      }

      this.glareNow += (want - this.glareNow) * Math.min(1.0F, dt * 7.0F);
      if (this.glareNow < 0.01F) {
         return;
      }

      float high = smooth(0.0F, 0.45F, (float)sun.y);
      int tint = tech.pain.utility.render.level.ParticleShapes.argb(255, (int)(128.0F + 117.0F * high), (int)(51.0F + 173.0F * high), 0.0F) & 0xFFFFFF;
      float k = this.glareNow * this.brightness.getCurrent();
      Matrix4f mat = event.getContext().getMatrices().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      glow(bb, mat, sx, sy, sh * 0.8F, tint, 0.11F * k);
      glow(bb, mat, sx, sy, sh * 0.3F, tint, 0.24F * k);
      glow(bb, mat, sx, sy, sh * 0.07F, 0xFFFFFF, 0.55F * k);
      // блики лежат на прямой от солнца через центр экрана
      float cx = sw * 0.5F;
      float cy = sh * 0.5F;
      float[] along = new float[]{0.45F, 0.8F, 1.25F, 1.6F, 2.05F};
      float[] size = new float[]{0.035F, 0.02F, 0.055F, 0.028F, 0.09F};
      int[] hue = new int[]{0xFFB070, 0x80C8FF, 0xFFD890, 0xB0FFC8, 0xFF9080};

      for (int i = 0; i < along.length; ++i) {
         glow(bb, mat, sx + (cx - sx) * along[i], sy + (cy - sy) * along[i], sh * size[i], hue[i], 0.11F * k);
      }

      net.minecraft.client.render.BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
   }

   /** Мягкое пятно: яркий центр, край уходит в ноль. */
   private static void glow(BufferBuilder bb, Matrix4f mat, float x, float y, float radius, int rgb, float alpha) {
      int centre = tech.pain.utility.render.level.ParticleShapes.withAlpha(rgb, Math.min(1.0F, alpha));
      int edge = tech.pain.utility.render.level.ParticleShapes.withAlpha(rgb, 0.0F);
      int segments = 40;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2831855F;
         float a1 = (float)(i + 1) / (float)segments * 6.2831855F;
         bb.vertex(mat, x, y, 0.0F).color(centre);
         bb.vertex(mat, x + MathHelper.cos(a0) * radius, y + MathHelper.sin(a0) * radius, 0.0F).color(edge);
         bb.vertex(mat, x + MathHelper.cos(a1) * radius, y + MathHelper.sin(a1) * radius, 0.0F).color(edge);
      }
   }

   /** Цвет горизонта «Атмосферы» — тем же расчётом, что в шейдере; им красится туман, чтобы даль сливалась с небом. */
   public float[] horizonColor(float pt) {
      float sunH = MathHelper.cos(mc.world.getSkyAngle(pt) * 6.2831855F);
      float day = smooth(-0.18F, 0.22F, sunH);
      float dusk = (1.0F - smooth(0.0F, 0.42F, Math.abs(sunH))) * smooth(-0.3F, 0.0F, sunH);
      float rain = mc.world.getRainGradient(pt) * 0.85F;
      float[] night = new float[]{0.035F, 0.045F, 0.085F};
      float[] noon = new float[]{0.62F, 0.78F, 0.96F};
      float[] warm = new float[]{0.97F, 0.44F, 0.38F};
      float[] out = new float[3];

      for (int i = 0; i < 3; ++i) {
         float c = night[i] + (noon[i] - night[i]) * day;
         c += (warm[i] - c) * dusk * 0.45F;
         float grey = (i == 0 ? 0.03F : i == 1 ? 0.035F : 0.045F) + ((i == 0 ? 0.50F : i == 1 ? 0.54F : 0.60F) - (i == 0 ? 0.03F : i == 1 ? 0.035F : 0.045F)) * day;
         out[i] = c + (grey - c) * rain;
      }

      return out;
   }

   private static float smooth(float a, float b, float x) {
      float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   /** В «Атмосфере» облака рисует шейдер, ванильные плоские убирает CloudRendererMixin. */
   public boolean ownClouds() {
      return this.isEnabled() && this.sky.is("Атмосфера");
   }

   /** Небо, которое целиком считает шейдер sky: солнце, луна, звёзды, облака, пасмурность в дождь. */
   private boolean drawAtmosphere() {
      tech.pain.utility.render.display.shader.GlProgram program = tech.pain.utility.render.display.shader.DrawUtil.skyProgram;
      if (mc.world == null || program == null || program.use() == null) {
         return false;
      }

      float pt = mc.getRenderTickCounter().getTickDelta(false);
      // солнце в игре ходит по кругу в плоскости восток-запад
      float angle = mc.world.getSkyAngle(pt) * 6.2831855F;
      float rain = mc.world.getRainGradient(pt);
      float flash = mc.world.getLightningTicksLeft() > 0 ? 0.35F : 0.0F;
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      program.use();
      program.findUniform("SunDir").set(-MathHelper.sin(angle), MathHelper.cos(angle), 0.0F);
      program.findUniform("Time").set((float)(System.currentTimeMillis() % 3600000L) / 1000.0F * (0.3F + this.rotation.getCurrent()));
      program.findUniform("Rain").set(rain);
      program.findUniform("Clouds").set(this.cloudiness.getCurrent());
      program.findUniform("Steps").set(this.cloudQuality.is("Низкое") ? 10.0F : this.cloudQuality.is("Высокое") ? 26.0F : 16.0F);
      float bolt = WeatherControl.INSTANCE.boltFlash();
      program.findUniform("Flash").set(Math.max(flash, bolt * 0.22F));
      program.findUniform("Bolt").set(WeatherControl.INSTANCE.boltAzimuth(), WeatherControl.INSTANCE.boltSeed(), bolt);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      // куб вокруг камеры: шейдеру нужно только направление взгляда
      float r = 100.0F;
      float[][] faces = new float[][]{
         {-r, r, -r, r, r, -r, r, r, r, -r, r, r}, {-r, -r, -r, -r, -r, r, r, -r, r, r, -r, -r},
         {-r, -r, -r, r, -r, -r, r, r, -r, -r, r, -r}, {-r, -r, r, -r, r, r, r, r, r, r, -r, r},
         {-r, -r, -r, -r, r, -r, -r, r, r, -r, -r, r}, {r, -r, -r, r, -r, r, r, r, r, r, r, -r}
      };

      for (float[] f : faces) {
         for (int i = 0; i < 4; ++i) {
            bb.vertex(f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(-1);
         }
      }

      BufferRenderer.drawWithGlobalProgram(bb.end());
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      return true;
   }

   /** Свои звёзды для «пустоты»: маленькие квадратики на сфере, каждая мерцает в своём ритме. */
   private void drawStars(Matrix4f mat, double time, ColorRGBA accent, float bright) {
      int count = 1400;
      if (this.starData == null) {
         Random rnd = new Random(10842L);
         this.starData = new float[count * 5];

         for (int i = 0; i < count; ++i) {
            // равномерно по сфере
            float y = rnd.nextFloat() * 2.0F - 1.0F;
            float a = rnd.nextFloat() * 6.2831855F;
            float r = (float)Math.sqrt((double)(1.0F - y * y));
            this.starData[i * 5] = r * MathHelper.cos(a);
            this.starData[i * 5 + 1] = y;
            this.starData[i * 5 + 2] = r * MathHelper.sin(a);
            float s = rnd.nextFloat();
            this.starData[i * 5 + 3] = 0.1F + s * s * s * 0.42F;
            this.starData[i * 5 + 4] = rnd.nextFloat() * 6.2831855F;
         }
      }

      RenderSystem.enableBlend();
      RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      Vector3f c = new Vector3f();
      Vector3f right = new Vector3f();
      Vector3f up = new Vector3f();
      boolean themed = this.tint.is("Тема");

      for (int i = 0; i < count; ++i) {
         c.set(this.starData[i * 5], this.starData[i * 5 + 1], this.starData[i * 5 + 2]);
         float size = this.starData[i * 5 + 3];
         float twinkle = 0.55F + 0.45F * (float)Math.sin(time * (0.6 + (double)(i % 7) * 0.25) + (double)this.starData[i * 5 + 4]);
         // базис в касательной плоскости к сфере
         right.set(-c.z, 0.0F, c.x);
         if (right.lengthSquared() < 1.0E-4F) {
            right.set(1.0F, 0.0F, 0.0F);
         }

         right.normalize().mul(size);
         up.set(c).cross(right).normalize().mul(size);
         c.mul(98.0F);
         int alpha = MathHelper.clamp((int)(255.0F * twinkle * bright), 0, 255);
         int rgb = themed && i % 3 == 0 ? accent.getRGB() & 0xFFFFFF : (i % 11 == 0 ? 0xBFD4FF : (i % 13 == 0 ? 0xFFE2B8 : 0xFFFFFF));
         int color = alpha << 24 | rgb;

         for (int k = 0; k < 4; ++k) {
            float sx = k == 0 || k == 1 ? -1.0F : 1.0F;
            float sy = k == 0 || k == 3 ? -1.0F : 1.0F;
            p(bb, mat, c.x + right.x * sx + up.x * sy, c.y + right.y * sx + up.y * sy, c.z + right.z * sx + up.z * sy, color);
         }
      }

      BufferRenderer.drawWithGlobalProgram(bb.end());
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
   }

   private static void p(BufferBuilder bb, Matrix4f mat, float x, float y, float z, int color) {
      Vector3f v = mat.transformPosition(new Vector3f(x, y, z));
      bb.vertex(v.x, v.y, v.z).color(color);
   }

   /** Подкраска: цвет вершины умножается на цвет пикселя панорамы. */
   private int color(float polar, float around, ColorRGBA c1, ColorRGBA c2, float bright, double time) {
      float r;
      float g;
      float b;
      if (this.tint.is("Тема")) {
         // от основного цвета темы в зените ко второму внизу, слегка высветлено, чтобы не съедало детали
         float t = MathHelper.clamp(polar * 1.3F, 0.0F, 1.0F);
         r = MathHelper.lerp(t, (float)c1.getRed(), (float)c2.getRed()) / 255.0F * 0.7F + 0.3F;
         g = MathHelper.lerp(t, (float)c1.getGreen(), (float)c2.getGreen()) / 255.0F * 0.7F + 0.3F;
         b = MathHelper.lerp(t, (float)c1.getBlue(), (float)c2.getBlue()) / 255.0F * 0.7F + 0.3F;
      } else if (this.tint.is("Радуга")) {
         ColorRGBA c = ColorRGBA.fromHSB((float)((double)around + (double)polar * 0.3 + time * 0.02) % 1.0F, 0.5F, 1.0F);
         r = (float)c.getRed() / 255.0F;
         g = (float)c.getGreen() / 255.0F;
         b = (float)c.getBlue() / 255.0F;
      } else if (this.tint.is("Свой")) {
         ColorRGBA c = this.custom.getColor();
         r = (float)c.getRed() / 255.0F;
         g = (float)c.getGreen() / 255.0F;
         b = (float)c.getBlue() / 255.0F;
      } else {
         r = g = b = 1.0F;
      }

      int ri = MathHelper.clamp((int)(r * bright * 255.0F), 0, 255);
      int gi = MathHelper.clamp((int)(g * bright * 255.0F), 0, 255);
      int bi = MathHelper.clamp((int)(b * bright * 255.0F), 0, 255);
      return 0xFF000000 | ri << 16 | gi << 8 | bi;
   }
}
