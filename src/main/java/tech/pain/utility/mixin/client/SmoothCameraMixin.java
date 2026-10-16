package tech.pain.utility.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.SmoothCamera;

@Mixin(Camera.class)
public abstract class SmoothCameraMixin {
   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Shadow
   protected abstract void setPos(double x, double y, double z);

   @Shadow
   protected abstract void moveBy(float f, float g, float h);

   @Shadow
   private float cameraY;

   @Shadow
   private float lastCameraY;

   @Unique
   private boolean pain$ready;
   @Unique
   private float pain$yaw;
   @Unique
   private float pain$pitch;
   @Unique
   private Vec3d pain$pivot;
   @Unique
   private long pain$last;

   // после того как игра поставила камеру: в F5 пересчитываем её по сглаженным углу и точке опоры
   @Inject(method = "update", at = @At("TAIL"))
   private void pain$stabilize(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
      SmoothCamera module = SmoothCamera.INSTANCE;
      if (!module.isEnabled() || !thirdPerson || focused == null) {
         this.pain$ready = false;
         return;
      }

      long now = System.nanoTime();
      float dt = this.pain$ready ? Math.min(0.1F, (float)(now - this.pain$last) / 1.0E9F) : 0.0F;
      this.pain$last = now;
      float yaw = focused.getYaw(tickDelta);
      float pitch = focused.getPitch(tickDelta);
      Vec3d pivot = new Vec3d(
         MathHelper.lerp((double)tickDelta, focused.prevX, focused.getX()),
         MathHelper.lerp((double)tickDelta, focused.prevY, focused.getY()) + (double)MathHelper.lerp(tickDelta, this.lastCameraY, this.cameraY),
         MathHelper.lerp((double)tickDelta, focused.prevZ, focused.getZ())
      );
      if (!this.pain$ready || this.pain$pivot == null || this.pain$pivot.squaredDistanceTo(pivot) > 64.0) {
         // первый кадр в F5 или телепорт — встаём сразу, без подлёта
         this.pain$yaw = yaw;
         this.pain$pitch = pitch;
         this.pain$pivot = pivot;
         this.pain$ready = true;
      } else {
         // экспоненциальное сглаживание: не зависит от частоты кадров
         float kTurn = module.smoothTurn.isEnabled() ? 1.0F - (float)Math.exp((double)(-dt * module.turn.getCurrent())) : 1.0F;
         float kMove = module.smoothMove.isEnabled() ? 1.0F - (float)Math.exp((double)(-dt * module.follow.getCurrent())) : 1.0F;
         float kUp = 1.0F - (float)Math.exp((double)(-dt * module.vertical.getCurrent()));
         this.pain$yaw += MathHelper.wrapDegrees(yaw - this.pain$yaw) * kTurn;
         this.pain$pitch += (pitch - this.pain$pitch) * kTurn;
         // вертикаль отдельно: камера держит высоту и только слегка подтягивается, но не отпускает персонажа дальше «Макс. отрыв»
         double max = (double)module.maxOffset.getCurrent();
         double y = MathHelper.clamp(MathHelper.lerp((double)kUp, this.pain$pivot.y, pivot.y), pivot.y - max, pivot.y + max);
         this.pain$pivot = new Vec3d(
            MathHelper.lerp((double)kMove, this.pain$pivot.x, pivot.x), y, MathHelper.lerp((double)kMove, this.pain$pivot.z, pivot.z)
         );
      }

      float camYaw = inverseView ? this.pain$yaw + 180.0F : this.pain$yaw;
      float camPitch = inverseView ? -this.pain$pitch : this.pain$pitch;
      this.setRotation(camYaw, camPitch);
      this.setPos(this.pain$pivot.x, this.pain$pivot.y, this.pain$pivot.z);
      this.moveBy(-((tech.pain.utility.mixin.accessors.CameraAccessor)this).setClipToSpace(module.distance.getCurrent()), 0.0F, 0.0F);
   }
}
