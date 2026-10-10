package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Размытие в движении: каждый кадр мира смешивается с предыдущими, за движущимся остаётся шлейф.
 * Рука, HUD и меню рисуются позже и остаются чёткими. Сила не зависит от FPS.
 */
@ModuleAnnotation(
   name = "MotionBlur",
   category = Category.RENDER,
   description = "Размытие в движении: шлейф при поворотах камеры и за движущимися объектами"
)
public final class MotionBlur extends Module {
   public static final MotionBlur INSTANCE = new MotionBlur();
   private final NumberSetting strength = new NumberSetting("Сила", 0.6F, 0.1F, 0.95F, 0.05F);
   private long lastFrame;

   private MotionBlur() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      DrawUtil.resetMotionBlur();
      this.lastFrame = 0L;
   }

   @Override
   public void onDisable() {
      super.onDisable();
      DrawUtil.resetMotionBlur();
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      // «Сила» задана для 60 кадров в секунду; на другом FPS пересчитываем, чтобы шлейф был той же длины
      float keep = (float)Math.pow((double)this.strength.getCurrent(), (double)(dt * 60.0F));
      DrawUtil.drawMotionBlur(mc.isPaused() ? 0.0F : keep);
   }
}
