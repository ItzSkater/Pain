package tech.pain.client.modules.impl.render;

import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(
   name = "WorldTime",
   description = "Меняет время суток",
   category = Category.RENDER
)
public class WorldTime extends Module {
   public static final WorldTime INSTANCE = new WorldTime();
   public final NumberSetting timeSetting = new NumberSetting("Время", 12.0F, 0.0F, 24.0F, 1.0F);
}
