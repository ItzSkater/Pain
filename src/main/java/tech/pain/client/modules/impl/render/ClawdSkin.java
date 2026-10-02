package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.Model3D;

/**
 * Ты сам выглядишь как Claw'd: обычная модель игрока не рисуется, вместо неё — Claw'd,
 * который перебирает ножками при ходьбе и машет мечом при ударе. Видно только тебе (в F5).
 */
@ModuleAnnotation(
   name = "Claw'd",
   category = Category.RENDER,
   description = "Превращает тебя (или всех игроков) в Claw'd — только у тебя на экране"
)
public final class ClawdSkin extends Module {
   public static final ClawdSkin INSTANCE = new ClawdSkin();
   private final ModeSetting who = new ModeSetting("Кого", "Только меня", "Всех игроков");
   private final NumberSetting size = new NumberSetting("Размер", 1.0F, 0.4F, 6.0F, 0.05F);
   private final BooleanSetting sword = new BooleanSetting("Меч, если в руке меч", true);

   private ClawdSkin() {
   }

   /** Нужно ли вместо этой сущности рисовать Claw'd. */
   public boolean replaces(Entity entity) {
      if (!this.isEnabled() || !(entity instanceof PlayerEntity) || mc.currentScreen instanceof HandledScreen) {
         return false;
      }

      return entity == mc.player || this.who.is("Всех игроков");
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.world == null || mc.player == null) {
         return;
      }

      Model3D body = Model3D.bundled("pet_body");
      Model3D eyes = Model3D.bundled("pet_eyes");
      Model3D leg = Model3D.bundled("pet_leg");
      Model3D blade = Model3D.bundled("pet_sword");
      if (body == null || eyes == null || leg == null) {
         return;
      }

      float delta = event.getPartialTicks();
      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      double time = (double)System.currentTimeMillis() / 1000.0;

      for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
         if (!this.replaces(player) || player.isInvisible() || player.isSpectator()) {
            continue;
         }

         // себя от первого лица не рисуем
         if (player == mc.player && mc.options.getPerspective().isFirstPerson()) {
            continue;
         }

         double x = MathHelper.lerp((double)delta, player.prevX, player.getX()) - cam.x;
         double y = MathHelper.lerp((double)delta, player.prevY, player.getY()) - cam.y;
         double z = MathHelper.lerp((double)delta, player.prevZ, player.getZ()) - cam.z;
         float yaw = MathHelper.lerpAngleDegrees(delta, player.prevBodyYaw, player.bodyYaw);
         float moving = MathHelper.clamp(player.limbAnimator.getSpeed(delta), 0.0F, 1.0F);
         float phase = player.limbAnimator.getPos(delta) * 0.9F;
         float swing = player.getHandSwingProgress(delta);
         float sneak = player.isSneaking() ? 1.0F : 0.0F;
         // ширина тела с клешнями около блока — примерно как у игрока с расставленными руками
         float unit = 0.42F * this.size.getCurrent();
         MatrixStack ms = event.getMatrix();
         ms.push();
         ms.translate(x, y, z);
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
         ms.scale(unit, unit, unit);
         float legSwing = MathHelper.sin(phase) * 40.0F * moving;
         float[] legX = new float[]{-0.82F, -0.34F, 0.34F, 0.82F};

         for (int i = 0; i < 4; ++i) {
            ms.push();
            ms.translate(legX[i], 0.42F - sneak * 0.12F, 0.0F);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(i % 2 == 0 ? legSwing : -legSwing));
            leg.draw(ms, 1.0F, null, 0.0F);
            ms.pop();
         }

         float bob = Math.abs(MathHelper.sin(phase)) * 0.08F * moving;
         ms.push();
         ms.translate(0.0F, bob - sneak * 0.14F, 0.0F);
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(moving * 5.0F + sneak * 10.0F + MathHelper.sin(swing * 3.1416F) * 12.0F));
         body.draw(ms, 1.0F, null, 0.0F);
         // моргает раз в несколько секунд, у каждого игрока в своё время
         double blinkClock = (time + (double)(player.getId() % 7) * 0.61) % 4.0;
         float open = blinkClock < 0.14 ? 0.12F : 1.0F;
         ms.push();
         ms.translate(0.0F, 1.11F, 0.0F);
         ms.scale(1.0F, open, 1.0F);
         ms.translate(0.0F, -1.11F, 0.0F);
         eyes.draw(ms, 1.0F, null, 0.0F);
         ms.pop();
         if (blade != null && this.sword.isEnabled() && player.getMainHandStack().getItem() instanceof SwordItem) {
            float arc = swing > 0.0F ? -100.0F + 190.0F * (1.0F - (1.0F - swing) * (1.0F - swing)) : 35.0F;
            ms.push();
            ms.translate(-1.22F, 0.93F, 0.32F);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(arc));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-8.0F));
            ms.scale(0.85F, 0.85F, 0.85F);
            blade.draw(ms, 1.0F, null, 0.0F);
            ms.pop();
         }

         ms.pop();
         ms.pop();
      }
   }
}
