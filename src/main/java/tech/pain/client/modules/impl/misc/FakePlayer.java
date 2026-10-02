package tech.pain.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.utility.render.level.Render3DUtil;

@ModuleAnnotation(
   name = "FakePlayer",
   category = Category.MISC,
   description = "Создаёт копию игрока в мире"
)
public final class FakePlayer extends Module {
   public static final FakePlayer INSTANCE = new FakePlayer();

   private final BooleanSetting copyInventory = new BooleanSetting("Копировать инвентарь", true);
   private final BooleanSetting follow = new BooleanSetting("Следовать за игроком", false);
   private final BooleanSetting esp = new BooleanSetting("ESP", true);

   private static int nextId = -112000;

   private OtherClientPlayerEntity fake;

   private FakePlayer() {
   }

   public void onEnable() {
      super.onEnable();
      spawn();
   }

   public void onDisable() {
      super.onDisable();
      remove();
   }

   private void spawn() {
      if (mc.player == null || mc.world == null) {
         return;
      }

      remove();

      OtherClientPlayerEntity clone = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
      clone.copyPositionAndRotation(mc.player);
      clone.setYaw(mc.player.getYaw());
      clone.setPitch(mc.player.getPitch());
      clone.setHeadYaw(mc.player.getHeadYaw());
      clone.setBodyYaw(mc.player.bodyYaw);
      clone.setHealth(mc.player.getHealth());

      if (this.copyInventory.isEnabled()) {
         clone.getInventory().clone(mc.player.getInventory());
      }

      clone.setId(nextId--);
      mc.world.addEntity(clone);
      this.fake = clone;
   }

   private void remove() {
      if (this.fake != null) {
         if (mc.world != null) {
            mc.world.removeEntity(this.fake.getId(), Entity.RemovalReason.DISCARDED);
         }

         this.fake = null;
      }
   }

   @EventTarget
   private void onRender3D(EventRender3D event) {
      if (this.esp.isEnabled() && this.fake != null && !this.fake.isRemoved()) {
         Box box = this.fake.getBoundingBox();
         int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
         Render3DUtil.drawBox(box, color, 1.0F);
      }
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (this.fake == null || mc.player == null || mc.world == null) {
         return;
      }

      if (this.fake.isRemoved()) {
         this.fake = null;
         spawn();
         return;
      }

      if (this.follow.isEnabled()) {
         this.fake.copyPositionAndRotation(mc.player);
         this.fake.setHeadYaw(mc.player.getHeadYaw());
         this.fake.setBodyYaw(mc.player.bodyYaw);
      }
   }
}
