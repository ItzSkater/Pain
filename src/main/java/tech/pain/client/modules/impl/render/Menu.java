package tech.pain.client.modules.impl.render;

import tech.pain.Pain;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;

@ModuleAnnotation(
   name = "ClickGUI",
   category = Category.RENDER,
   description = "Клик-гуи: бинд и стиль (Cool / Tabs / Liquid Tabs / Liquid Glass / Classic)"
)
public final class Menu extends Module {
   public static final Menu INSTANCE = new Menu();
   public final tech.pain.client.modules.api.setting.impl.ModeSetting style = new tech.pain.client.modules.api.setting.impl.ModeSetting("Стиль", "Cool", "Tabs", "Liquid Tabs", "Liquid Glass", "Classic");

   private Menu() {
      this.setKeyCode(344);
   }

   public void onEnable() {
      if (mc.world == null) {
         this.setEnabled(false);
      } else {
         Pain.getInstance().getMenuScreen().needToClose = false;
         if (mc.currentScreen instanceof tech.pain.client.screens.menu.tabs.TabsMenuScreen tabs && mc.currentScreen == Pain.getInstance().getMenuScreen()) {
            tabs.reopen();
            super.onEnable();
         } else if (mc.currentScreen instanceof tech.pain.client.screens.menu.rock.RockMenuScreen rock && mc.currentScreen == Pain.getInstance().getMenuScreen()) {
            rock.reopen();
            super.onEnable();
         } else if (mc.currentScreen != Pain.getInstance().getMenuScreen()) {
            mc.setScreen(Pain.getInstance().getMenuScreen());
            super.onEnable();
         }
      }
   }

   public void onDisable() {
      super.onDisable();
   }

   public void setKeyCode(int keyCode) {
      if (keyCode != -1) {
         super.setKeyCode(keyCode);
      }
   }
}
