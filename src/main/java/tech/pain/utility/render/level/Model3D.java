package tech.pain.utility.render.level;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tech.pain.utility.render.display.base.color.ColorRGBA;

/**
 * Простая 3D-модель: треугольники с нормалями и цветом вершин (формат .pmodel, см. tools/pmodel_export.py).
 * Свои FBX/OBJ/GLB конвертируются через установленный Blender.
 */
public final class Model3D {
   private static final Map<String, Model3D> BUNDLED = new ConcurrentHashMap<>();
   private final float[] data;
   private final int vertices;

   private Model3D(float[] data, int vertices) {
      this.data = data;
      this.vertices = vertices;
   }

   public int triangles() {
      return this.vertices / 3;
   }

   public static Model3D bundled(String name) {
      return BUNDLED.computeIfAbsent(name, n -> {
         try (InputStream in = Model3D.class.getResourceAsStream("/assets/pain/models/" + n + ".pmodel")) {
            return in == null ? null : read(in);
         } catch (Exception e) {
            e.printStackTrace();
            return null;
         }
      });
   }

   public static Model3D load(Path file) throws Exception {
      try (InputStream in = Files.newInputStream(file)) {
         return read(in);
      }
   }

   private static Model3D read(InputStream in) throws Exception {
      List<float[]> rows = new ArrayList<>();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
         String line;
         while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') {
               continue;
            }

            String[] parts = line.trim().split("\\s+");
            if (parts.length < 9) {
               continue;
            }

            float[] row = new float[9];

            for (int i = 0; i < 9; ++i) {
               row[i] = Float.parseFloat(parts[i]);
            }

            rows.add(row);
         }
      }

      int count = rows.size() / 3 * 3;
      float[] data = new float[count * 9];

      for (int i = 0; i < count; ++i) {
         System.arraycopy(rows.get(i), 0, data, i * 9, 9);
      }

      return new Model3D(data, count);
   }

   /**
    * Конвертирует FBX/OBJ/GLB в .pmodel через Blender. Блокирует поток — вызывать не из рендера.
    * @return сообщение об ошибке или null
    */
   public static String convert(Path source, Path target, Path toolsDir) {
      try {
         Files.createDirectories(toolsDir);

         for (String script : new String[]{"pmodel_export.py", "convert_fbx.py"}) {
            try (InputStream in = Model3D.class.getResourceAsStream("/assets/pain/models/" + script)) {
               if (in == null) {
                  return "скрипт " + script + " не найден в jar";
               }

               Files.copy(in, toolsDir.resolve(script), StandardCopyOption.REPLACE_EXISTING);
            }
         }

         File log = toolsDir.resolve("convert.log").toFile();
         Process p = new ProcessBuilder("blender", "-b", "--python", toolsDir.resolve("convert_fbx.py").toString(), "--", source.toString(), target.toString()).redirectErrorStream(true).redirectOutput(log).start();
         if (!p.waitFor(120L, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "Blender не ответил за 2 минуты";
         }

         return Files.exists(target) && Files.size(target) > 0L ? null : "Blender не смог прочитать файл, лог: " + log;
      } catch (java.io.IOException e) {
         return "нужен установленный Blender (" + e.getMessage() + ")";
      } catch (Exception e) {
         return e.toString();
      }
   }

   /**
    * Рисует модель в текущей матрице (единичный размер, центр в нуле).
    * Свет считается на процессоре: «фонарик» от камеры сверху-слева, чтобы был виден объём.
    * @param tint цвет подкраски или null; amount — доля подкраски 0..1
    */
   public void draw(MatrixStack ms, float alpha, ColorRGBA tint, float amount) {
      if (this.vertices == 0 || alpha <= 0.01F) {
         return;
      }

      Matrix4f mat = ms.peek().getPositionMatrix();
      Matrix3f normalMat = new Matrix3f(mat).invert().transpose();
      Vector3f light = new Vector3f(-0.35F, 0.75F, 0.55F).normalize();
      Vector3f n = new Vector3f();
      float tr = tint == null ? 0.0F : (float)tint.getRed() / 255.0F;
      float tg = tint == null ? 0.0F : (float)tint.getGreen() / 255.0F;
      float tb = tint == null ? 0.0F : (float)tint.getBlue() / 255.0F;
      float k = tint == null ? 0.0F : amount;
      int a = Math.min(255, Math.max(0, (int)(alpha * 255.0F)));
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(true);
      RenderSystem.disableCull();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

      for (int i = 0; i < this.vertices; ++i) {
         int o = i * 9;
         n.set(this.data[o + 3], this.data[o + 4], this.data[o + 5]).mul(normalMat).normalize();
         float diffuse = Math.abs(n.dot(light));
         // блик от камеры: грани, повёрнутые к зрителю, чуть светлее
         float shade = 0.42F + 0.5F * diffuse + 0.12F * Math.max(0.0F, n.z);
         float r = (this.data[o + 6] * (1.0F - k) + tr * k) * shade;
         float g = (this.data[o + 7] * (1.0F - k) + tg * k) * shade;
         float b = (this.data[o + 8] * (1.0F - k) + tb * k) * shade;
         int color = a << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
         bb.vertex(mat, this.data[o], this.data[o + 1], this.data[o + 2]).color(color);
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   private static int clamp(float v) {
      return Math.min(255, Math.max(0, (int)(v * 255.0F)));
   }
}
