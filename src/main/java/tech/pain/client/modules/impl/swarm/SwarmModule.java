package tech.pain.client.modules.impl.swarm;

import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.screens.swarm.SwarmScreen;

@ModuleAnnotation(
   name = "Bots",
   category = Category.SWARM,
   description = "Меню ботов: сервер, ник, количество"
)
public final class SwarmModule extends Module {
   public static final SwarmModule INSTANCE = new SwarmModule();

   private SwarmModule() {
   }

   public void onEnable() {
      super.onEnable();
      if (mc != null) {
         mc.setScreen(new SwarmScreen());
      }

      this.setEnabled(false);
   }
}
