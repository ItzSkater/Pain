package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import java.util.Random;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.player.EventMotion;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.math.Timer;

@ModuleAnnotation(
   name = "TargetTP",
   category = Category.COMBAT,
   description = "Телепортирует вас вокруг цели ауры"
)
public final class TargetTP extends Module {
   public static final TargetTP INSTANCE = new TargetTP();

   private final NumberSetting radius = new NumberSetting("Радиус", 2.5F, 1.5F, 5.0F, 0.1F);
   private final NumberSetting heightOffset = new NumberSetting("Смещение высоты", 0.0F, -1.5F, 1.5F, 0.1F);
   private final NumberSetting tpsPerSecond = new NumberSetting("ТП в секунду", 25.0F, 5.0F, 500.0F, 1.0F);
   private final NumberSetting spoofPackets = new NumberSetting("Спуф пакеты", 3.0F, 0.0F, 20.0F, 1.0F);
   private final ModeSetting mode = new ModeSetting("Режим", "Круг", "Рандом");
   private final BooleanSetting requireAura = new BooleanSetting("Требует ауру", true);
   private final BooleanSetting safeOnly = new BooleanSetting("Только безопасно", true);

   private final Timer tpTimer = new Timer();
   private final Random rng = new Random();
   private double angle;

   private TargetTP() {
   }

   public void onDisable() {
      super.onDisable();
      this.angle = 0.0D;
   }

   @EventTarget
   private void onMotion(EventMotion event) {
      if (mc.player != null && mc.world != null) {
         LivingEntity target = Aura.INSTANCE.getTarget();
         if (target != null && target != mc.player) {
            if (!this.requireAura.isEnabled() || Aura.INSTANCE.isEnabled()) {
               long intervalMs = Math.max(1L, 1000L / (long)Math.max(1, (int)this.tpsPerSecond.getCurrent()));
               if (this.tpTimer.finished(intervalMs)) {
                  Vec3d destination = this.pickDestination(target);
                  if (destination != null) {
                     this.teleport(destination);
                     this.tpTimer.reset();
                  }
               }
            }
         }
      }
   }

   private Vec3d pickDestination(LivingEntity target) {
      double r = this.radius.getCurrent();
      double centerX = target.getX();
      double centerY = target.getY() + (double)this.heightOffset.getCurrent();
      double centerZ = target.getZ();
      if (this.mode.is("Круг")) {
         this.angle += Math.toRadians(this.rng.nextDouble() * 30.0D + 60.0D);
         if (this.angle > Math.PI * 2.0D) {
            this.angle -= Math.PI * 2.0D;
         }

         double x = centerX + Math.cos(this.angle) * r;
         double z = centerZ + Math.sin(this.angle) * r;
         return this.validate(new Vec3d(x, centerY, z));
      } else {
         for(int tries = 0; tries < 6; ++tries) {
            double a = this.rng.nextDouble() * Math.PI * 2.0D;
            double x = centerX + Math.cos(a) * r;
            double z = centerZ + Math.sin(a) * r;
            Vec3d candidate = this.validate(new Vec3d(x, centerY, z));
            if (candidate != null) {
               return candidate;
            }
         }

         return null;
      }
   }

   private Vec3d validate(Vec3d pos) {
      if (!this.safeOnly.isEnabled()) {
         return pos;
      } else {
         Box box = mc.player.getBoundingBox().offset(pos.subtract(mc.player.getPos()));
         return !mc.world.isSpaceEmpty(box) ? null : pos;
      }
   }

   private void teleport(Vec3d to) {
      double curY = mc.player.getY();
      boolean collision = mc.player.horizontalCollision;

      for(int i = 0; i < (int)this.spoofPackets.getCurrent(); ++i) {
         mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), curY, mc.player.getZ(), false, collision));
      }

      mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(to.x, to.y, to.z, false, collision));
      mc.player.setPosition(to.x, to.y, to.z);
   }
}
