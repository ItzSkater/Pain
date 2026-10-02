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
   private final ModeSetting sky = new ModeSetting("Небо", "Deep Space", "Inside Galaxy", "Black Void", "Свой файл");
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
      double time = (double)System.currentTimeMillis() / 1000.0;
      float spin = (float)(time * 0.012 * (double)this.rotation.getCurrent() % (Math.PI * 2.0));
      Matrix4f mat = new Matrix4f().rotateY(spin).rotateX(0.3F);
      Sky sky = null;
      if (this.sky.is("Deep Space")) {
         sky = this.bundled("deep_space");
      } else if (this.sky.is("Inside Galaxy")) {
         sky = this.bundled("inside_galaxy");
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
