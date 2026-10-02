package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.Render3DUtil;

@ModuleAnnotation(
   name = "KillEffect",
   category = Category.RENDER,
   description = "Эффект при убийстве цели"
)
public final class KillEffect extends Module {
   public static final KillEffect INSTANCE = new KillEffect();

   private final NumberSetting duration = new NumberSetting("Длительность (мс)", 700.0F, 200.0F, 2000.0F, 50.0F);

   private LivingEntity lastTarget;
   private Vec3d deathPos;
   private long start;

   private KillEffect() {
   }

   public void onDisable() {
      super.onDisable();
      this.deathPos = null;
      this.lastTarget = null;
   }

   @EventTarget
   private void onUpdate(EventUpdate event) {
      if (mc.player != null && mc.world != null) {
         LivingEntity target = tech.pain.client.modules.impl.combat.Aura.INSTANCE.getTarget();
         if (target != null && target != mc.player) {
            this.lastTarget = target;
         } else if (this.lastTarget != null) {
            boolean dead = !this.lastTarget.isAlive() || this.lastTarget.getHealth() <= 0.0F || this.lastTarget.isRemoved();
            if (dead) {
               this.deathPos = this.lastTarget.getBoundingBox().getCenter();
               this.start = System.currentTimeMillis();
            }

            this.lastTarget = null;
         }
      }
   }

   @EventTarget
   private void onRender3D(EventRender3D event) {
      if (this.deathPos != null) {
         long elapsed = System.currentTimeMillis() - this.start;
         long dur = (long)this.duration.getCurrent();
         if (elapsed > dur) {
            this.deathPos = null;
         } else {
            float f = (float)elapsed / (float)dur;
            double size = 0.4D + (double)f * 1.6D;
            int alpha = (int)((1.0F - f) * 200.0F);
            int color = Pain.getInstance().getThemeManager().getClientColor(90).withAlpha(alpha).getRGB();
            Box box = new Box(
               this.deathPos.x - size, this.deathPos.y - size, this.deathPos.z - size,
               this.deathPos.x + size, this.deathPos.y + size, this.deathPos.z + size
            );
            Render3DUtil.drawBox(box, color, 1.5F);
         }
      }
   }
}
