package tech.pain.client.modules.impl.render;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.darkmagician6.eventapi.EventTarget;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(
   name = "Trails",
   category = Category.RENDER,
   description = "Светящийся след за игроком"
)
public final class Trails extends Module {
   public static final Trails INSTANCE = new Trails();

   private final NumberSetting length = new NumberSetting("Длина", 40.0F, 5.0F, 120.0F, 1.0F);
   private final Deque<Vec3d> points = new ArrayDeque<>();

   private Trails() {
   }

   public void onEnable() {
      super.onEnable();
      this.points.clear();
   }

   public void onDisable() {
      super.onDisable();
      this.points.clear();
   }

   @EventTarget
   private void onUpdate(EventUpdate event) {
      if (mc.player != null) {
         this.points.addLast(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));

         while (this.points.size() > (int)this.length.getCurrent()) {
            this.points.removeFirst();
         }
      }
   }

   @EventTarget
   private void onRender3D(EventRender3D event) {
      if (!this.points.isEmpty()) {
         int total = this.points.size();
         int i = 0;

         for (Vec3d p : this.points) {
            float f = (float)i / (float)total;
            int alpha = (int)(f * 180.0F);
            int color = Pain.getInstance().getThemeManager().getClientColor((int)(f * 90.0F)).withAlpha(alpha).getRGB();
            double s = 0.06D;
            Box box = new Box(p.x - s, p.y, p.z - s, p.x + s, p.y + 0.12D, p.z + s);
            Render3DUtilWrap(box, color);
            ++i;
         }
      }
   }

   private static void Render3DUtilWrap(Box box, int color) {
      tech.pain.utility.render.level.Render3DUtil.drawBox(box, color, 1.0F);
   }
}
