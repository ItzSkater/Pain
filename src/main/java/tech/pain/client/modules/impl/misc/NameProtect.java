package tech.pain.client.modules.impl.misc;

import java.util.Collection;
import java.util.Iterator;
import tech.pain.Pain;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;

@ModuleAnnotation(
   name = "NameProtect",
   category = Category.MISC,
   description = "Защищает имена игроков"
)
public final class NameProtect extends Module {
   public static final NameProtect INSTANCE = new NameProtect();
   private final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);

   private NameProtect() {
   }

   public static String getCustomName() {
      Module module = INSTANCE;
      return module != null && module.isEnabled() ? "PAIN" : mc.player.getNameForScoreboard();
   }

   public static String getCustomName(String originalName) {
      Module module = INSTANCE;
      if (module != null && module.isEnabled() && mc.player != null) {
         String me = mc.player.getNameForScoreboard();
         if (originalName.contains(me)) {
            return originalName.replace(me, "PAIN");
         } else {
            if (module instanceof NameProtect) {
               NameProtect nameProtect = (NameProtect)module;
               if (nameProtect.hideFriends.isEnabled()) {
                  Collection<String> friends = Pain.getInstance().getFriendManager().getItems();
                  Iterator var5 = friends.iterator();

                  while(var5.hasNext()) {
                     String friend = (String)var5.next();
                     if (originalName.contains(friend)) {
                        return originalName.replace(friend, "PAIN");
                     }
                  }
               }
            }

            return originalName;
         }
      } else {
         return originalName;
      }
   }
}
