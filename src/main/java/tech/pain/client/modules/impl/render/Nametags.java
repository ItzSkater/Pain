package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.server.EventPacket;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender2D;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.math.MathUtil;
import tech.pain.utility.math.ProjectionUtil;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;

@ModuleAnnotation(
   name = "Nametags",
   category = Category.RENDER,
   description = "Кастомные ники над игроками: HP и дистанция"
)
public final class Nametags extends Module {
   public static final Nametags INSTANCE = new Nametags();

   private final NumberSetting size = new NumberSetting("Размер", 7.0F, 4.0F, 12.0F, 0.5F);
   private final BooleanSetting showHp = new BooleanSetting("HP", true);
   private final BooleanSetting showDistance = new BooleanSetting("Дистанция", true);
   private final BooleanSetting showTotems = new BooleanSetting("Попы тотемов", true);

   private final Map<UUID, Integer> totemPops = new HashMap<>();

   private Nametags() {
   }

   @EventTarget
   private void onPacket(EventPacket event) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof EntityStatusS2CPacket status && status.getStatus() == 35 && mc.world != null) {
         Entity entity = status.getEntity(mc.world);
         if (entity instanceof PlayerEntity player) {
            this.totemPops.merge(player.getUuid(), 1, Integer::sum);
         }
      }
   }

   @EventTarget
   private void onRender2D(EventRender2D event) {
      if (mc.player != null && mc.world != null) {
         CustomDrawContext ctx = event.getContext();
         Font font = Fonts.REGULAR.getFont(this.size.getCurrent());

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player.isAlive()) {
               Vec3d feet = MathUtil.interpolate(player);
               Vec3d head = new Vec3d(feet.x, feet.y + (double)player.getHeight() + 0.55D, feet.z);
               Vec3d screen = ProjectionUtil.worldSpaceToScreenSpace(head);
               if (!(screen.z <= 0.0D) && !(screen.z >= 1.0D)) {
                  StringBuilder sb = new StringBuilder(player.getName().getString());
                  if (this.showHp.isEnabled()) {
                     float hp = player.getHealth() + player.getAbsorptionAmount();
                     sb.append("  ").append((int)hp).append("hp");
                  }

                  if (this.showDistance.isEnabled()) {
                     sb.append("  ").append((int)mc.player.distanceTo(player)).append("m");
                  }

                  if (this.showTotems.isEnabled()) {
                     sb.append("  ").append(this.totemPops.getOrDefault(player.getUuid(), 0)).append("T");
                  }

                  String text = sb.toString();
                  float width = font.width(text);
                  float x = (float)screen.x - width / 2.0F;
                  float y = (float)screen.y - this.size.getCurrent() - 2.0F;
                  int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
                  ctx.drawText(font, text, x, y, new ColorRGBA(color));
               }
            }
         }
      }
   }
}
