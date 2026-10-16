package tech.pain.client.modules.impl.render;

import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Оформление вне клик-гуи: своё главное меню и его обои, стеклянные меню самой игры
 * и общий материал стекла (матовое или глянцевое) для HUD, клик-гуи и меню игры.
 */
@ModuleAnnotation(
   name = "CoolInterface",
   category = Category.RENDER,
   description = "Главное меню, обои, стеклянные меню игры и материал стекла"
)
public final class CoolInterface extends Module {
   public static final CoolInterface INSTANCE = new CoolInterface();
   public final BooleanSetting customTitle = new BooleanSetting("Своё главное меню", true);
   public final BooleanSetting customServers = new BooleanSetting("Свой список серверов", true);
   public final ModeSetting wallpaper = new ModeSetting("Обои меню", () -> this.customTitle.isEnabled(), "Слайд-шоу", "Случайные", "Deep Space", "Inside Galaxy", "Спираль", "Чёрная дыра", "Линии", "Калифорния", "Туманность Киля", "Тарантул", "Трифид и Лагуна", "Лагуна", "Звёздный след", "Чжанцзяцзе", "Над облаками", "Глен-Коу", "Уюни", "Остров Скай", "Лейк-Луиз", "Глен-Этив", "Дедвлей", "Тейде", "Свой файл");
   public final tech.pain.client.modules.api.setting.impl.NumberSetting slideSeconds = new tech.pain.client.modules.api.setting.impl.NumberSetting("Смена обоев, сек", 25.0F, 8.0F, 120.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customTitle.isEnabled() && this.wallpaper.is("Слайд-шоу"));
   public final ModeSetting titleColor = new ModeSetting("Цвет заголовка", () -> this.customTitle.isEnabled(), "Белый", "Тема", "Чёрный");
   public final BooleanSetting gameMenus = new BooleanSetting("Свои меню игры", true);
   public final ModeSetting menuLook = new ModeSetting("Вид меню игры", () -> this.gameMenus.isEnabled(), "Pain", "Стекло");
   public final ModeSetting glass = new ModeSetting("Стекло", "Матовое", "Глянцевое");
   public final NumberSetting density = new NumberSetting("Плотность стекла", 0.5F, 0.0F, 1.0F, 0.05F);

   private CoolInterface() {
      // включён по умолчанию: это оформление, а не игровая функция
      this.setEnabled(true);
   }

   public boolean titleEnabled() {
      return this.isEnabled() && this.customTitle.isEnabled();
   }

   public boolean serversEnabled() {
      return this.isEnabled() && this.customServers.isEnabled();
   }

   public boolean gameMenusEnabled() {
      return this.isEnabled() && this.gameMenus.isEnabled();
   }

   /** Глянцевое — прозрачное, с резким бликом; матовое — с сильным размытием фона. */
   public boolean painLook() {
      return this.menuLook.is("Pain");
   }

   public boolean glossy() {
      return this.glass.is("Глянцевое");
   }

   public float density() {
      return this.density.getCurrent();
   }
}
