package tech.pain.base.config;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import lombok.Generated;
import tech.pain.Pain;
import tech.pain.base.theme.Theme;
import tech.pain.base.theme.ThemeManager;
import tech.pain.client.modules.api.Module;

public class Config {
   private final String name;
   private final File file;

   public Config(String name) {
      this.name = name;
      this.file = new File(ConfigManager.configDirectory, name + "." + "Pain".toLowerCase());
      if (!this.file.exists()) {
         try {
            this.file.createNewFile();
         } catch (IOException var3) {
            var3.printStackTrace();
         }
      }

   }

   public JsonObject save() {
      try {
         JsonObject root = new JsonObject();
         JsonObject modulesObject = new JsonObject();
         Iterator var3 = Pain.getInstance().getModuleManager().getModules().iterator();

         while(var3.hasNext()) {
            Module module = (Module)var3.next();
            modulesObject.add(module.getName(), module.save());
         }

         root.add("Modules", modulesObject);
         ThemeManager themeManager = Pain.getInstance().getThemeManager();
         JsonObject themeObject = new JsonObject();
         themeObject.addProperty("selected", themeManager.getCurrentTheme().getName());
         themeObject.addProperty("custom1", themeManager.getCustomTheme().getColor1());
         themeObject.addProperty("custom2", themeManager.getCustomTheme().getColor2());
         root.add("Theme", themeObject);
         return root;
      } catch (Exception var5) {
         var5.printStackTrace();
         return null;
      }
   }

   public void load(JsonObject object) {
      JsonObject modulesObject;
      if (object.has("Theme")) {
         modulesObject = object.getAsJsonObject("Theme");
         if (modulesObject.has("custom1") && modulesObject.has("custom2")) {
            Pain.getInstance().getThemeManager().setCustomColors(modulesObject.get("custom1").getAsInt(), modulesObject.get("custom2").getAsInt());
         }

         if (modulesObject.has("selected")) {
            String selected = modulesObject.get("selected").getAsString();
            Iterator var4 = Pain.getInstance().getThemeManager().getThemes().iterator();

            while(var4.hasNext()) {
               Theme t = (Theme)var4.next();
               if (t.getName().equalsIgnoreCase(selected)) {
                  Pain.getInstance().getThemeManager().setCurrentTheme(t);
                  break;
               }
            }
         }
      }

      if (object.has("Modules")) {
         try {
            modulesObject = object.getAsJsonObject("Modules");
            Iterator var7 = Pain.getInstance().getModuleManager().getModules().iterator();

            while(var7.hasNext()) {
               Module module = (Module)var7.next();
               module.load(modulesObject.getAsJsonObject(module.getName()));
            }
         } catch (Exception var6) {
            var6.printStackTrace();
         }
      }

   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public File getFile() {
      return this.file;
   }
}
