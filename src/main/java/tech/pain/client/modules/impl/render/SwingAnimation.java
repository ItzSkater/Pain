package tech.pain.client.modules.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(
   name = "SwingAnimation",
   category = Category.RENDER,
   description = "Кастомные анимации удара"
)
public final class SwingAnimation extends Module {
   public static final SwingAnimation INSTANCE = new SwingAnimation();
   public ModeSetting animationMode = new ModeSetting("Режим", new String[]{"Smooth", "Self", "Self2", "Down", "Forward", "Touch", "Pander", "Curt", "BlockHit", "Slide", "Push", "Spin"});
   public NumberSetting swingPower = new NumberSetting("Сила", 5.0F, 1.0F, 10.0F, 1.0F, () -> {
      return !this.animationMode.is("BlockHit") && !this.animationMode.is("Pander") && !this.animationMode.is("Curt");
   });
   public NumberSetting speed = new NumberSetting("Скорость", 7.0F, 0.0F, 10.0F, 1.0F);
   public NumberSetting angle = new NumberSetting("Угол", 0.0F, 0.0F, 360.0F, 1.0F, () -> {
      return this.animationMode.is("Self") || this.animationMode.is("Self2");
   });

   public NumberSetting offsetX = new NumberSetting("Смещение X", 0.0F, -1.0F, 1.0F, 0.01F);
   public NumberSetting offsetY = new NumberSetting("Смещение Y", 0.0F, -1.0F, 1.0F, 0.01F);
   public NumberSetting offsetZ = new NumberSetting("Смещение Z", 0.0F, -1.0F, 1.0F, 0.01F);
   public tech.pain.client.modules.api.setting.impl.BooleanSetting trail = new tech.pain.client.modules.api.setting.impl.BooleanSetting("Трейл за мечом", true);
   public NumberSetting trailLife = new NumberSetting("Длина трейла (сек)", 0.22F, 0.08F, 0.6F, 0.01F, (java.util.function.Supplier<Boolean>)() -> this.trail.isEnabled());
   public ModeSetting trailColor = new ModeSetting("Цвет трейла", () -> this.trail.isEnabled(), "Тема", "Радуга", "Свой");
   public tech.pain.client.modules.api.setting.impl.ColorSetting trailCustom = new tech.pain.client.modules.api.setting.impl.ColorSetting("Свой цвет трейла", new tech.pain.utility.render.display.base.color.ColorRGBA(255, 255, 255), () -> this.trail.isEnabled() && this.trailColor.is("Свой"));
   // точки лезвия по кадрам: основание, кончик (в координатах руки) и время
   private final java.util.ArrayDeque<float[]> blade = new java.util.ArrayDeque<>();

   private SwingAnimation() {
   }

   /**
    * Запоминает, где сейчас основание и кончик клинка. matrices — матрица руки прямо перед отрисовкой предмета;
    * поверх неё накладывается стандартная поза «оружие в правой руке» из ванильной модели handheld.
    */
   public void recordBlade(MatrixStack matrices, net.minecraft.item.ItemStack stack) {
      if (!this.isEnabled() || !this.trail.isEnabled() || !(stack.getItem() instanceof net.minecraft.item.SwordItem || stack.getItem() instanceof net.minecraft.item.AxeItem || stack.getItem() instanceof net.minecraft.item.MaceItem)) {
         return;
      }

      org.joml.Matrix4f m = new org.joml.Matrix4f(matrices.peek().getPositionMatrix())
         .translate(1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F)
         .rotate(new org.joml.Quaternionf().rotationXYZ(0.0F, (float)Math.toRadians(-90.0), (float)Math.toRadians(25.0)))
         .scale(0.68F)
         .translate(-0.5F, -0.5F, -0.5F);
      // текстура меча: рукоять внизу слева, кончик вверху справа
      org.joml.Vector3f base = m.transformPosition(new org.joml.Vector3f(0.36F, 0.36F, 0.5F));
      org.joml.Vector3f tip = m.transformPosition(new org.joml.Vector3f(0.94F, 0.96F, 0.5F));
      this.blade.addLast(new float[]{base.x, base.y, base.z, tip.x, tip.y, tip.z, (float)(System.nanoTime() / 1000000L % 100000000L) / 1000.0F});
      while (this.blade.size() > 90) {
         this.blade.pollFirst();
      }
   }

   /** Лента между положениями клинка за последние доли секунды: у кончика яркая, к рукояти и к хвосту гаснет. */
   public void drawTrail() {
      float now = (float)(System.nanoTime() / 1000000L % 100000000L) / 1000.0F;
      float life = this.trailLife.getCurrent();

      while (!this.blade.isEmpty() && (now - this.blade.peekFirst()[6] > life || now < this.blade.peekFirst()[6])) {
         this.blade.pollFirst();
      }

      if (!this.isEnabled() || !this.trail.isEnabled() || this.blade.size() < 2) {
         return;
      }

      com.mojang.blaze3d.systems.RenderSystem.enableBlend();
      com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(770, 1, 0, 1);
      com.mojang.blaze3d.systems.RenderSystem.disableCull();
      com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
      com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
      com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);
      net.minecraft.client.render.BufferBuilder bb = net.minecraft.client.render.Tessellator.getInstance().begin(net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLES, net.minecraft.client.render.VertexFormats.POSITION_COLOR);
      org.joml.Matrix4f id = new org.joml.Matrix4f();
      float[] prev = null;
      int i = 0;

      for (float[] p : this.blade) {
         if (prev != null) {
            float a0 = 1.0F - (now - prev[6]) / life;
            float a1 = 1.0F - (now - p[6]) / life;
            int c0 = this.trailRgb((float)i / (float)this.blade.size());
            int c1 = this.trailRgb((float)(i + 1) / (float)this.blade.size());
            int tip0 = tech.pain.utility.render.level.ParticleShapes.withAlpha(c0, a0 * a0 * 0.85F);
            int tip1 = tech.pain.utility.render.level.ParticleShapes.withAlpha(c1, a1 * a1 * 0.85F);
            int base0 = tech.pain.utility.render.level.ParticleShapes.withAlpha(c0, 0.0F);
            int base1 = tech.pain.utility.render.level.ParticleShapes.withAlpha(c1, 0.0F);
            bb.vertex(id, prev[0], prev[1], prev[2]).color(base0);
            bb.vertex(id, prev[3], prev[4], prev[5]).color(tip0);
            bb.vertex(id, p[3], p[4], p[5]).color(tip1);
            bb.vertex(id, prev[0], prev[1], prev[2]).color(base0);
            bb.vertex(id, p[3], p[4], p[5]).color(tip1);
            bb.vertex(id, p[0], p[1], p[2]).color(base1);
         }

         prev = p;
         ++i;
      }

      net.minecraft.client.render.BuiltBuffer built = bb.endNullable();
      if (built != null) {
         net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(built);
      }

      com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
      com.mojang.blaze3d.systems.RenderSystem.enableCull();
      com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
      com.mojang.blaze3d.systems.RenderSystem.disableBlend();
   }

   private int trailRgb(float phase) {
      if (this.trailColor.is("Радуга")) {
         return tech.pain.utility.render.level.ParticleShapes.rainbow(phase + (float)(System.currentTimeMillis() % 4000L) / 4000.0F, 0.7F, 1.0F);
      } else if (this.trailColor.is("Свой")) {
         return this.trailCustom.getColor().getRGB() & 0xFFFFFF;
      }

      return tech.pain.Pain.getInstance().getThemeManager().getClientColor((int)(phase * 180.0F)).getRGB() & 0xFFFFFF;
   }

   public void renderSwordAnimation(MatrixStack matrices, float swingProgress, float equipProgress, Arm arm) {
      float anim = (float)Math.sin((double)swingProgress * 1.5707963267948966D * 2.0D);
      float sin2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * 3.1415927F);
      matrices.translate(this.offsetX.getCurrent(), this.offsetY.getCurrent(), this.offsetZ.getCurrent());
      if (this.animationMode.is("Slide") || this.animationMode.is("Push")) {
         // блок-анимации как в 1.7: Slide — клинок скользит внутрь и вниз, Push — тычок вперёд
         float power = this.swingPower.getCurrent() / 5.0F;
         if (this.animationMode.is("Slide")) {
            matrices.translate(0.56F - 0.22F * sin2 * power, -0.52F - 0.1F * sin2 * power, -0.72F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F - 25.0F * sin2));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-40.0F * sin2 * power));
         } else {
            matrices.translate(0.56F, -0.52F, -0.72F - 0.35F * sin2 * power);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0F * sin2));
         }

         // поза «меч в блоке»
         matrices.translate(-0.1F, 0.08F, 0.0F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
         return;
      }

      if (this.animationMode.is("Spin")) {
         matrices.translate(0.56F, -0.52F, -0.72F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swingProgress * 360.0F * this.swingPower.getCurrent()));
         return;
      }

      String var7 = this.animationMode.get();
      byte var8 = -1;
      switch(var7.hashCode()) {
      case -1911677324:
         if (var7.equals("Pander")) {
            var8 = 7;
         }
         break;
      case -1814666802:
         if (var7.equals("Smooth")) {
            var8 = 0;
         }
         break;
      case -599960602:
         if (var7.equals("BlockHit")) {
            var8 = 8;
         }
         break;
      case 2112084:
         if (var7.equals("Curt")) {
            var8 = 6;
         }
         break;
      case 2136258:
         if (var7.equals("Down")) {
            var8 = 4;
         }
         break;
      case 2573164:
         if (var7.equals("Self")) {
            var8 = 3;
         }
         break;
      case 79768134:
         if (var7.equals("Self2")) {
            var8 = 1;
         }
         break;
      case 80998175:
         if (var7.equals("Touch")) {
            var8 = 5;
         }
         break;
      case 987507365:
         if (var7.equals("Forward")) {
            var8 = 2;
         }
      }

      float f;
      float g;
      float sinExtra;
      switch(var8) {
      case 0:
         matrices.translate(0.56F, -0.52F, -0.72F);
         f = this.swingPower.getCurrent() * 10.0F;
         g = MathHelper.sin(swingProgress * swingProgress * 3.1415927F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F + g * (-f / 4.0F)));
         sinExtra = MathHelper.sin(MathHelper.sqrt(swingProgress * swingProgress) * 3.1415927F);
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sinExtra * -(f / 4.0F)));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinExtra * -f));
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0F));
         break;
      case 1:
         matrices.translate(0.56F, -0.52F, -0.72F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-this.angle.getCurrent() - this.swingPower.getCurrent() * 10.0F * anim));
         break;
      case 2:
         matrices.translate(0.56F, -0.52F, -0.72F);
         f = 35.0F;
         matrices.translate(0.0D, 0.0D, -0.3D * (double)sin2);
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -f));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * f));
         break;
      case 3:
         matrices.translate(0.56F, -0.52F, -0.72F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-this.angle.getCurrent() - this.swingPower.getCurrent() * 10.0F * anim));
         break;
      case 4:
         matrices.translate(0.56F, -0.52F - anim * this.swingPower.getCurrent() / 24.0F, -0.72F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
         break;
      case 5:
         matrices.translate(0.56F, -0.52F, -0.72F);
         matrices.scale(1.0F, 1.0F, 1.0F + anim * this.swingPower.getCurrent() / 4.0F);
         matrices.translate(0.0F, 0.0F, -0.265F);
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F));
         break;
      case 6:
         matrices.translate(0.56F, -0.52F, -0.72F);
         f = MathHelper.sqrt(swingProgress);
         g = MathHelper.sin(f * 3.1415927F);
         sinExtra = MathHelper.sin(swingProgress * 3.1415927F);
         matrices.translate(0.4F - g * 0.2F, -0.2F + g * 0.3F, -0.5F - sinExtra * 0.2F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(91.0F));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-40.0F + g * -100.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60.0F));
         break;
      case 7:
         matrices.translate(0.56F, -0.52F, -0.72F);
         matrices.scale(0.8F, 0.8F, 0.8F);
         f = 1.0F - MathHelper.lerp(mc.getRenderTickCounter().getTickDelta(true), mc.gameRenderer.firstPersonRenderer.prevEquipProgressMainHand, mc.gameRenderer.firstPersonRenderer.equipProgressMainHand);
         matrices.translate(0.3D - (double)(anim * 0.15F), (double)(0.2F - f * 0.12F), (double)(-0.15F - anim * 0.13F));
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76.0F - 10.0F * anim));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-16.0F - 8.0F * anim));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-83.0F - 26.0F * anim));
         break;
      case 8:
         matrices.translate(0.56F, -0.52F, -0.72F);
         f = MathHelper.sin((float)((double)(swingProgress * swingProgress) * 3.141592653589793D));
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
         g = MathHelper.sin((float)((double)MathHelper.sqrt(swingProgress) * 3.141592653589793D));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * -20.0F));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(g * -20.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
         matrices.translate(0.4F, 0.2F, 0.2F);
         matrices.translate(-0.5F, 0.08F, 0.0F);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
      }

   }

   private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
      int i = arm == Arm.RIGHT ? 1 : -1;
      matrices.translate((float)i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
   }

   private void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress) {
      int i = arm == Arm.RIGHT ? 1 : -1;
      float f = MathHelper.sin(swingProgress * swingProgress * 3.1415927F);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)i * (45.0F + f * -20.0F)));
      float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * 3.1415927F);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)i * g * -20.0F));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80.0F));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)i * -45.0F));
   }
}
