package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import tech.pain.Pain;
import tech.pain.base.events.impl.entity.EventEntityColor;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;

@ModuleAnnotation(
   name = "Chams",
   category = Category.RENDER,
   description = "Заливка моделей цветом, режим Шейдер — анимированный градиент"
)
public final class Chams extends Module {
   public static final Chams INSTANCE = new Chams();

   private final ModeSetting mode = new ModeSetting("Режим", "Заливка", "Шейдер");
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 40, 40), () -> this.mode.is("Заливка"));
   private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);
   private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true);

   private Chams() {
   }

   @EventTarget
   public void onEntityColor(EventEntityColor e) {
      LivingEntityRenderState state = e.getState();
      if (state == null) {
         return;
      }

      if (!this.onlyPlayers.isEnabled() || state.width == 0.6F) {
         int rgb;
         if (this.mode.is("Шейдер")) {
            rgb = Pain.getInstance().getThemeManager().getClientColor((int)(System.currentTimeMillis() / 20L % 360L)).getRGB();
         } else {
            rgb = this.color.getColor().getRGB();
         }

         e.setColor(rgb);
         if (this.throughWalls.isEnabled()) {
            e.cancel();
         }
      }
   }
}
