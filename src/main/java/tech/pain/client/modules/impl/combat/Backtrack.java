package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.base.events.impl.server.EventPacket;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.level.ParticleShapes;

/**
 * Бектрек: пакеты движения цели придерживаются на заданное время, поэтому у тебя на экране цель
 * стоит там, где была чуть раньше. Пока она убегает, её можно бить по старой позиции.
 * Если настоящая позиция оказалась ближе старой, задержка сразу снимается — отставание тогда только мешает.
 */
@ModuleAnnotation(
   name = "Backtrack",
   category = Category.COMBAT,
   description = "Задерживает движение цели: убегающего игрока можно бить по его прошлой позиции"
)
public final class Backtrack extends Module {
   public static final Backtrack INSTANCE = new Backtrack();
   private final NumberSetting delay = new NumberSetting("Задержка, мс", 150.0F, 50.0F, 5000.0F, 50.0F);
   private final NumberSetting range = new NumberSetting("Дистанция", 6.0F, 3.0F, 8.0F, 0.5F);
   private final BooleanSetting showReal = new BooleanSetting("Показывать настоящую позицию", true);
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 60, 80));
   // пакеты приходят в сетевом потоке, а применяются в игровом, поэтому очередь под замком
   private final ArrayDeque<Held> held = new ArrayDeque<>();
   private final TrackedPosition tracker = new TrackedPosition();
   private volatile int targetId = -1;
   private Vec3d realPos;

   private Backtrack() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.targetId = -1;
      this.flush();
   }

   @EventTarget
   public void onPacket(EventPacket event) {
      int id = this.targetId;
      if (id == -1 || !event.isReceive() || mc.world == null) {
         return;
      }

      Packet<?> packet = event.getPacket();
      synchronized (this.held) {
         if (this.realPos == null) {
            return;
         }

         if (packet instanceof EntityS2CPacket move) {
            Entity entity = move.getEntity(mc.world);
            if (entity == null || entity.getId() != id) {
               return;
            }

            if (move.isPositionChanged()) {
               this.tracker.setPos(this.realPos);
               this.realPos = this.tracker.withDelta((long)move.getDeltaX(), (long)move.getDeltaY(), (long)move.getDeltaZ());
            }
         } else if (packet instanceof EntityPositionSyncS2CPacket sync && sync.id() == id) {
            this.realPos = sync.values().position();
         } else {
            return;
         }

         this.held.add(new Held(packet, System.currentTimeMillis()));
      }

      event.cancel();
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         this.targetId = -1;
         synchronized (this.held) {
            this.held.clear();
            this.realPos = null;
         }
         return;
      }

      LivingEntity target = this.pickTarget();
      int id = target == null ? -1 : target.getId();
      if (id != this.targetId) {
         this.targetId = -1;
         this.flush();
         if (target != null) {
            synchronized (this.held) {
               this.realPos = target.getTrackedPosition().getPos();
            }

            this.targetId = id;
         }
      }
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null || this.targetId == -1) {
         return;
      }

      Entity target = mc.world.getEntityById(this.targetId);
      Vec3d real;
      boolean lagging;
      synchronized (this.held) {
         real = this.realPos;
         lagging = !this.held.isEmpty();
      }

      if (target == null || real == null) {
         this.flush();
         return;
      }

      Vec3d eye = mc.player.getEyePos();
      double realDist = eye.distanceTo(real);
      // настоящая позиция ближе показанной или цель уже слишком далеко — держать пакеты незачем
      if (lagging && (realDist <= eye.distanceTo(target.getPos()) || realDist > (double)this.range.getCurrent() + 1.0)) {
         this.flush();
         return;
      }

      this.release(System.currentTimeMillis() - (long)this.delay.getCurrent());
      if (lagging && this.showReal.isEnabled()) {
         this.drawReal(event, real, target);
      }
   }

   private LivingEntity pickTarget() {
      double max = (double)this.range.getCurrent();
      LivingEntity aura = Aura.INSTANCE.isEnabled() ? Aura.INSTANCE.getTarget() : null;
      if (aura != null && aura.isAlive() && (double)mc.player.distanceTo(aura) <= max) {
         return aura;
      }

      PlayerEntity best = null;
      double bestDist = max;

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player == mc.player || !player.isAlive() || Pain.getInstance().getFriendManager().isFriend(player.getName().getString())) {
            continue;
         }

         double dist = (double)mc.player.distanceTo(player);
         if (dist <= bestDist) {
            bestDist = dist;
            best = player;
         }
      }

      return best;
   }

   // применяет придержанные пакеты старше указанного момента
   private void release(long olderThan) {
      while (true) {
         Held next;
         synchronized (this.held) {
            next = this.held.peek();
            if (next == null || next.time > olderThan) {
               return;
            }

            this.held.poll();
         }

         apply(next.packet);
      }
   }

   private void flush() {
      this.release(Long.MAX_VALUE);
   }

   @SuppressWarnings("unchecked")
   private static void apply(Packet<?> packet) {
      if (mc.getNetworkHandler() != null && mc.isOnThread()) {
         try {
            ((Packet<ClientPlayPacketListener>)packet).apply(mc.getNetworkHandler());
         } catch (Exception ignored) {
         }
      }
   }

   private void drawReal(EventRender3D event, Vec3d real, Entity target) {
      Vec3d cam = mc.gameRenderer.getCamera().getPos();
      Matrix4f mat = event.getMatrix().peek().getPositionMatrix();
      float half = target.getWidth() / 2.0F;
      float x0 = (float)(real.x - cam.x) - half;
      float y0 = (float)(real.y - cam.y);
      float z0 = (float)(real.z - cam.z) - half;
      float x1 = x0 + half * 2.0F;
      float y1 = y0 + target.getHeight();
      float z1 = z0 + half * 2.0F;
      int rgb = this.color.getColor().getRGB() & 0xFFFFFF;
      int fill = ParticleShapes.withAlpha(rgb, 0.18F);
      int line = ParticleShapes.withAlpha(rgb, 0.9F);
      float w = 0.008F;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      box(bb, mat, x0, y0, z0, x1, y1, z1, fill);

      for (int i = 0; i < 2; ++i) {
         for (int j = 0; j < 2; ++j) {
            float ya = i == 0 ? y0 : y1;
            float za = j == 0 ? z0 : z1;
            float xa = i == 0 ? x0 : x1;
            float yb = j == 0 ? y0 : y1;
            box(bb, mat, x0 - w, ya - w, za - w, x1 + w, ya + w, za + w, line);
            box(bb, mat, xa - w, y0 - w, za - w, xa + w, y1 + w, za + w, line);
            box(bb, mat, xa - w, yb - w, z0 - w, xa + w, yb + w, z1 + w, line);
         }
      }

      BuiltBuffer built = bb.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   private static void box(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
      quad(bb, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c);
      quad(bb, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c);
      quad(bb, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c);
      quad(bb, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c);
      quad(bb, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c);
      quad(bb, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, c);
   }

   private static void quad(
      BufferBuilder bb, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int c
   ) {
      bb.vertex(m, ax, ay, az).color(c);
      bb.vertex(m, bx, by, bz).color(c);
      bb.vertex(m, cx, cy, cz).color(c);
      bb.vertex(m, ax, ay, az).color(c);
      bb.vertex(m, cx, cy, cz).color(c);
      bb.vertex(m, dx, dy, dz).color(c);
   }

   private record Held(Packet<?> packet, long time) {
   }
}
