package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import tech.pain.base.events.impl.render.EventCamera;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;

@ModuleAnnotation(
   name = "NoRender",
   category = Category.RENDER,
   description = "Убирает лишние элементы с экрана"
)
public final class NoRender extends Module {
   public static final NoRender INSTANCE = new NoRender();
   // первые четыре включены, как раньше; всё новое выключено, пока не отметишь
   private final MultiBooleanSetting settings = new MultiBooleanSetting(
      "Убрать",
      new MultiBooleanSetting.Value("Огонь", true),
      new MultiBooleanSetting.Value("Плохие эффекты", true),
      new MultiBooleanSetting.Value("Камера клип", true),
      new MultiBooleanSetting.Value("Тыква", true),
      new MultiBooleanSetting.Value("Вода на экране", false),
      new MultiBooleanSetting.Value("Блок в голове", false),
      new MultiBooleanSetting.Value("Тряска от урона", false),
      new MultiBooleanSetting.Value("Анимация тотема", false),
      new MultiBooleanSetting.Value("Портал и тошнота", false),
      new MultiBooleanSetting.Value("Виньетка", false),
      new MultiBooleanSetting.Value("Иней", false),
      new MultiBooleanSetting.Value("Подзорная труба", false),
      new MultiBooleanSetting.Value("Босс-бар", false),
      new MultiBooleanSetting.Value("Скорборд", false),
      new MultiBooleanSetting.Value("Титры", false),
      new MultiBooleanSetting.Value("Иконки эффектов", false),
      new MultiBooleanSetting.Value("Погода", false),
      new MultiBooleanSetting.Value("Туман", false),
      new MultiBooleanSetting.Value("Туман под водой", false),
      new MultiBooleanSetting.Value("Граница мира", false),
      new MultiBooleanSetting.Value("Барьеры", false),
      new MultiBooleanSetting.Value("Взрывы", false),
      new MultiBooleanSetting.Value("Частицы блоков", false),
      new MultiBooleanSetting.Value("Частицы зелий", false),
      new MultiBooleanSetting.Value("Все частицы", false),
      new MultiBooleanSetting.Value("Броня", false),
      new MultiBooleanSetting.Value("Луч маяка", false),
      new MultiBooleanSetting.Value("Огонь на сущностях", false),
      new MultiBooleanSetting.Value("Тени сущностей", false)
   );

   public boolean hide(String what) {
      return this.isEnabled() && this.settings.isEnable(what);
   }

   public boolean isRemoveFire() {
      return this.isEnabled() && this.settings.isEnable(0);
   }

   public boolean isRemovePumpkin() {
      return this.isEnabled() && this.settings.isEnable("Тыква");
   }

   public boolean isRemoveBadEffect() {
      return this.isEnabled() && this.settings.isEnable(1);
   }

   @EventTarget
   private void onCamera(EventCamera e) {
      e.setCameraClip(this.settings.isEnable("Камера клип"));
      e.cancel();
   }
}
