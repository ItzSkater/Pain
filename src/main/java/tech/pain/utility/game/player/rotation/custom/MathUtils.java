package tech.pain.utility.game.player.rotation.custom;

import java.util.concurrent.ThreadLocalRandom;

public final class MathUtils {
   public static double randomBest(double min, double max) {
      return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
   }

   private MathUtils() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
