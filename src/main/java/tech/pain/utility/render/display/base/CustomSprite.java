package tech.pain.utility.render.display.base;

import lombok.Generated;
import net.minecraft.util.Identifier;
import tech.pain.Pain;

public class CustomSprite {
   private final Identifier texture;

   public CustomSprite(String path) {
      if (path.contains(":")) {
         this.texture = Identifier.of(path);
      } else if (path.contains("/")) {
         this.texture = Pain.id(path);
      } else {
         this.texture = Pain.id("icons/category/" + path);
      }

   }

   @Generated
   public Identifier getTexture() {
      return this.texture;
   }
}
