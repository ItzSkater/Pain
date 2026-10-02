package tech.pain.client.modules.impl.misc;

import java.util.ArrayList;
import java.util.List;
import tech.pain.Pain;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.screens.menu.MenuScreen;

@ModuleAnnotation(
   name = "Panic",
   category = Category.MISC,
   description = "Выгрузка по биндую: гасит все модули и UI"
)
public final class Panic extends Module {
   public static final Panic INSTANCE = new Panic();
   public static boolean ACTIVE = false;

   private final List<Module> restore = new ArrayList<>();

   private Panic() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      ACTIVE = true;
      this.restore.clear();

      for (Module module : Pain.getInstance().getModuleManager().getModules()) {
         if (module != this && module.isEnabled()) {
            this.restore.add(module);
            module.setToggled(false);
         }
      }

      if (mc.currentScreen instanceof MenuScreen) {
         mc.setScreen(null);
      }
   }

   /** В конфиг Panic всегда пишется выключенным, а модули — такими, какими были до него. */
   public boolean wasEnabled(Module module) {
      return ACTIVE && this.restore.contains(module);
   }

   @Override
   public com.google.gson.JsonObject save() {
      com.google.gson.JsonObject object = super.save();
      object.addProperty("enabled", false);
      return object;
   }

   @Override
   public void onDisable() {
      super.onDisable();
      ACTIVE = false;

      for (Module module : this.restore) {
         if (!module.isEnabled()) {
            module.setToggled(true);
         }
      }

      this.restore.clear();
   }
}
