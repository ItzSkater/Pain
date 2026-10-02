package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.utility.render.level.Render3DUtil;

@ModuleAnnotation(
   name = "ESP",
   category = Category.RENDER,
   description = "Боксы вокруг сущностей"
)
public final class ESP extends Module {
   public static final ESP INSTANCE = new ESP();

   private final MultiBooleanSetting targets = MultiBooleanSetting.create(
      "Цели",
      java.util.List.of("Игроки", "Мобы", "Животные")
   );

   private ESP() {
   }

   @EventTarget
   private void onRender3D(EventRender3D event) {
      if (mc.player != null && mc.world != null) {
         float delta = event.getPartialTicks();

         for (Entity entity : mc.world.getEntities()) {
            if (entity != mc.player && entity instanceof LivingEntity && this.isTarget(entity)) {
               double x = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * (double)delta;
               double y = entity.lastRenderY + (entity.getY() - entity.lastRenderY) * (double)delta;
               double z = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * (double)delta;
               double w = (double)entity.getWidth() / 2.0D;
               Box box = new Box(x - w, y, z - w, x + w, y + (double)entity.getHeight(), z + w);
               int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
               Render3DUtil.drawBox(box, color, 1.5F);
            }
         }
      }
   }

   private boolean isTarget(Entity entity) {
      if (entity instanceof PlayerEntity) {
         return this.targets.isEnable("Игроки");
      } else if (entity instanceof HostileEntity) {
         return this.targets.isEnable("Мобы");
      } else {
         return entity instanceof PassiveEntity && this.targets.isEnable("Животные");
      }
   }
}
