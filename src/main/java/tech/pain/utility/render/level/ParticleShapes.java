package tech.pain.utility.render.level;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Общие фигуры для частиц (погода, эффект удара, TargetESP). Всё рисуется треугольниками
 * в формате POSITION_COLOR, координаты — относительно камеры, right/up — оси камеры.
 */
public final class ParticleShapes {
   private ParticleShapes() {
   }

   public static int argb(int r, int g, int b, float alpha) {
      int a = MathHelper.clamp((int)(alpha * 255.0F), 0, 255);
      return a << 24 | MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(b, 0, 255);
   }

   public static int withAlpha(int rgb, float alpha) {
      return MathHelper.clamp((int)(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
   }

   /** Мягкая точка: яркий центр, прозрачный край. */
   public static void softDot(BufferBuilder bb, Matrix4f mat, float x, float y, float z, Vector3f right, Vector3f up, float size, int rgb, float alpha) {
      int center = withAlpha(rgb, alpha);
      int edge = withAlpha(rgb, 0.0F);
      int segments = 8;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2832F;
         float a1 = (float)(i + 1) / (float)segments * 6.2832F;
         float c0 = MathHelper.cos(a0) * size;
         float s0 = MathHelper.sin(a0) * size;
         float c1 = MathHelper.cos(a1) * size;
         float s1 = MathHelper.sin(a1) * size;
         bb.vertex(mat, x, y, z).color(center);
         bb.vertex(mat, x + right.x * c0 + up.x * s0, y + right.y * c0 + up.y * s0, z + right.z * c0 + up.z * s0).color(edge);
         bb.vertex(mat, x + right.x * c1 + up.x * s1, y + right.y * c1 + up.y * s1, z + right.z * c1 + up.z * s1).color(edge);
      }
   }

   /** Отрезок-«лучик» в плоскости экрана: от (ax,ay) до (bx,by) в осях right/up, толщиной width. */
   private static void ray(BufferBuilder bb, Matrix4f mat, float x, float y, float z, Vector3f right, Vector3f up, float ax, float ay, float bx, float by, float width, int color) {
      float dx = bx - ax;
      float dy = by - ay;
      float len = (float)Math.sqrt((double)(dx * dx + dy * dy));
      if (len < 1.0E-5F) {
         return;
      }

      float nx = -dy / len * width;
      float ny = dx / len * width;
      float[][] q = new float[][]{{ax - nx, ay - ny}, {ax + nx, ay + ny}, {bx + nx, by + ny}, {bx - nx, by - ny}};
      int[] order = new int[]{0, 1, 2, 0, 2, 3};

      for (int i : order) {
         bb.vertex(mat, x + right.x * q[i][0] + up.x * q[i][1], y + right.y * q[i][0] + up.y * q[i][1], z + right.z * q[i][0] + up.z * q[i][1]).color(color);
      }
   }

   /**
    * Снежинка: шесть лучей с парой веточек на каждом и мягким свечением в центре.
    * rotation — поворот в плоскости экрана (радианы).
    */
   public static void snowflake(BufferBuilder bb, Matrix4f mat, float x, float y, float z, Vector3f right, Vector3f up, float size, float rotation, int rgb, float alpha) {
      int color = withAlpha(rgb, alpha);
      float width = size * 0.07F;
      softDot(bb, mat, x, y, z, right, up, size * 0.55F, rgb, alpha * 0.45F);

      for (int arm = 0; arm < 6; ++arm) {
         float angle = rotation + (float)arm * 1.0472F;
         float cos = MathHelper.cos(angle);
         float sin = MathHelper.sin(angle);
         ray(bb, mat, x, y, z, right, up, 0.0F, 0.0F, cos * size, sin * size, width, color);

         // две пары веточек: ближе к центру длиннее, у кончика короче
         for (int k = 0; k < 2; ++k) {
            float at = k == 0 ? 0.45F : 0.72F;
            float branch = size * (k == 0 ? 0.32F : 0.2F);
            float bx = cos * size * at;
            float by = sin * size * at;

            for (int side = -1; side <= 1; side += 2) {
               float ba = angle + (float)side * 1.0472F;
               ray(bb, mat, x, y, z, right, up, bx, by, bx + MathHelper.cos(ba) * branch, by + MathHelper.sin(ba) * branch, width * 0.85F, color);
            }
         }
      }
   }

   /** Цвет радуги по фазе 0..1. */
   public static int rainbow(float phase, float saturation, float brightness) {
      return java.awt.Color.HSBtoRGB(phase - (float)Math.floor((double)phase), saturation, brightness) & 0xFFFFFF;
   }
}
