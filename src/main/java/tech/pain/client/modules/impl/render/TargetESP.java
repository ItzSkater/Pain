package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import tech.pain.utility.render.level.Render3DUtil;
import tech.pain.Pain;
import tech.pain.base.animations.base.Animation;
import tech.pain.base.animations.base.Easing;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

@ModuleAnnotation(
   name = "TargetESP",
   category = Category.RENDER,
   description = "Выделяет цель"
)
public class TargetESP extends Module {
   public static final TargetESP INSTANCE = new TargetESP();
   private final ModeSetting mode = new ModeSetting("Мод", new String[]{"3D модель", "Маркер", "Призраки", "ThunderHack", "CelkaPasta", "NurikZapen", "Бокс", "Пульс", "Двойной", "Ноги", "Голова", "Дыхание", "Мигание"});
   private final ModeSetting model3d = new ModeSetting("Модель", () -> this.mode.is("3D модель"), "Pain Logo", "Claw'd", "Свой файл");
   private final ModeSetting place3d = new ModeSetting("Положение", () -> this.mode.is("3D модель"), "Над головой", "Орбита", "Кольцо вокруг");
   private final tech.pain.client.modules.api.setting.impl.NumberSetting size3d = new tech.pain.client.modules.api.setting.impl.NumberSetting("Размер", 0.9F, 0.3F, 3.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("3D модель"));
   private final tech.pain.client.modules.api.setting.impl.NumberSetting spin3d = new tech.pain.client.modules.api.setting.impl.NumberSetting("Вращение", 1.0F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("3D модель"));
   private final tech.pain.client.modules.api.setting.impl.BooleanSetting tint3d = new tech.pain.client.modules.api.setting.impl.BooleanSetting("Красить цветом темы", false, (java.util.function.Supplier<Boolean>)() -> this.mode.is("3D модель"));
   private final Animation appear3d = new Animation(350L, Easing.CUBIC_OUT);
   private Entity target3d;
   private net.minecraft.util.math.Vec3d pos3d;
   private volatile tech.pain.utility.render.level.Model3D customModel;
   private volatile boolean customLoading;
   private volatile String customError;
   private long customChecked;
   private static final long INIT_TIME = System.currentTimeMillis();
   private final Animation animation;
   private final Animation animation2;
   private Entity lastTarget;
   private boolean textureLoaded;
   private float rotationAngle;
   private float rotationSpeed;
   private boolean isReversing;
   private float animationNurik;
   private long currentTime;

   public TargetESP() {
      this.animation = new Animation(400L, Easing.CUBIC_OUT);
      this.animation2 = new Animation(250L, Easing.CUBIC_OUT);
      this.lastTarget = null;
      this.textureLoaded = false;
      this.rotationAngle = 0.0F;
      this.rotationSpeed = 0.0F;
      this.isReversing = false;
      this.animationNurik = 0.0F;
   }

   public void onEnable() {
      super.onEnable();
   }

   @EventTarget
   private void onRenderWorldLast(EventRender3D e) {
      if (this.mode.is("3D модель")) {
         this.draw3D(e);
         return;
      }

      if (this.mode.is("Призраки")) {
         this.drawSpiritsTrack(e);
      }

      if (!this.mode.is("Призраки") && !this.mode.is("Маркер")) {
         Entity styleTarget = Aura.INSTANCE.getTarget();
         if (styleTarget != null) {
            if (this.mode.is("ThunderHack")) {
               this.drawThunderTarget(e, styleTarget);
            } else if (this.mode.is("CelkaPasta")) {
               this.drawOldTarget(e, styleTarget);
            } else if (this.mode.is("NurikZapen")) {
               this.drawCaptureMark(e, styleTarget);
            } else if (this.mode.is("Бокс")) {
               this.drawBox(e, styleTarget);
            } else if (this.mode.is("Пульс")) {
               this.drawPulseBox(e, styleTarget);
            } else if (this.mode.is("Двойной")) {
               this.drawDoubleBox(e, styleTarget);
            } else if (this.mode.is("Ноги")) {
               this.drawFeetBox(e, styleTarget);
            } else if (this.mode.is("Голова")) {
               this.drawHeadBox(e, styleTarget);
            } else if (this.mode.is("Дыхание")) {
               this.drawBreathBox(e, styleTarget);
            } else if (this.mode.is("Мигание")) {
               this.drawBlinkBox(e, styleTarget);
            }
         }
      }

      if (this.mode.is("Маркер")) {
         if (!this.textureLoaded) {
            MinecraftClient.getInstance().getTextureManager().registerTexture(Identifier.of("pain", "hud/marker.png"), new ResourceTexture(Identifier.of("pain", "hud/marker.png")));
            this.textureLoaded = true;
         }

         Vec3d camPos = mc.gameRenderer.getCamera().getPos();
         Entity target = Aura.INSTANCE.getTarget();
         if (target != null) {
            this.lastTarget = target;
            this.animation.update(true);
         } else {
            this.animation.update(false);
            if (this.animation.getValue() == 0.0F) {
               this.lastTarget = null;
            }
         }

         if (this.lastTarget != null) {
            double tickDelta = (double)e.getPartialTicks();
            MatrixStack matrices = e.getMatrix();
            double x = MathHelper.lerp(tickDelta, this.lastTarget.lastRenderX, this.lastTarget.getX());
            double y = MathHelper.lerp(tickDelta, this.lastTarget.lastRenderY, this.lastTarget.getY()) + (double)this.lastTarget.getHeight() / 2.0D;
            double z = MathHelper.lerp(tickDelta, this.lastTarget.lastRenderZ, this.lastTarget.getZ());
            matrices.push();
            matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
            float scale = 0.15F * this.animation.getValue();
            matrices.scale(-scale, -scale, scale);
            this.updateRotation();
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.rotationAngle));
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Identifier textureId = Identifier.of("pain", "icons/marker.png");
            AbstractTexture abstractTexture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
            int texId = abstractTexture.getGlId();
            float alpha = this.animation.getValue();
            float size = 12.0F;
            Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
            ColorRGBA color = theme.getColor().withAlpha((int)(alpha * 255.0F));
            DrawUtil.drawTexture(matrices, textureId, 0.0F - size / 2.0F, 0.0F - size / 2.0F, size, size, color);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            matrices.pop();
         }
      }
   }

   private Vec3d interp(EventRender3D e, Entity t) {
      float d = e.getPartialTicks();
      return new Vec3d(
         t.lastRenderX + (t.getX() - t.lastRenderX) * (double)d,
         t.lastRenderY + (t.getY() - t.lastRenderY) * (double)d,
         t.lastRenderZ + (t.getZ() - t.lastRenderZ) * (double)d
      );
   }

   /** Свой файл: первый .fbx/.obj/.glb из папки Pain/models, конвертируется через Blender в фоне. */
   private tech.pain.utility.render.level.Model3D customModel() {
      long now = System.currentTimeMillis();
      if (!this.customLoading && now - this.customChecked > 3000L) {
         this.customChecked = now;
         this.customLoading = true;
         Thread t = new Thread(() -> {
            try {
               java.nio.file.Path dir = mc.runDirectory.toPath().resolve("Pain").resolve("models");
               java.nio.file.Files.createDirectories(dir);
               java.nio.file.Path source = null;

               try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                  source = files.filter(f -> {
                     String n = f.getFileName().toString().toLowerCase();
                     return n.endsWith(".fbx") || n.endsWith(".obj") || n.endsWith(".glb") || n.endsWith(".gltf");
                  }).sorted().findFirst().orElse(null);
               }

               if (source == null) {
                  this.customError = "положи .fbx в " + dir;
                  this.customModel = null;
                  return;
               }

               java.nio.file.Path target = dir.resolve(source.getFileName().toString() + ".pmodel");
               boolean fresh = java.nio.file.Files.exists(target) && java.nio.file.Files.getLastModifiedTime(target).compareTo(java.nio.file.Files.getLastModifiedTime(source)) >= 0;
               if (!fresh) {
                  String error = tech.pain.utility.render.level.Model3D.convert(source, target, dir.resolve(".tools"));
                  if (error != null) {
                     this.customError = error;
                     this.customModel = null;
                     return;
                  }

                  this.customModel = null;
               }

               if (this.customModel == null) {
                  this.customModel = tech.pain.utility.render.level.Model3D.load(target);
                  this.customError = null;
               }
            } catch (Exception ex) {
               this.customError = ex.toString();
            } finally {
               this.customLoading = false;
            }
         }, "Pain-Model-Convert");
         t.setDaemon(true);
         t.start();
      }

      return this.customModel;
   }

   private String shownError = "";

   private void draw3D(EventRender3D e) {
      Entity current = Aura.INSTANCE.getTarget();
      boolean has = current != null && Aura.INSTANCE.isEnabled();
      if (has) {
         this.target3d = current;
         this.pos3d = tech.pain.utility.math.MathUtil.interpolate(current);
      }

      this.appear3d.update(has);
      float appear = this.appear3d.getValue();
      if (appear <= 0.01F || this.target3d == null || this.pos3d == null) {
         return;
      }

      tech.pain.utility.render.level.Model3D model;
      if (this.model3d.is("Свой файл")) {
         model = this.customModel();
         String error = this.customError;
         if (model == null && error != null && !error.equals(this.shownError)) {
            this.shownError = error;
            tech.pain.Pain.getInstance().getNotifyManager().addNotification("\uf06a", net.minecraft.text.Text.of("TargetESP 3D: " + error));
         }

         if (model == null) {
            model = tech.pain.utility.render.level.Model3D.bundled("pain_logo");
         }
      } else {
         model = tech.pain.utility.render.level.Model3D.bundled(this.model3d.is("Claw'd") ? "clawd" : "pain_logo");
      }

      if (model == null) {
         return;
      }

      net.minecraft.util.math.Vec3d cam = mc.gameRenderer.getCamera().getPos();
      double time = (double)(System.currentTimeMillis() - INIT_TIME) / 1000.0;
      float spin = (float)(time * 1.6 * (double)this.spin3d.getCurrent());
      float height = this.target3d.getHeight();
      float width = this.target3d.getWidth();
      float size = this.size3d.getCurrent();
      // появление с «пружинкой»
      float pop = 1.0F + 2.2F * (float)Math.pow((double)(appear - 1.0F), 3.0) + 1.2F * (float)Math.pow((double)(appear - 1.0F), 2.0);
      double x = this.pos3d.x - cam.x;
      double y = this.pos3d.y - cam.y;
      double z = this.pos3d.z - cam.z;
      net.minecraft.client.util.math.MatrixStack ms = e.getMatrix();
      ms.push();
      if (this.place3d.is("Орбита")) {
         double r = (double)width * 0.5 + 0.75;
         ms.translate(x + Math.cos((double)spin) * r, y + (double)height * 0.6 + Math.sin(time * 2.0) * 0.18, z + Math.sin((double)spin) * r);
         ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation(-spin * 2.0F));
         ms.scale(size * 0.6F * pop, size * 0.6F * pop, size * 0.6F * pop);
      } else if (this.place3d.is("Кольцо вокруг")) {
         // модель лежит плашмя и охватывает цель, как обруч
         float ring = (width * 2.4F + 0.9F) * size * pop;
         ms.translate(x, y + (double)height * 0.5 + Math.sin(time * 1.8) * (double)height * 0.25, z);
         ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation(spin));
         ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
         ms.scale(ring, ring, ring);
      } else {
         ms.translate(x, y + (double)height + 0.45 + (double)size * 0.35 + Math.sin(time * 2.2) * 0.07, z);
         ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotation(spin));
         ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float)Math.sin(time * 1.3) * 6.0F));
         ms.scale(size * pop, size * pop, size * pop);
      }

      tech.pain.base.theme.Theme theme = tech.pain.Pain.getInstance().getThemeManager().getCurrentTheme();
      model.draw(ms, Math.min(1.0F, appear * 1.4F), theme.getColor(), this.tint3d.isEnabled() ? 0.85F : 0.0F);
      ms.pop();
   }

   private void drawDoubleBox(EventRender3D e, Entity target) {
      Vec3d p = this.interp(e, target);
      double w = target.getWidth() / 2.0D;
      double h = target.getHeight();
      int c1 = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
      int c2 = Pain.getInstance().getThemeManager().getClientColor(0).withAlpha(120).getRGB();
      Render3DUtil.drawBox(new Box(p.x - w, p.y, p.z - w, p.x + w, p.y + h, p.z + w), c1, 1.5F);
      double e2 = 0.18D;
      Render3DUtil.drawBox(new Box(p.x - w - e2, p.y - e2, p.z - w - e2, p.x + w + e2, p.y + h + e2, p.z + w + e2), c2, 1.0F);
   }

   private void drawFeetBox(EventRender3D e, Entity target) {
      Vec3d p = this.interp(e, target);
      double w = target.getWidth() / 2.0D + 0.1D;
      int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
      Render3DUtil.drawBox(new Box(p.x - w, p.y, p.z - w, p.x + w, p.y + 0.08D, p.z + w), color, 1.5F);
   }

   private void drawHeadBox(EventRender3D e, Entity target) {
      Vec3d p = this.interp(e, target);
      double w = target.getWidth() / 2.0D + 0.05D;
      double h = target.getHeight();
      int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
      Render3DUtil.drawBox(new Box(p.x - w, p.y + h - 0.5D, p.z - w, p.x + w, p.y + h + 0.05D, p.z + w), color, 1.5F);
   }

   private void drawBreathBox(EventRender3D e, Entity target) {
      Vec3d p = this.interp(e, target);
      double w = target.getWidth() / 2.0D;
      double h = target.getHeight();
      double pulse = 0.25D * (Math.sin((double)(System.currentTimeMillis() - INIT_TIME) / 500.0D) * 0.5D + 0.5D);
      int color = Pain.getInstance().getThemeManager().getClientColor(90).withAlpha(140).getRGB();
      Render3DUtil.drawBox(new Box(p.x - w - pulse, p.y, p.z - w - pulse, p.x + w + pulse, p.y + h + pulse, p.z + w + pulse), color, 1.0F);
   }

   private void drawBlinkBox(EventRender3D e, Entity target) {
      Vec3d p = this.interp(e, target);
      double w = target.getWidth() / 2.0D;
      double h = target.getHeight();
      int alpha = (int)((Math.sin((double)(System.currentTimeMillis() - INIT_TIME) / 150.0D) * 0.5D + 0.5D) * 255.0D);
      int color = Pain.getInstance().getThemeManager().getClientColor(90).withAlpha(alpha).getRGB();
      Render3DUtil.drawBox(new Box(p.x - w, p.y, p.z - w, p.x + w, p.y + h, p.z + w), color, 1.5F);
   }

   private void drawBox(EventRender3D e, Entity target) {
      float delta = e.getPartialTicks();
      double x = target.lastRenderX + (target.getX() - target.lastRenderX) * (double)delta;
      double y = target.lastRenderY + (target.getY() - target.lastRenderY) * (double)delta;
      double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * (double)delta;
      double w = target.getWidth() / 2.0D;
      Box box = new Box(x - w, y, z - w, x + w, y + (double)target.getHeight(), z + w);
      int color = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
      Render3DUtil.drawBox(box, color, 1.5F);
   }

   private void drawPulseBox(EventRender3D e, Entity target) {
      float delta = e.getPartialTicks();
      double x = target.lastRenderX + (target.getX() - target.lastRenderX) * (double)delta;
      double y = target.lastRenderY + (target.getY() - target.lastRenderY) * (double)delta;
      double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * (double)delta;
      double w = target.getWidth() / 2.0D;
      double pulse = 0.15D * (Math.sin((double)(System.currentTimeMillis() - INIT_TIME) / 250.0D) * 0.5D + 0.5D);
      Box box = new Box(x - w - pulse, y - pulse, z - w - pulse, x + w + pulse, y + (double)target.getHeight() + pulse, z + w + pulse);
      int color = Pain.getInstance().getThemeManager().getClientColor(90).withAlpha(160).getRGB();
      Render3DUtil.drawBox(box, color, 1.0F);
   }

   private void drawThunderTarget(EventRender3D e, Entity target) {
      float delta = e.getPartialTicks();
      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      double x = target.prevX + (target.getX() - target.prevX) * (double)delta - cam.x;
      double y = target.prevY + (target.getY() - target.prevY) * (double)delta - cam.y;
      double z = target.prevZ + (target.getZ() - target.prevZ) * (double)delta - cam.z;
      MatrixStack matrices = e.getMatrix();
      matrices.push();
      matrices.translate(x, y, z);
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();

      for(int ring = 0; ring < 3; ++ring) {
         BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
         double height = target.getHeight();
         double offsetDeg = (double)ring * 120.0D;
         java.util.List<Vec3d> verts = new java.util.ArrayList<>();

         for(int i = 0; i <= 361; ++i) {
            double v = Math.sin(Math.toRadians((double)i + offsetDeg));
            double u = Math.cos(Math.toRadians((double)i + offsetDeg));
            verts.add(new Vec3d(u * 0.5D, height, v * 0.5D));
            height -= 0.004D;
         }

         for(int j = 0; j < verts.size() - 1; ++j) {
            float alpha = 1.0F - ((float)j + (float)(System.currentTimeMillis() - INIT_TIME) / 5.0F) % 360.0F / 60.0F;
            if (alpha < 0.0F) {
               alpha = 0.0F;
            }

            int rgb = Pain.getInstance().getThemeManager().getClientColor((int)((float)j / 20.0F)).withAlpha((int)(alpha * 255.0F)).getRGB();
            Vec3d a = verts.get(j);
            Vec3d b = verts.get(j + 1);
            buffer.vertex(matrix, (float)a.x, (float)a.y, (float)a.z).color(rgb);
            buffer.vertex(matrix, (float)b.x, (float)b.y + 0.1F, (float)b.z).color(rgb);
         }

         BufferRenderer.drawWithGlobalProgram(buffer.end());
      }

      RenderSystem.enableDepthTest();
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      matrices.pop();
   }

   private void drawOldTarget(EventRender3D e, Entity target) {
      float delta = e.getPartialTicks();
      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      double anim = Math.sin((double)(System.currentTimeMillis() - INIT_TIME) / 600.0D) * 0.5D + 0.5D;
      double x = target.prevX + (target.getX() - target.prevX) * (double)delta - cam.x;
      double yBase = target.prevY + (target.getY() - target.prevY) * (double)delta - cam.y;
      double z = target.prevZ + (target.getZ() - target.prevZ) * (double)delta - cam.z;
      double nextY = yBase + anim * (double)target.getHeight();
      MatrixStack matrices = e.getMatrix();
      matrices.push();
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

      for(int i = 0; i <= 30; ++i) {
         float cos = (float)(x + Math.cos((double)i * 6.28D / 30.0D) * (double)target.getWidth() * 0.8D);
         float sin = (float)(z + Math.sin((double)i * 6.28D / 30.0D) * (double)target.getWidth() * 0.8D);
         int top = Pain.getInstance().getThemeManager().getClientColor(i).withAlpha(170).getRGB();
         int bottom = Pain.getInstance().getThemeManager().getClientColor(i).withAlpha(0).getRGB();
         buffer.vertex(matrix, cos, (float)nextY, sin).color(top);
         buffer.vertex(matrix, cos, (float)yBase, sin).color(bottom);
      }

      BufferRenderer.drawWithGlobalProgram(buffer.end());
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
      matrices.pop();
   }

   private void drawCaptureMark(EventRender3D e, Entity target) {
      float delta = e.getPartialTicks();
      net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
      Vec3d cam = camera.getPos();
      double x = target.prevX + (target.getX() - target.prevX) * (double)delta - cam.x;
      double y = target.prevY + (target.getY() - target.prevY) * (double)delta - cam.y;
      double z = target.prevZ + (target.getZ() - target.prevZ) * (double)delta - cam.z;
      float rot = (float)(System.currentTimeMillis() - INIT_TIME) / 20.0F % 360.0F;
      MatrixStack matrices = new MatrixStack();
      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
      matrices.translate(x, y + (double)(target.getEyeHeight(target.getPose()) / 2.0F), z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(770, 1);
      RenderSystem.setShaderTexture(0, Pain.id("icons/marker.png"));
      matrices.translate(-0.75D, -0.75D, -0.01D);
      org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      int c0 = Pain.getInstance().getThemeManager().getClientColor(90).getRGB();
      int c1 = Pain.getInstance().getThemeManager().getClientColor(0).getRGB();
      int c2 = Pain.getInstance().getThemeManager().getClientColor(180).getRGB();
      int c3 = Pain.getInstance().getThemeManager().getClientColor(270).getRGB();
      buffer.vertex(matrix, 0.0F, 1.5F, 0.0F).texture(0.0F, 1.0F).color(c0);
      buffer.vertex(matrix, 1.5F, 1.5F, 0.0F).texture(1.0F, 1.0F).color(c1);
      buffer.vertex(matrix, 1.5F, 0.0F, 0.0F).texture(1.0F, 0.0F).color(c2);
      buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).texture(0.0F, 0.0F).color(c3);
      BufferRenderer.drawWithGlobalProgram(buffer.end());
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.blendFunc(770, 771);
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private void updateRotation() {
      if (!this.isReversing) {
         this.rotationSpeed += 0.01F;
         if ((double)this.rotationSpeed > 2.3D) {
            this.rotationSpeed = 2.3F;
            this.isReversing = true;
         }
      } else {
         this.rotationSpeed -= 0.01F;
         if ((double)this.rotationSpeed < -2.3D) {
            this.rotationSpeed = -2.3F;
            this.isReversing = false;
         }
      }

      this.rotationAngle += this.rotationSpeed;
      this.rotationAngle %= 360.0F;
   }

   public static double interpolate(double current, double old, double scale) {
      return old + (current - old) * scale;
   }

   private void drawSpiritsTrack(EventRender3D event3D) {
      Aura aura = Aura.INSTANCE;
      this.animation2.update(aura.getTarget() != null && aura.isEnabled());
      if ((double)this.animation2.getValue() != 0.0D) {
         MatrixStack e = event3D.getMatrix();
         if (aura.getTarget() != null) {
            if (this.lastTarget == null) {
               this.currentTime = System.currentTimeMillis();
            }

            this.lastTarget = aura.getTarget();
         }

         if (this.lastTarget != null) {
            this.animationNurik += (float)(5L * (System.currentTimeMillis() - this.currentTime)) / 600.0F;
            this.currentTime = System.currentTimeMillis();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, Pain.id("icons/glow.png"));
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(770, 1, 0, 1);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            double x = interpolate(this.lastTarget.getX(), this.lastTarget.lastRenderX, (double)event3D.getPartialTicks()) - mc.gameRenderer.getCamera().getPos().getX();
            double y = interpolate(this.lastTarget.getY(), this.lastTarget.lastRenderY, (double)event3D.getPartialTicks()) - mc.gameRenderer.getCamera().getPos().getY();
            double z = interpolate(this.lastTarget.getZ(), this.lastTarget.lastRenderZ, (double)event3D.getPartialTicks()) - mc.gameRenderer.getCamera().getPos().getZ();
            int n2 = 3;
            int n3 = 12;
            int n4 = 3 * n2;
            e.push();
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            for(int i = 0; i < n4; i += n2) {
               for(int j = 0; j < n3; ++j) {
                  ColorRGBA color = Pain.getInstance().getThemeManager().getCurrentTheme().getColor();
                  float f2 = this.animationNurik + (float)j * 0.1F;
                  float f3 = 0.8F;
                  float f4 = 0.5F;
                  int n5 = (int)Math.pow((double)i, 2.0D);
                  e.push();
                  e.translate(x + (double)(f3 * MathHelper.sin(f2 + (float)n5)), y + (double)f4 + (double)(0.3F * MathHelper.sin(this.animationNurik + (float)j * 0.2F)) + (double)(0.2F * (float)i), z + (double)(f3 * MathHelper.cos(f2 - (float)n5)));
                  e.scale(this.animation2.getValue() * (0.005F + (float)j / 2000.0F), this.animation2.getValue() * (0.005F + (float)j / 2000.0F), this.animation2.getValue() * (0.005F + (float)j / 2000.0F));
                  e.multiply(mc.gameRenderer.getCamera().getRotation());
                  int n7 = -25;
                  int n8 = 50;
                  buffer.vertex(e.peek().getPositionMatrix(), (float)n7, (float)(n7 + n8), 0.0F).texture(0.0F, 1.0F).color(color.withAlpha((int)(this.animation2.getValue() * 255.0F)).getRGB());
                  buffer.vertex(e.peek().getPositionMatrix(), (float)(n7 + n8), (float)(n7 + n8), 0.0F).texture(1.0F, 1.0F).color(color.withAlpha((int)(this.animation2.getValue() * 255.0F)).getRGB());
                  buffer.vertex(e.peek().getPositionMatrix(), (float)(n7 + n8), (float)n7, 0.0F).texture(1.0F, 0.0F).color(color.withAlpha((int)(this.animation2.getValue() * 255.0F)).getRGB());
                  buffer.vertex(e.peek().getPositionMatrix(), (float)n7, (float)n7, 0.0F).texture(0.0F, 0.0F).color(color.withAlpha((int)(this.animation2.getValue() * 255.0F)).getRGB());
                  e.pop();
               }
            }

            BufferRenderer.drawWithGlobalProgram(buffer.end());
            e.pop();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.blendFunc(770, 771);
            RenderSystem.enableCull();
         }

      }
   }
}
