package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import java.util.function.Supplier;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventFog;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;

@ModuleAnnotation(
   name = "CustomFog",
   category = Category.RENDER,
   description = "Меняет туман: цвет, перелив, радуга, пульсация"
)
public class CustomFog extends Module {
   public static final CustomFog INSTANCE = new CustomFog();
   public final NumberSetting distanceSetting = new NumberSetting("Дистанция тумана", 80.0F, 10.0F, 255.0F, 5.0F);
   private final ModeSetting effect = new ModeSetting("Эффект", "Тема", "Перелив", "Радуга", "Закат", "Серый", "Свой цвет");
   private final ColorSetting custom = new ColorSetting("Цвет", new ColorRGBA(140, 90, 255), (Supplier<Boolean>)() -> this.effect.is("Свой цвет"));
   private final NumberSetting speed = new NumberSetting("Скорость", 1.0F, 0.1F, 5.0F, 0.1F);
   private final NumberSetting brightness = new NumberSetting("Яркость", 1.0F, 0.2F, 1.0F, 0.05F);
   private final BooleanSetting pulse = new BooleanSetting("Пульсация (дыхание)", false);
   private final NumberSetting pulseAmount = new NumberSetting("Сила пульсации", 25.0F, 5.0F, 100.0F, 5.0F, (Supplier<Boolean>)() -> this.pulse.isEnabled());

   @EventTarget
   public void onFog(EventFog e) {
      double t = (double)System.currentTimeMillis() / 1000.0 * (double)this.speed.getCurrent();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      ColorRGBA color;
      switch (this.effect.get()) {
         case "Перелив":
            // плавно между двумя цветами темы
            color = theme.getColor().mix(theme.getSecondColor(), 0.5F + 0.5F * (float)Math.sin(t * 0.8));
            break;
         case "Радуга":
            color = ColorRGBA.fromHSB((float)(t * 0.04 % 1.0), 0.55F, 1.0F);
            break;
         case "Закат":
            // оранжевый → розовый → фиолетовый по кругу
            float k = 0.5F + 0.5F * (float)Math.sin(t * 0.5);
            ColorRGBA warm = new ColorRGBA(255, 140, 70).mix(new ColorRGBA(255, 90, 150), k);
            color = warm.mix(new ColorRGBA(120, 70, 220), 0.5F + 0.5F * (float)Math.sin(t * 0.23 + 1.3));
            break;
         case "Серый":
            color = new ColorRGBA(150, 154, 162);
            break;
         case "Свой цвет":
            color = this.custom.getColor();
            break;
         default:
            color = theme.getColor();
      }

      float b = this.brightness.getCurrent();
      color = new ColorRGBA((int)((float)color.getRed() * b), (int)((float)color.getGreen() * b), (int)((float)color.getBlue() * b), 255);
      float distance = this.distanceSetting.getCurrent();
      if (this.pulse.isEnabled()) {
         distance = Math.max(8.0F, distance + (float)Math.sin(t * 1.2) * this.pulseAmount.getCurrent());
      }

      e.setDistance(distance);
      e.setColor(color.getRGB());
      e.setCancelled(true);
   }
}
