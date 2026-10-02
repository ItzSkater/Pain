package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.utility.render.level.Render3DUtil;

@ModuleAnnotation(
   name = "Tracers",
   category = Category.RENDER,
   description = "Линии до сущностей"
)
public final class Tracers extends Module {
   public static final Tracers INSTANCE = new Tracers();

   private final MultiBooleanSetting targets = new MultiBooleanSetting(
      "Цели",
      new MultiBooleanSetting.Value("Игроки", true),
      new MultiBooleanSetting.Value("Мобы", false),
      new MultiBooleanSetting.Value("Животные", false)
   );

   private Tracers() {
   }

   @EventTarget
   private void onRender3D(EventRender3D event) {
      if (mc.player != null && mc.world != null) {
         float delta = event.getPartialTicks();
         float yaw = mc.gameRenderer.getCamera().getYaw();
         float pitch = mc.gameRenderer.getCamera().getPitch();
         Vec3d cam = mc.gameRenderer.getCamera().getPos();
         Vec3d start = cam.add(Vec3d.fromPolar(pitch, yaw));
         int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();

         for (Entity entity : mc.world.getEntities()) {
            if (entity != mc.player && entity instanceof LivingEntity && this.isTarget(entity)) {
               double x = entity.lastRenderX + (entity.getX() - entity.lastRenderX) * (double)delta;
               double y = entity.lastRenderY + (entity.getY() - entity.lastRenderY) * (double)delta;
               double z = entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * (double)delta;
               Render3DUtil.drawLine(start, new Vec3d(x, y + (double)entity.getHeight() / 2.0D, z), color, 1.0F, false);
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
