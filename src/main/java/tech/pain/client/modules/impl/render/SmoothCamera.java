package tech.pain.client.modules.impl.render;

import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;

@ModuleAnnotation(
   name = "SmoothCamera",
   category = Category.RENDER,
   description = "Плавная (кинематографичная) камера"
)
public final class SmoothCamera extends Module {
   public static final SmoothCamera INSTANCE = new SmoothCamera();

   private boolean previous;

   private SmoothCamera() {
   }

   public void onEnable() {
      super.onEnable();
      if (mc.options != null) {
         this.previous = mc.options.smoothCameraEnabled;
         mc.options.smoothCameraEnabled = true;
      }
   }

   public void onDisable() {
      super.onDisable();
      if (mc.options != null) {
         mc.options.smoothCameraEnabled = this.previous;
      }
   }
}
