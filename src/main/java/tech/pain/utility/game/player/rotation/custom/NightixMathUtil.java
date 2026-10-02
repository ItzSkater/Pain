package tech.pain.utility.game.player.rotation.custom;

import java.security.SecureRandom;
import net.minecraft.util.math.MathHelper;
public final class NightixMathUtil {
   private static final SecureRandom SECURE_RANDOM = new SecureRandom();

   private NightixMathUtil() {
   }

   public static float random(double min, double max) {
      return (float)(min + (max - min) * Math.random());
   }

   public static float random1(float min, float max) {
      return (float)(Math.random() * (max - min) + min);
   }

   public static float randomLerp(float min, float max) {
      return lerp(max, min, SECURE_RANDOM.nextFloat());
   }

   public static int randomInt(int min, int max) {
      return min + (int)(Math.random() * (max - min + 1));
   }

   public static float lerp(float input, float target, double step) {
      return (float)(input + step * ((double)target - input));
   }

   public static double clamp(double value, double min, double max) {
      return MathHelper.clamp(value, min, max);
   }
}
