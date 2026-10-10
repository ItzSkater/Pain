package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Вспышка в точке удара. В отличие от HitEffect здесь нет частиц: это один квадрат лицом к камере,
 * а плазму, кольцо, разрез или искры на нём рисует шейдер impact — поэтому края мягкие и свет «горит».
 */
@ModuleAnnotation(
   name = "HitFlash",
   category = Category.RENDER,
   description = "Светящаяся вспышка в точке удара: плазма, ударная волна, разрез или искры"
)
public final class HitFlash extends Module {
   public static final HitFlash INSTANCE = new HitFlash();
   private static final String[] MODES = new String[]{"Плазма", "Волна", "Разрез", "Искры"};
   private final ModeSetting effect = new ModeSetting("Эффект", "Плазма", "Волна", "Разрез", "Искры", "По очереди");
   private final NumberSetting radius = new NumberSetting("Радиус", 0.9F, 0.4F, 3.0F, 0.1F);
   private final NumberSetting duration = new NumberSetting("Длительность (мс)", 480.0F, 200.0F, 1200.0F, 20.0F);
   private final NumberSetting bright = new NumberSetting("Яркость", 1.0F, 0.3F, 3.0F, 0.05F);
   private final BooleanSetting themeColor = new BooleanSetting("Цвет темы", true);
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 70, 90), () -> !this.themeColor.isEnabled());
   private final BooleanSetting throughWalls = new BooleanSetting("Поверх всего", false);
   private final List<Flash> flashes = new ArrayList<>();
   private int next;

   private HitFlash() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.flashes.clear();
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      Entity target = event.getTarget();
      if (mc.player == null || target == null) {
         return;
      }

      // точка удара — на поверхности цели со стороны камеры, чтобы вспышку не съедала модель
      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Vec3d centre = target.getPos().add(0.0, (double)target.getHeight() * 0.6, 0.0);
      Vec3d toCam = cam.subtract(centre);
      double dist = toCam.length();
      double out = Math.min(dist * 0.5, (double)target.getWidth() * 0.5 + 0.25);
      Flash flash = new Flash();
      flash.pos = dist < 1.0E-3 ? centre : centre.add(toCam.multiply(out / dist));
      flash.born = System.currentTimeMillis();
      flash.life = this.duration.getCurrent();
      flash.seed = ThreadLocalRandom.current().nextFloat();
      flash.mode = this.pickMode();
      flash.rgb = this.themeColor.isEnabled() ? Pain.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB() & 0xFFFFFF : this.color.getColor().getRGB() & 0xFFFFFF;
      if (this.flashes.size() >= 12) {
         this.flashes.remove(0);
      }

      this.flashes.add(flash);
   }

   private int pickMode() {
      for (int i = 0; i < MODES.length; ++i) {
         if (this.effect.is(MODES[i])) {
            return i;
         }
      }

      return this.next++ % MODES.length;
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (this.flashes.isEmpty() || mc.player == null || DrawUtil.impactProgram == null) {
         return;
      }

      long now = System.currentTimeMillis();
      this.flashes.removeIf(f -> (float)(now - f.born) >= f.life);
      if (this.flashes.isEmpty() || DrawUtil.impactProgram.use() == null) {
         return;
      }

      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cam = camera.getPos();
      Vector3f up = camera.getVerticalPlane();
      Vector3f side = camera.getDiagonalPlane();
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      RenderSystem.enableBlend();
      // чистое сложение: свет накладывается на картинку и может пережигать в белый
      RenderSystem.blendFuncSeparate(1, 1, 0, 1);
      RenderSystem.disableCull();
      RenderSystem.depthMask(false);
      if (this.throughWalls.isEnabled()) {
         RenderSystem.disableDepthTest();
      } else {
         RenderSystem.enableDepthTest();
      }

      for (Flash f : this.flashes) {
         float size = this.radius.getCurrent();
         float x = (float)(f.pos.x - cam.x);
         float y = (float)(f.pos.y - cam.y);
         float z = (float)(f.pos.z - cam.z);
         DrawUtil.impactProgram.use();
         DrawUtil.impactProgram.findUniform("Progress").set((float)(now - f.born) / f.life);
         DrawUtil.impactProgram.findUniform("Time").set((float)(now % 100000L) / 1000.0F);
         DrawUtil.impactProgram.findUniform("Mode").set((float)f.mode);
         DrawUtil.impactProgram.findUniform("Bright").set(this.bright.getCurrent());
         DrawUtil.impactProgram.findUniform("Seed").set(f.seed);
         int c = ParticleShapes.withAlpha(f.rgb, 1.0F);
         BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
         bb.vertex(mat, x - (side.x + up.x) * size, y - (side.y + up.y) * size, z - (side.z + up.z) * size).color(c);
         bb.vertex(mat, x - (side.x - up.x) * size, y - (side.y - up.y) * size, z - (side.z - up.z) * size).color(c);
         bb.vertex(mat, x + (side.x + up.x) * size, y + (side.y + up.y) * size, z + (side.z + up.z) * size).color(c);
         bb.vertex(mat, x + (side.x - up.x) * size, y + (side.y - up.y) * size, z + (side.z - up.z) * size).color(c);
         BufferRenderer.drawWithGlobalProgram(bb.end());
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
      RenderSystem.enableCull();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();
   }

   private static final class Flash {
      Vec3d pos;
      long born;
      float life;
      float seed;
      int mode;
      int rgb;
   }
}
