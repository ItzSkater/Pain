package tech.pain.utility.game.player.rotation.custom;

import tech.pain.utility.interfaces.IMinecraft;
public class GCDUtil implements IMinecraft {
   public static float getFixedRotation(float rot) {
      return getDeltaMouse(rot) * getGCDValue();
   }

   public static float getGCDValue() {
      return (float)(getGCD() * 0.15);
   }

   public static float getGCD() {
      double var0 = 0.5;
      if (mc != null && mc.options != null) {
         var0 = (Double)mc.options.getMouseSensitivity().getValue();
      }

      double var2 = var0 * 0.6F + 0.2F;
      return (float)(var2 * var2 * var2 * 8.0);
   }

   public static float getDeltaMouse(float delta) {
      return Math.round(delta / getGCDValue());
   }

   public static float getFixedDelta(float rot, float last) {
      float gcd = getGCDValue();
      float delta = rot - last;
      return last + Math.round(delta / gcd) * gcd;
   }
}
