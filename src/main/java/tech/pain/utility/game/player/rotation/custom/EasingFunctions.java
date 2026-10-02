package tech.pain.utility.game.player.rotation.custom;

import lombok.Generated;
public final class EasingFunctions {
   public static final double O00000000 = 1.70158;
   public static final double O000000000 = 2.5949095;
   public static final double O0000000000 = 2.70158;
   public static final double O00000000000 = Math.PI * 2.0 / 3.0;
   public static final double O000000000000 = Math.PI * 4.0 / 9.0;
   public static final EasingFunction linear = var0 -> var0;
   public static final EasingFunction quadIn = powerIn(2);
   public static final EasingFunction quadOut = powerOut(2);
   public static final EasingFunction quadInOut = powerInOut(2.0);
   public static final EasingFunction cubicIn = powerIn(3);
   public static final EasingFunction cubicOut = powerOut(3);
   public static final EasingFunction cubicInOut = powerInOut(3.0);
   public static final EasingFunction quartIn = powerIn(4);
   public static final EasingFunction quartOut = powerOut(4);
   public static final EasingFunction quartInOut = powerInOut(4.0);
   public static final EasingFunction quintIn = powerIn(5);
   public static final EasingFunction quintOut = powerOut(5);
   public static final EasingFunction quintInOut = powerInOut(5.0);
   public static final EasingFunction sineIn = var0 -> 1.0 - Math.cos(var0 * Math.PI / 2.0);
   public static final EasingFunction sineOut = var0 -> Math.sin(var0 * Math.PI / 2.0);
   public static final EasingFunction sineInOut = var0 -> -(Math.cos(Math.PI * var0) - 1.0) / 2.0;
   public static final EasingFunction circIn = var0 -> 1.0 - Math.sqrt(1.0 - Math.pow(var0, 2.0));
   public static final EasingFunction circOut = var0 -> Math.sqrt(1.0 - Math.pow(var0 - 1.0, 2.0));
   public static final EasingFunction circInOut = var0 -> var0 < 0.5
      ? (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * var0, 2.0))) / 2.0
      : (Math.sqrt(1.0 - Math.pow(-2.0 * var0 + 2.0, 2.0)) + 1.0) / 2.0;
   public static final EasingFunction elasticIn = var0 -> var0 != 0.0 && var0 != 1.0
      ? Math.pow(-2.0, 10.0 * var0 - 10.0) * Math.sin((var0 * 10.0 - 10.75) * (Math.PI * 2.0 / 3.0))
      : var0;
   public static final EasingFunction elasticOut = var0 -> var0 != 0.0 && var0 != 1.0
      ? Math.pow(2.0, -10.0 * var0) * Math.sin((var0 * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0
      : var0;
   public static final EasingFunction elasticInOut = var0 -> {
      if (var0 != 0.0 && var0 != 1.0) {
         return var0 < 0.5
            ? -(Math.pow(2.0, 20.0 * var0 - 10.0) * Math.sin((20.0 * var0 - 11.125) * (Math.PI * 4.0 / 9.0))) / 2.0
            : Math.pow(2.0, -20.0 * var0 + 10.0) * Math.sin((20.0 * var0 - 11.125) * (Math.PI * 4.0 / 9.0)) / 2.0 + 1.0;
      } else {
         return var0;
      }
   };
   public static final EasingFunction expoIn = var0 -> var0 != 0.0 ? Math.pow(2.0, 10.0 * var0 - 10.0) : var0;
   public static final EasingFunction expoOut = var0 -> var0 != 1.0 ? 1.0 - Math.pow(2.0, -10.0 * var0) : var0;
   public static final EasingFunction expoInOut = var0 -> {
      if (var0 != 0.0 && var0 != 1.0) {
         return var0 < 0.5 ? Math.pow(2.0, 20.0 * var0 - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * var0 + 10.0)) / 2.0;
      } else {
         return var0;
      }
   };
   public static final EasingFunction backIn = var0 -> 2.70158 * Math.pow(var0, 3.0) - 1.70158 * Math.pow(var0, 2.0);
   public static final EasingFunction backOut = var0 -> 1.0 + 2.70158 * Math.pow(var0 - 1.0, 3.0) + 1.70158 * Math.pow(var0 - 1.0, 2.0);
   public static final EasingFunction backInOut = var0 -> var0 < 0.5
      ? Math.pow(2.0 * var0, 2.0) * (7.189819 * var0 - 2.5949095) / 2.0
      : (Math.pow(2.0 * var0 - 2.0, 2.0) * (3.5949095 * (var0 * 2.0 - 2.0) + 2.5949095) + 2.0) / 2.0;
   public static final EasingFunction bounceOut = var0 -> {
      double var2 = 7.5625;
      double var4 = 2.75;
      if (var0 < 1.0 / var4) {
         return var2 * Math.pow(var0, 2.0);
      } else if (var0 < 2.0 / var4) {
         return var2 * Math.pow(var0 - 1.5 / var4, 2.0) + 0.75;
      } else {
         return var0 < 2.5 / var4 ? var2 * Math.pow(var0 - 2.25 / var4, 2.0) + 0.9375 : var2 * Math.pow(var0 - 2.625 / var4, 2.0) + 0.984375;
      }
   };
   public static final EasingFunction bounceIn = var0 -> 1.0 - bounceOut.ease(1.0 - var0);
   public static final EasingFunction bounceInOut = var0 -> var0 < 0.5
      ? (1.0 - bounceOut.ease(1.0 - 2.0 * var0)) / 2.0
      : (1.0 + bounceOut.ease(2.0 * var0 - 1.0)) / 2.0;

   public static EasingFunction powerIn(double var0) {
      return var2 -> Math.pow(var2, var0);
   }

   public static EasingFunction powerIn(int var0) {
      return powerIn((double)var0);
   }

   public static EasingFunction powerOut(double var0) {
      return var2 -> 1.0 - Math.pow(1.0 - var2, var0);
   }

   public static EasingFunction powerOut(int var0) {
      return powerOut((double)var0);
   }

   public static EasingFunction powerInOut(double var0) {
      return var2 -> var2 < 0.5 ? Math.pow(2.0, var0 - 1.0) * Math.pow(var2, var0) : 1.0 - Math.pow(-2.0 * var2 + 2.0, var0) / 2.0;
   }

   @Generated
   private EasingFunctions() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
