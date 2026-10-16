package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender2D;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.combat.AntiBot;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.client.modules.impl.misc.ScoreboardHealth;
import tech.pain.utility.game.player.PlayerIntersectionUtil;
import tech.pain.utility.math.MathUtil;
import tech.pain.utility.math.ProjectionUtil;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Стрелки у края экрана на врагов, которых не видно. Чем ближе враг, тем стрелка крупнее и ярче;
 * под ней полоска здоровья, а если враг быстро приближается или уже рядом — стрелка пульсирует.
 */
@ModuleAnnotation(
   name = "Arrows",
   category = Category.RENDER,
   description = "Стрелки у края экрана на врагов вне поля зрения: размер от дистанции, полоска HP, пульс при приближении"
)
public final class Arrows extends Module {
   public static final Arrows INSTANCE = new Arrows();
   private final ModeSetting place = new ModeSetting("Где", "Край экрана", "Вокруг прицела");
   private final NumberSetting radius = new NumberSetting("Радиус у прицела", 70.0F, 25.0F, 220.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.place.is("Вокруг прицела"));
   private final NumberSetting margin = new NumberSetting("Отступ от края", 26.0F, 8.0F, 120.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.place.is("Край экрана"));
   private final NumberSetting size = new NumberSetting("Размер", 10.0F, 5.0F, 24.0F, 0.5F);
   private final NumberSetting range = new NumberSetting("Дальность", 96.0F, 16.0F, 256.0F, 4.0F);
   private final BooleanSetting onlyHidden = new BooleanSetting("Только кого не видно", true);
   private final BooleanSetting showHp = new BooleanSetting("Полоска здоровья", true);
   private final BooleanSetting showDistance = new BooleanSetting("Расстояние", true);
   private final BooleanSetting pulse = new BooleanSetting("Пульс при приближении", true);
   private final BooleanSetting friends = new BooleanSetting("Друзья", false);
   private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "По расстоянию", "Свой");
   private final ColorSetting color = new ColorSetting("Свой цвет", new ColorRGBA(255, 70, 90), () -> this.colorMode.is("Свой"));
   // сглаженная скорость сближения по каждому игроку: блоки в секунду, плюс — подходит
   private final Map<Integer, float[]> approach = new HashMap<>();
   private long lastFrame;

   private Arrows() {
   }

   @EventTarget
   public void onRender2D(EventRender2D event) {
      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      if (mc.player == null || mc.world == null || mc.options.hudHidden) {
         return;
      }

      CustomDrawContext ctx = event.getContext();
      Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cam = camera.getPos();
      float yaw = (float)Math.toRadians((double)camera.getYaw());
      float sin = MathHelper.sin(yaw);
      float cos = MathHelper.cos(yaw);
      float w = (float)mc.getWindow().getScaledWidth();
      float h = (float)mc.getWindow().getScaledHeight();
      float cx = w / 2.0F;
      float cy = h / 2.0F;
      double time = (double)now / 1000.0;
      LivingEntity target = Aura.INSTANCE.isEnabled() ? Aura.INSTANCE.getTarget() : null;
      List<Object[]> labels = new ArrayList<>();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      Map<Integer, float[]> seen = new HashMap<>();

      for (PlayerEntity p : mc.world.getPlayers()) {
         if (p == mc.player || !p.isAlive() || p.isSpectator()) {
            continue;
         }

         if (AntiBot.INSTANCE.isEnabled() && AntiBot.INSTANCE.isBot(p)) {
            continue;
         }

         boolean friend = Pain.getInstance().getFriendManager().isFriend(p.getName().getString());
         if (friend && !this.friends.isEnabled()) {
            continue;
         }

         float dist = mc.player.distanceTo(p);
         if (dist > this.range.getCurrent()) {
            continue;
         }

         Vec3d pos = MathUtil.interpolate(p);
         // сближение: насколько быстро сокращается дистанция
         float[] state = this.approach.getOrDefault(p.getId(), new float[]{dist, 0.0F});
         float speed = dt > 0.0F ? (state[0] - dist) / dt : 0.0F;
         state[1] += (speed - state[1]) * Math.min(1.0F, dt * 4.0F);
         state[0] = dist;
         seen.put(p.getId(), state);
         if (this.onlyHidden.isEnabled()) {
            Vec3d screen = ProjectionUtil.worldSpaceToScreenSpace(pos.add(0.0, (double)p.getHeight() * 0.5, 0.0));
            if (screen.z > 0.0 && screen.z < 1.0 && screen.x > 0.0 && screen.x < (double)w && screen.y > 0.0 && screen.y < (double)h) {
               continue;
            }
         }

         double dx = pos.x - cam.x;
         double dz = pos.z - cam.z;
         double flat = Math.sqrt(dx * dx + dz * dz);
         if (flat < 0.3) {
            continue;
         }

         // направление на экране: вперёд — вверх, вправо — вправо
         float fwd = (float)((-dx * (double)sin + dz * (double)cos) / flat);
         float side = (float)((-dx * (double)cos - dz * (double)sin) / flat);
         float ax = side;
         float ay = -fwd;
         float x;
         float y;
         if (this.place.is("Край экрана")) {
            float m = this.margin.getCurrent();
            float k = Math.min((cx - m) / Math.max(1.0E-4F, Math.abs(ax)), (cy - m) / Math.max(1.0E-4F, Math.abs(ay)));
            x = cx + ax * k;
            y = cy + ay * k;
         } else {
            float r = this.radius.getCurrent();
            x = cx + ax * r;
            y = cy + ay * r;
         }

         float near = MathHelper.clamp(1.0F - dist / this.range.getCurrent(), 0.0F, 1.0F);
         float s = this.size.getCurrent() * (0.65F + 0.55F * near);
         float alpha = 0.4F + 0.6F * near;
         boolean danger = this.pulse.isEnabled() && (state[1] > 3.0F || dist < 6.0F);
         if (danger) {
            float beat = 0.5F + 0.5F * (float)Math.sin(time * 10.0);
            s *= 1.0F + 0.25F * beat;
            alpha = Math.min(1.0F, alpha + 0.3F * beat);
         }

         int rgb;
         if (p == target) {
            rgb = 0xFF3B4A;
         } else if (friend) {
            rgb = 0x6EE89A;
         } else if (this.colorMode.is("По расстоянию")) {
            rgb = ParticleShapes.rainbow(0.33F * (1.0F - near), 0.8F, 1.0F);
         } else if (this.colorMode.is("Свой")) {
            rgb = this.color.getColor().getRGB() & 0xFFFFFF;
         } else {
            rgb = Pain.getInstance().getThemeManager().getClientColor((int)(dist * 6.0F)).getRGB() & 0xFFFFFF;
         }

         arrow(bb, mat, x, y, ax, ay, s, rgb, alpha);
         float hp = ScoreboardHealth.INSTANCE.isEnabled() ? PlayerIntersectionUtil.getHealth(p) : p.getHealth();
         labels.add(new Object[]{x - ax * (s + 6.0F), y - ay * (s + 6.0F), hp / Math.max(1.0F, p.getMaxHealth()), dist, alpha, rgb});
      }

      this.approach.keySet().retainAll(seen.keySet());
      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      Font font = Fonts.MEDIUM.getFont(6.0F);

      for (Object[] l : labels) {
         float lx = (Float)l[0];
         float ly = (Float)l[1];
         float hpPart = MathHelper.clamp((Float)l[2], 0.0F, 1.0F);
         float a = (Float)l[4];
         float bw = 16.0F;
         if (this.showHp.isEnabled()) {
            ColorRGBA hpColor = ColorRGBA.lerp(new ColorRGBA(255, 70, 70), new ColorRGBA(110, 235, 120), hpPart);
            DrawUtil.drawRoundedRect(ctx.getMatrices(), lx - bw / 2.0F, ly - 1.5F, bw, 3.0F, BorderRadius.all(1.5F), new ColorRGBA(0, 0, 0, 150.0F * a));
            DrawUtil.drawRoundedRect(ctx.getMatrices(), lx - bw / 2.0F, ly - 1.5F, Math.max(3.0F, bw * hpPart), 3.0F, BorderRadius.all(1.5F), hpColor.withAlpha(255.0F * a));
         }

         if (this.showDistance.isEnabled()) {
            String text = (int)(float)(Float)l[3] + "m";
            float tw = font.width(text);
            ctx.drawText(font, text, lx - tw / 2.0F, ly + 3.0F, new ColorRGBA(235, 235, 240, 230.0F * a));
         }
      }
   }

   /** Треугольник остриём наружу: тёмная подложка, мягкое свечение и яркое остриё. */
   private static void arrow(BufferBuilder bb, Matrix4f m, float x, float y, float dx, float dy, float s, int rgb, float alpha) {
      float nx = -dy;
      float ny = dx;
      float tipX = x + dx * s;
      float tipY = y + dy * s;
      float lx = x - dx * s * 0.6F + nx * s * 0.7F;
      float ly = y - dy * s * 0.6F + ny * s * 0.7F;
      float rx = x - dx * s * 0.6F - nx * s * 0.7F;
      float ry = y - dy * s * 0.6F - ny * s * 0.7F;
      // выемка у основания — получается стрелка, а не просто треугольник
      float notchX = x - dx * s * 0.2F;
      float notchY = y - dy * s * 0.2F;
      float gx = x;
      float gy = y;
      int glow = ParticleShapes.withAlpha(rgb, alpha * 0.35F);
      int clear = ParticleShapes.withAlpha(rgb, 0.0F);
      float g = 2.1F;
      tri(bb, m, gx, gy, glow, gx + (tipX - gx) * g, gy + (tipY - gy) * g, clear, gx + (lx - gx) * g, gy + (ly - gy) * g, clear);
      tri(bb, m, gx, gy, glow, gx + (rx - gx) * g, gy + (ry - gy) * g, clear, gx + (tipX - gx) * g, gy + (tipY - gy) * g, clear);
      tri(bb, m, gx, gy, glow, gx + (lx - gx) * g, gy + (ly - gy) * g, clear, gx + (rx - gx) * g, gy + (ry - gy) * g, clear);
      float o = 1.18F;
      int shadow = ParticleShapes.withAlpha(0, alpha * 0.5F);
      tri(bb, m, x + (tipX - x) * o, y + (tipY - y) * o, shadow, x + (lx - x) * o, y + (ly - y) * o, shadow, notchX, notchY, shadow);
      tri(bb, m, x + (tipX - x) * o, y + (tipY - y) * o, shadow, notchX, notchY, shadow, x + (rx - x) * o, y + (ry - y) * o, shadow);
      int tip = ParticleShapes.withAlpha(mix(rgb, 0xFFFFFF, 0.45F), alpha);
      int base = ParticleShapes.withAlpha(rgb, alpha * 0.85F);
      tri(bb, m, tipX, tipY, tip, lx, ly, base, notchX, notchY, base);
      tri(bb, m, tipX, tipY, tip, notchX, notchY, base, rx, ry, base);
   }

   private static void tri(BufferBuilder bb, Matrix4f m, float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc) {
      bb.vertex(m, ax, ay, 0.0F).color(ca);
      bb.vertex(m, bx, by, 0.0F).color(cb);
      bb.vertex(m, cx, cy, 0.0F).color(cc);
   }

   private static int mix(int a, int b, float k) {
      int r = (int)((float)(a >> 16 & 0xFF) * (1.0F - k) + (float)(b >> 16 & 0xFF) * k);
      int g = (int)((float)(a >> 8 & 0xFF) * (1.0F - k) + (float)(b >> 8 & 0xFF) * k);
      int bl = (int)((float)(a & 0xFF) * (1.0F - k) + (float)(b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }
}
