package tech.pain.client.modules.impl.render;

import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Стабилизатор камеры для вида от третьего лица (F5): камера почти не двигается по вертикали,
 * поэтому прыжки, ступеньки и падения с блока её не трясут. По горизонтали и по повороту
 * сглаживание отдельное и по умолчанию выключено. От первого лица ничего не меняет.
 * Сглаживание делает SmoothCameraMixin.
 */
@ModuleAnnotation(
   name = "SmoothCamera",
   category = Category.RENDER,
   description = "Стабилизатор камеры в F5: камера не прыгает по вертикали вместе с персонажем"
)
public final class SmoothCamera extends Module {
   public static final SmoothCamera INSTANCE = new SmoothCamera();
   // 0 — камера по вертикали стоит на месте, пока персонаж не уйдёт дальше «Макс. отрыв»
   public final NumberSetting vertical = new NumberSetting("Движение по вертикали", 1.5F, 0.0F, 20.0F, 0.5F);
   public final NumberSetting maxOffset = new NumberSetting("Макс. отрыв", 2.5F, 0.5F, 8.0F, 0.5F);
   public final BooleanSetting smoothMove = new BooleanSetting("Сглаживать движение", false);
   public final NumberSetting follow = new NumberSetting("Плавность движения", 12.0F, 2.0F, 30.0F, 1.0F);
   public final BooleanSetting smoothTurn = new BooleanSetting("Сглаживать поворот", false);
   public final NumberSetting turn = new NumberSetting("Плавность поворота", 6.0F, 1.0F, 20.0F, 0.5F);
   public final NumberSetting distance = new NumberSetting("Расстояние камеры", 4.0F, 2.0F, 10.0F, 0.5F);

   private SmoothCamera() {
   }
}
