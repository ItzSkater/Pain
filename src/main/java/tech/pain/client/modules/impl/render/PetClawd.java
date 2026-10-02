package tech.pain.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.utility.math.MathUtil;
import tech.pain.utility.render.level.Model3D;

/**
 * Один питомец Claw'd: состояние, поведение и отрисовка. Настройки общие — берутся из модуля Pet.
 */
final class PetClawd implements tech.pain.utility.interfaces.IMinecraft {
   private static final float GRAVITY = 24.0F;
   private final NumberSetting size = Pet.INSTANCE.size;
   private final NumberSetting sleepAfter = Pet.INSTANCE.sleepAfter;
   private final BooleanSetting attack = Pet.INSTANCE.attack;
   private final BooleanSetting sword = Pet.INSTANCE.sword;
   private final BooleanSetting shadow = Pet.INSTANCE.shadow;
   private final BooleanSetting eatWithPlayer = Pet.INSTANCE.eatWithPlayer;
   private final BooleanSetting jetpack = Pet.INSTANCE.jetpack;
   private final BooleanSetting jetpackAlways = Pet.INSTANCE.jetpackAlways;
   private final BooleanSetting parachute = Pet.INSTANCE.parachute;
   /** Вид питомца: какие модели рисовать и где у него лапы, глаза, рот, меч и ранец (в единицах модели). */
   record Rig(String name, String body, String eyes, String[] legs, float[][] legPos, float legY, float eyeY, float[] sword, float jetY, float jetZ, float chuteY, float mouthY, float mouthZ, float height) {
   }

   static final Rig CLAWD = new Rig("Claw'd", "pet_body", "pet_eyes", new String[]{"pet_leg", "pet_leg", "pet_leg", "pet_leg"}, new float[][]{{-0.82F, 0.0F}, {-0.34F, 0.0F}, {0.34F, 0.0F}, {0.82F, 0.0F}}, 0.42F, 1.11F, new float[]{-1.22F, 0.93F, 0.32F}, 0.0F, 0.0F, 0.0F, 0.62F, 0.95F, 1.7F);
   /** Маузер: толстый белый кот с полосатой шапочкой; задняя левая лапа полосатая. */
   static final Rig MAUSER = new Rig("Mauser", "mauser_body", "mauser_eyes", new String[]{"mauser_leg", "mauser_leg", "mauser_leg_tabby", "mauser_leg"}, new float[][]{{-0.5F, 0.62F}, {0.5F, 0.62F}, {-0.55F, -0.72F}, {0.55F, -0.72F}}, 0.45F, 1.46F, new float[]{-1.0F, 0.8F, 0.85F}, 0.55F, 0.05F, 0.35F, 1.2F, 2.0F, 2.3F);

   static Rig rigByName(String name) {
      return "Mauser".equals(name) ? MAUSER : CLAWD;
   }

   private final Rig rig;
   private final int slot;
   private boolean held;
   private float heldAmount;
   private boolean sitting;
   private float sitAmount;
   private float flyAmount;
   private float flyTilt;
   private float playerAirTime;
   private boolean chute;
   private float chuteAmount;
   private float smokeTimer;
   private float petTime;
   private float eatAmount;
   private float eatTime;
   private float crumbTimer;
   private net.minecraft.item.ItemStack eatStack = net.minecraft.item.ItemStack.EMPTY;
   private Vec3d pos;
   private double velY;
   private boolean onGround;
   private float yaw;
   private float walkPhase;
   private float moveSpeed;
   private float sleepAmount;
   private float swing = 1.0F;
   private float swingCooldown;
   private float blink;
   private float nextBlink = 3.0F;
   private float idleTime;
   private float jumpDelay = -1.0F;
   private boolean playerWasOnGround = true;
   private Vec3d lastPlayerPos;
   private Entity attackTarget;
   private float attackHold;
   private long lastFrame;
   private Object lastWorld;

   PetClawd(int slot, Rig rig) {
      this.slot = slot;
      this.rig = rig;
   }

   /** Место в строю: питомцы выстраиваются парами слева и справа позади игрока. */
   private double slotSide() {
      return (this.slot % 2 == 0 ? 1.0 : -1.0) * (1.1 + 0.85 * (double)(this.slot / 2));
   }

   private double slotBack() {
      return 0.5 + 0.65 * (double)(this.slot / 2);
   }

   int slot() {
      return this.slot;
   }

   boolean isSitting() {
      return this.sitting;
   }

   /** Сидит — остаётся на месте и ждёт: не бежит за игроком, не летит и не атакует. */
   void setSitting(boolean sitting) {
      this.sitting = sitting;
      this.idleTime = 0.0F;
   }

   boolean isHeld() {
      return this.held;
   }

   void setHeld(boolean held) {
      if (this.held && !held) {
         // отпустили — мягко падает из рук
         this.velY = 1.5;
         this.onGround = false;
         this.idleTime = 0.0F;
      }

      this.held = held;
      if (held) {
         this.sitting = false;
      }
   }

   double distanceTo(Vec3d point) {
      return this.pos == null ? Double.MAX_VALUE : this.pos.distanceTo(point);
   }

   /** Смотрит ли игрок на этого питомца (для выбора, кого взять в руки). */
   boolean aimedAt(double range) {
      if (this.pos == null) {
         return false;
      }

      float unit = 0.2F * this.size.getCurrent();
      Vec3d eye = mc.gameRenderer.getCamera().getPos();
      Vec3d look = Vec3d.fromPolar(mc.gameRenderer.getCamera().getPitch(), mc.gameRenderer.getCamera().getYaw());
      double half = (double)unit * 1.5;
      net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(this.pos.x - half, this.pos.y - 0.05, this.pos.z - half, this.pos.x + half, this.pos.y + (double)unit * 1.9, this.pos.z + half);
      return box.raycast(eye, eye.add(look.multiply(range))).isPresent();
   }

   /** В руках: в первом лице висит справа-снизу перед камерой, в F5 — перед грудью игрока. */
   private void holdUpdate(float dt) {
      Camera cam = mc.gameRenderer.getCamera();
      float unit = 0.2F * this.size.getCurrent();
      Vec3d target;
      float faceYaw;
      if (mc.options.getPerspective().isFirstPerson()) {
         double yawRad = Math.toRadians((double)cam.getYaw());
         Vec3d forward = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
         Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
         target = cam.getPos().add(forward.multiply(0.7 + (double)unit * 1.4)).add(right.multiply(0.4 + (double)unit * 0.4)).add(0.0, -0.36 - (double)unit * 0.9, 0.0);
         faceYaw = cam.getYaw() + 180.0F - 25.0F;
      } else {
         Vec3d p = MathUtil.interpolate(mc.player);
         double yawRad = Math.toRadians((double)mc.player.bodyYaw);
         target = p.add(-Math.sin(yawRad) * (0.5 + (double)unit), 0.75, Math.cos(yawRad) * (0.5 + (double)unit));
         faceYaw = mc.player.bodyYaw;
      }

      // чуть отстаёт от руки — выглядит живее, чем жёсткая привязка
      this.pos = this.pos == null ? target : this.pos.lerp(target, (double)Math.min(1.0F, dt * 22.0F));
      this.yaw = lerpAngle(this.yaw, faceYaw, Math.min(1.0F, dt * 14.0F));
      this.velY = 0.0;
      this.onGround = false;
      this.moveSpeed = 0.0F;
      this.idleTime = 0.0F;
      this.chute = false;
      this.attackTarget = null;
      this.walkPhase += dt * 7.0F;
      this.sleepAmount += (0.0F - this.sleepAmount) * Math.min(1.0F, dt * 6.0F);
      this.flyAmount += (0.0F - this.flyAmount) * Math.min(1.0F, dt * 6.0F);
      this.chuteAmount += (0.0F - this.chuteAmount) * Math.min(1.0F, dt * 7.0F);
      this.eatAmount += (0.0F - this.eatAmount) * Math.min(1.0F, dt * 7.0F);
      this.swing = Math.min(1.0F, this.swing + dt * 3.4F);
      this.petTime = Math.max(0.0F, this.petTime - dt);
      this.nextBlink -= dt;
      if (this.nextBlink <= 0.0F) {
         this.blink = 0.16F;
         this.nextBlink = 2.5F + (float)Math.random() * 3.5F;
      }

      this.blink = Math.max(0.0F, this.blink - dt);
   }

   void frame(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.05F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      Vec3d player = MathUtil.interpolate(mc.player);
      if (this.pos == null || this.lastWorld != mc.world || !this.held && !this.sitting && this.pos.distanceTo(player) > 24.0 * Math.max(1.0, (double)this.size.getCurrent() * 0.3)) {
         // новый мир или убежали слишком далеко — появляемся рядом
         this.lastWorld = mc.world;
         this.pos = player.add(this.slotSide(), 0.5, 0.6 + this.slotBack());
         this.velY = 0.0;
         this.lastPlayerPos = player;
         this.idleTime = 0.0F;
      }

      this.heldAmount += ((this.held ? 1.0F : 0.0F) - this.heldAmount) * Math.min(1.0F, dt * 8.0F);
      this.sitAmount += ((this.sitting && !this.held ? 1.0F : 0.0F) - this.sitAmount) * Math.min(1.0F, dt * 7.0F);
      if (this.held) {
         this.holdUpdate(dt);
      } else if (!mc.isPaused()) {
         this.simulate(dt, player);
      }

      this.render(event, (double)now / 1000.0);
   }

   // ---------------------------------------------------------------- поведение

   private void simulate(float dt, Vec3d player) {
      // стоит ли игрок: от этого зависит сон
      double moved = this.lastPlayerPos == null ? 1.0 : player.distanceTo(this.lastPlayerPos);
      this.lastPlayerPos = player;
      if (moved > 0.003) {
         this.idleTime = 0.0F;
      } else {
         this.idleTime += dt;
      }

      // игрок летит: креатив, элитры или чит-флай (долго в воздухе и не падает)
      boolean playerAir = !mc.player.isOnGround() && !mc.player.isTouchingWater() && !mc.player.isClimbing();
      this.playerAirTime = playerAir ? this.playerAirTime + dt : 0.0F;
      boolean playerFlying = mc.player.getAbilities().flying || mc.player.isGliding() || this.playerAirTime > 1.0F && mc.player.getVelocity().y > -0.4 && player.y - this.groundAt(player.x, player.y, player.z) > 2.5;
      boolean flying = this.jetpack.isEnabled() && playerFlying && !this.sitting;
      this.flyAmount += ((flying ? 1.0F : 0.0F) - this.flyAmount) * Math.min(1.0F, dt * 6.0F);
      if (flying) {
         this.simulateFlight(dt, player);
         return;
      }

      this.flyTilt += (0.0F - this.flyTilt) * Math.min(1.0F, dt * 6.0F);
      this.updateTarget(dt);
      if (this.sitting) {
         this.attackTarget = null;
      }

      boolean attacking = this.attackTarget != null;
      if (attacking) {
         this.idleTime = 0.0F;
      }

      // ест то же, что и игрок: яблоко, золотое, зачарованное яблоко или золотую морковку
      net.minecraft.item.ItemStack using = mc.player.isUsingItem() ? mc.player.getActiveItem() : net.minecraft.item.ItemStack.EMPTY;
      boolean eating = this.eatWithPlayer.isEnabled() && !attacking && isPetFood(using);
      if (eating) {
         if (this.eatStack.isEmpty() || this.eatStack.getItem() != using.getItem()) {
            this.eatStack = using.copyWithCount(1);
            this.eatTime = 0.0F;
         }

         this.eatTime += dt;
         this.idleTime = 0.0F;
         this.crumbTimer -= dt;
         if (this.crumbTimer <= 0.0F && this.eatTime > 0.35F) {
            this.crumbTimer = 0.18F;
            this.spawnCrumbs();
         }
      } else if (this.eatAmount > 0.05F && this.eatTime > 1.2F && this.onGround) {
         // доел — довольный подскок
         this.velY = 4.5;
         this.onGround = false;
         this.eatTime = 0.0F;
      }

      this.eatAmount += ((eating ? 1.0F : 0.0F) - this.eatAmount) * Math.min(1.0F, dt * 7.0F);
      if (this.eatAmount < 0.02F && !eating) {
         this.eatStack = net.minecraft.item.ItemStack.EMPTY;
      }

      if (this.petTime > 0.0F) {
         this.petTime -= dt;
         this.idleTime = Math.min(this.idleTime, this.sleepAfter.getCurrent() - 4.0F);
      }

      boolean sleeping = !attacking && this.idleTime > this.sleepAfter.getCurrent();
      this.sleepAmount += ((sleeping ? 1.0F : 0.0F) - this.sleepAmount) * Math.min(1.0F, dt * 4.0F);

      // прыжок вместе с игроком, с маленькой задержкой — как будто повторяет
      boolean playerGround = mc.player.isOnGround();
      if (this.playerWasOnGround && !playerGround && mc.player.getVelocity().y > 0.2) {
         this.jumpDelay = 0.12F;
      }

      this.playerWasOnGround = playerGround;
      if (this.jumpDelay >= 0.0F) {
         this.jumpDelay -= dt;
         if (this.jumpDelay < 0.0F && this.onGround && !sleeping && !this.sitting) {
            this.velY = 7.6;
            this.onGround = false;
         }
      }

      // куда идти: к цели атаки или на своё место справа-сзади от игрока
      Vec3d goal;
      double stop;
      if (attacking) {
         Vec3d t = MathUtil.interpolate(this.attackTarget);
         Vec3d from = this.pos.subtract(t);
         Vec3d flat = new Vec3d(from.x, 0.0, from.z);
         double reach = (double)this.attackTarget.getWidth() * 0.5 + 0.55 * Math.max(1.0, (double)this.size.getCurrent() * 0.6);
         goal = flat.lengthSquared() < 1.0E-4 ? t.add(reach, 0.0, 0.0) : t.add(flat.normalize().multiply(reach));
         stop = 0.2;
      } else {
         double yawRad = Math.toRadians((double)mc.player.getYaw());
         Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
         Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
         double big = Math.max(1.0, (double)this.size.getCurrent() * 0.45);
         goal = player.add(right.multiply(this.slotSide() * big)).subtract(forward.multiply(this.slotBack() * big));
         stop = 0.45 * big;
      }

      double dx = goal.x - this.pos.x;
      double dz = goal.z - this.pos.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float targetSpeed = 0.0F;
      if (!sleeping && !this.sitting && dist > stop && !eating && this.petTime <= 0.0F) {
         targetSpeed = (float)Math.min(attacking ? 9.0 : 8.0, 1.5 + dist * 3.2);
      }

      this.moveSpeed += (targetSpeed - this.moveSpeed) * Math.min(1.0F, dt * 10.0F);
      if (this.moveSpeed > 0.05F && dist > 1.0E-3) {
         double step = Math.min(dist, (double)(this.moveSpeed * dt));
         double nx = this.pos.x + dx / dist * step;
         double nz = this.pos.z + dz / dist * step;
         double ground = this.groundAt(nx, this.pos.y, nz);
         if (ground > this.pos.y + 0.05) {
            // впереди ступенька: запрыгиваем, а двигаться вперёд начинаем, когда поднялись
            if (this.onGround && ground - this.pos.y <= this.stepHeight()) {
               // прыжок ровно такой, чтобы хватило на эту высоту
               this.velY = Math.max(8.2, Math.sqrt(2.0 * (double)GRAVITY * (ground - this.pos.y)) + 1.5);
               this.onGround = false;
            }
         } else {
            this.pos = new Vec3d(nx, this.pos.y, nz);
         }

         this.walkPhase += (float)step * 9.0F;
         this.yaw = lerpAngle(this.yaw, (float)Math.toDegrees(Math.atan2(-dx, dz)), Math.min(1.0F, dt * 12.0F));
      } else if (!sleeping) {
         // стоим — смотрим на цель или на игрока
         Vec3d look = attacking ? MathUtil.interpolate(this.attackTarget) : player;
         this.yaw = lerpAngle(this.yaw, (float)Math.toDegrees(Math.atan2(-(look.x - this.pos.x), look.z - this.pos.z)), Math.min(1.0F, dt * 8.0F));
      }

      // гравитация и земля
      double groundHere = this.groundAt(this.pos.x, this.pos.y, this.pos.z);
      this.velY -= (double)(GRAVITY * dt);
      // падает с большой высоты — раскрывает парашют и плавно спускается
      if (this.parachute.isEnabled() && !this.onGround && this.velY < -7.0 && this.pos.y - groundHere > 6.0) {
         this.chute = true;
      }

      if (this.chute) {
         this.velY += (-3.2 - this.velY) * (double)Math.min(1.0F, dt * 5.0F);
      }

      double ny = this.pos.y + this.velY * (double)dt;
      if (ny <= groundHere) {
         ny = groundHere;
         this.velY = 0.0;
         this.onGround = true;
         this.chute = false;
      } else {
         this.onGround = false;
      }

      this.chuteAmount += ((this.chute ? 1.0F : 0.0F) - this.chuteAmount) * Math.min(1.0F, dt * 7.0F);

      this.pos = new Vec3d(this.pos.x, ny, this.pos.z);

      // удары мечом, когда добежал до цели
      this.swing = Math.min(1.0F, this.swing + dt * 3.4F);
      this.swingCooldown -= dt;
      if (attacking && dist < 0.6 * Math.max(1.0, (double)this.size.getCurrent() * 0.5) && this.swingCooldown <= 0.0F) {
         this.swing = 0.0F;
         this.swingCooldown = 0.55F;
         if (this.onGround) {
            this.velY = 3.2;
         }
      }

      // моргание
      this.nextBlink -= dt;
      if (this.nextBlink <= 0.0F) {
         this.blink = 0.16F;
         this.nextBlink = 2.5F + (float)Math.random() * 3.5F;
      }

      this.blink = Math.max(0.0F, this.blink - dt);
   }

   /** Полёт на джетпаке: летит к своему месту рядом с игроком напрямую, без гравитации. */
   private void simulateFlight(float dt, Vec3d player) {
      this.idleTime = 0.0F;
      this.attackTarget = null;
      this.chute = false;
      this.chuteAmount += (0.0F - this.chuteAmount) * Math.min(1.0F, dt * 7.0F);
      this.sleepAmount += (0.0F - this.sleepAmount) * Math.min(1.0F, dt * 6.0F);
      this.eatAmount += (0.0F - this.eatAmount) * Math.min(1.0F, dt * 7.0F);
      this.swing = Math.min(1.0F, this.swing + dt * 3.4F);
      double big = Math.max(1.0, (double)this.size.getCurrent() * 0.45);
      double yawRad = Math.toRadians((double)mc.player.getYaw());
      Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
      Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
      Vec3d goal = player.add(right.multiply(this.slotSide() * 1.25 * big)).subtract(forward.multiply(this.slotBack() * 1.5 * big)).add(0.0, 0.3, 0.0);
      Vec3d d = goal.subtract(this.pos);
      double dist = d.length();
      // чем дальше отстал, тем сильнее тяга — на элитрах догоняет
      float speed = dist > 0.35 * big ? (float)Math.min(60.0, 2.0 + dist * 4.0) : 0.0F;
      this.moveSpeed += (speed - this.moveSpeed) * Math.min(1.0F, dt * 8.0F);
      if (this.moveSpeed > 0.05F && dist > 1.0E-3) {
         double step = Math.min(dist, (double)(this.moveSpeed * dt));
         this.pos = this.pos.add(d.multiply(step / dist));
         double flat = Math.sqrt(d.x * d.x + d.z * d.z);
         if (flat > 0.3) {
            this.yaw = lerpAngle(this.yaw, (float)Math.toDegrees(Math.atan2(-d.x, d.z)), Math.min(1.0F, dt * 10.0F));
         }
      } else {
         this.yaw = lerpAngle(this.yaw, mc.player.getYaw(), Math.min(1.0F, dt * 6.0F));
      }

      this.flyTilt += (MathHelper.clamp(this.moveSpeed / 18.0F, 0.0F, 1.0F) - this.flyTilt) * Math.min(1.0F, dt * 6.0F);
      this.velY = 0.0;
      this.onGround = false;
      this.walkPhase += dt * 3.0F;
      // дымок из сопел
      this.smokeTimer -= dt;
      if (this.smokeTimer <= 0.0F) {
         this.smokeTimer = 0.07F;
         float unit = 0.2F * this.size.getCurrent();
         double rad = Math.toRadians((double)this.yaw);
         mc.particleManager.addParticle(net.minecraft.particle.ParticleTypes.SMOKE, this.pos.x + Math.sin(rad) * (double)unit * 0.8, this.pos.y + (double)unit * 0.3, this.pos.z - Math.cos(rad) * (double)unit * 0.8, 0.0, -0.05, 0.0);
      }

      this.nextBlink -= dt;
      if (this.nextBlink <= 0.0F) {
         this.blink = 0.16F;
         this.nextBlink = 2.5F + (float)Math.random() * 3.5F;
      }

      this.blink = Math.max(0.0F, this.blink - dt);
   }

   /** Цель — та же, что у Aura, либо тот, по кому игрок только что ударил. */
   private void updateTarget(float dt) {
      if (!this.attack.isEnabled()) {
         this.attackTarget = null;
         return;
      }

      Entity found = null;
      if (Aura.INSTANCE.isEnabled() && Aura.INSTANCE.getTarget() != null) {
         found = Aura.INSTANCE.getTarget();
      } else if (mc.player.handSwinging && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity) {
         found = hit.getEntity();
      }

      if (found != null) {
         this.attackTarget = found;
         this.attackHold = 3.0F;
      } else {
         this.attackHold -= dt;
      }

      if (this.attackTarget != null && (this.attackHold <= 0.0F || !this.attackTarget.isAlive() || this.attackTarget.squaredDistanceTo(mc.player) > 400.0)) {
         this.attackTarget = null;
      }
   }

   private static boolean isPetFood(net.minecraft.item.ItemStack stack) {
      // любое мясо и рыба из игры (сырое, жареное, гнилая плоть) — по тегам предметов
      if (!stack.isEmpty() && (stack.isIn(net.minecraft.registry.tag.ItemTags.MEAT) || stack.isIn(net.minecraft.registry.tag.ItemTags.WOLF_FOOD) || stack.isIn(net.minecraft.registry.tag.ItemTags.FISHES))) {
         return true;
      }

      return !stack.isEmpty() && (stack.isOf(net.minecraft.item.Items.APPLE) || stack.isOf(net.minecraft.item.Items.GOLDEN_APPLE) || stack.isOf(net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(net.minecraft.item.Items.GOLDEN_CARROT));
   }

   /** Точка перед мордочкой питомца в мире. */
   private Vec3d mouth() {
      float unit = 0.2F * this.size.getCurrent();
      double rad = Math.toRadians((double)this.yaw);
      return this.pos.add(-Math.sin(rad) * (double)unit * 0.8, (double)unit * 0.7, Math.cos(rad) * (double)unit * 0.8);
   }

   private void spawnCrumbs() {
      Vec3d m = this.mouth();
      java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();

      for (int i = 0; i < 3; ++i) {
         mc.particleManager.addParticle(new net.minecraft.particle.ItemStackParticleEffect(net.minecraft.particle.ParticleTypes.ITEM, this.eatStack), m.x, m.y, m.z, rnd.nextDouble(-0.04, 0.04), rnd.nextDouble(0.03, 0.1), rnd.nextDouble(-0.04, 0.04));
      }
   }

   /** Присел + ПКМ по питомцу — погладить. true, если клик достался питомцу (игре его не отдаём). */
   public boolean handleMouse(int button, int action) {
      if (!Pet.INSTANCE.isEnabled() || this.held || this.pos == null || mc.player == null || mc.currentScreen != null || button != 1 || action != 1 || !mc.player.isSneaking()) {
         return false;
      }

      float unit = 0.2F * this.size.getCurrent();
      Vec3d eye = mc.gameRenderer.getCamera().getPos();
      Vec3d look = Vec3d.fromPolar(mc.gameRenderer.getCamera().getPitch(), mc.gameRenderer.getCamera().getYaw());
      double half = (double)unit * 1.5;
      net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(this.pos.x - half, this.pos.y - 0.05, this.pos.z - half, this.pos.x + half, this.pos.y + (double)unit * 1.9, this.pos.z + half);
      if (box.raycast(eye, eye.add(look.multiply(4.5))).isEmpty() && !box.contains(eye)) {
         return false;
      }

      this.petTime = 1.6F;
      this.idleTime = 0.0F;
      this.yaw = (float)Math.toDegrees(Math.atan2(-(eye.x - this.pos.x), eye.z - this.pos.z));
      if (this.onGround) {
         this.velY = 3.4;
         this.onGround = false;
      }

      // взмах руки только на клиенте, без пакета на сервер
      mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND, false);
      java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();

      for (int i = 0; i < 5; ++i) {
         mc.particleManager.addParticle(net.minecraft.particle.ParticleTypes.HEART, this.pos.x + rnd.nextDouble(-0.3, 0.3), this.pos.y + (double)unit * 1.8 + rnd.nextDouble(0.0, 0.3), this.pos.z + rnd.nextDouble(-0.3, 0.3), 0.0, 0.05, 0.0);
      }

      return true;
   }

   /** Большой питомец перешагивает блоки, которые маленькому пришлось бы запрыгивать. */
   private double stepHeight() {
      return Math.max(1.3, (double)this.size.getCurrent() * 0.35);
   }

   /** Высота опоры под точкой: верх ближайшего блока с коллизией не выше «шага» питомца. */
   private double groundAt(double x, double y, double z) {
      BlockPos.Mutable p = new BlockPos.Mutable();
      int top = MathHelper.floor(y + this.stepHeight() + 0.05);

      for (int by = top; by > top - 16; --by) {
         p.set(MathHelper.floor(x), by, MathHelper.floor(z));
         BlockState state = mc.world.getBlockState(p);
         VoxelShape shape = state.getCollisionShape(mc.world, p);
         if (!shape.isEmpty()) {
            double surface = (double)by + shape.getMax(Direction.Axis.Y);
            if (surface <= y + this.stepHeight() + 0.05) {
               return surface;
            }
         }
      }

      return y - 16.0;
   }

   // ------------------------------------------------------------------ рендер

   private void render(EventRender3D event, double time) {
      Rig rig = this.rig;
      Model3D body = Model3D.bundled(rig.body());
      Model3D eyes = Model3D.bundled(rig.eyes());
      Model3D leg = Model3D.bundled(rig.legs()[0]);
      Model3D blade = Model3D.bundled("pet_sword");
      Model3D jet = Model3D.bundled("pet_jetpack");
      Model3D canopy = Model3D.bundled("pet_parachute");
      if (body == null || eyes == null || leg == null) {
         return;
      }

      Camera cam = mc.gameRenderer.getCamera();
      Vec3d camPos = cam.getPos();
      float sleep = this.sleepAmount;
      float moving = MathHelper.clamp(this.moveSpeed / 3.0F, 0.0F, 1.0F) * (this.onGround ? 1.0F : 0.4F);
      // модель в «единицах Claw'd»: тело шириной 2, земля в нуле
      float unit = 0.2F * this.size.getCurrent();
      MatrixStack ms = event.getMatrix();
      if (this.shadow.isEnabled() && this.heldAmount < 0.5F) {
         this.drawShadow(ms, camPos, unit);
      }

      ms.push();
      ms.translate(this.pos.x - camPos.x, this.pos.y - camPos.y, this.pos.z - camPos.z);
      ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-this.yaw));
      ms.scale(unit, unit, unit);
      // во сне ложится на бок и «дышит», в беге подпрыгивает
      float breath = 1.0F + (float)Math.sin(time * (sleep > 0.5F ? 1.6 : 2.6)) * (0.015F + 0.03F * sleep);
      float bob = (float)Math.abs(Math.sin((double)this.walkPhase)) * 0.09F * moving;
      float sit = this.sitAmount;
      float lower = Math.max(sleep * 0.3F, sit * 0.24F);
      float fly = this.flyAmount;
      float legSwing = (float)Math.sin((double)this.walkPhase) * (38.0F * moving + 10.0F * fly + 16.0F * this.heldAmount);
      // в полёте наклоняется вперёд по ходу, под парашютом слегка раскачивается
      float chuteSway = this.chuteAmount * (float)Math.sin(time * 1.7) * 7.0F;
      ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(chuteSway));
      ms.translate(0.0F, fly * (0.25F + (float)Math.sin(time * 5.0) * 0.05F), 0.0F);
      ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.flyTilt * 38.0F * fly));
      if (canopy != null && this.chuteAmount > 0.03F) {
         ms.push();
         float cs = this.chuteAmount;
         ms.translate(0.0F, rig.chuteY() + 1.56F * (1.0F - cs), 0.0F);
         ms.scale(0.4F + 0.6F * cs, cs, 0.4F + 0.6F * cs);
         canopy.draw(ms, Math.min(1.0F, cs * 1.5F), null, 0.0F);
         ms.pop();
      }

      for (int i = 0; i < 4; ++i) {
         Model3D legModel = Model3D.bundled(rig.legs()[i]);
         if (legModel == null) {
            legModel = leg;
         }

         ms.push();
         ms.translate(rig.legPos()[i][0], rig.legY() - lower, rig.legPos()[i][1]);
         // сидя вытягивает ножки вперёд
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((i % 2 == 0 ? legSwing : -legSwing) * (1.0F - sit) + sleep * (1.0F - sit) * (i < 2 ? -70.0F : 70.0F) - sit * 78.0F));
         ms.scale(1.0F, 1.0F - sleep * 0.45F, 1.0F);
         legModel.draw(ms, 1.0F, null, 0.0F);
         ms.pop();
      }

      ms.push();
      ms.translate(0.0F, bob - lower, 0.0F);
      ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sleep * 7.0F + (float)Math.sin((double)this.walkPhase * 0.5) * 3.0F * moving));
      // лёгкий наклон вперёд на бегу и рывок при ударе
      float lunge = (float)Math.sin((double)this.swing * Math.PI) * 14.0F;
      // жуёт — кивает к еде; гладят — виляет из стороны в сторону
      float chew = this.eatAmount * (float)Math.abs(Math.sin(time * 9.0)) * 7.0F;
      float happy = MathHelper.clamp(this.petTime / 0.3F, 0.0F, 1.0F);
      ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(happy * (float)Math.sin(time * 16.0) * 9.0F));
      ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(moving * 5.0F + lunge + chew));
      ms.push();
      ms.translate(0.0F, 0.36F, 0.0F);
      ms.scale(1.0F, breath, 1.0F);
      ms.translate(0.0F, -0.36F, 0.0F);
      body.draw(ms, 1.0F, null, 0.0F);
      ms.pop();
      float jetShow = this.jetpackAlways.isEnabled() ? 1.0F : fly;
      if (jet != null && jetShow > 0.03F) {
         ms.push();
         ms.translate(0.0F, 1.0F + rig.jetY(), -0.6F + rig.jetZ());
         ms.scale(jetShow, jetShow, jetShow);
         ms.translate(0.0F, -1.0F, 0.6F);
         jet.draw(ms, 1.0F, null, 0.0F);
         ms.pop();
         if (fly > 0.2F) {
            this.drawFlame(ms, -0.36F, 0.37F + rig.jetY(), -0.78F + rig.jetZ(), time, fly);
            this.drawFlame(ms, 0.36F, 0.37F + rig.jetY(), -0.78F + rig.jetZ(), time + 0.37, fly);
         }
      }

      // глаза: во сне и при моргании сжимаются в щёлочки
      float open = Math.max(0.12F, (1.0F - sleep) * (this.blink > 0.0F ? 0.12F : 1.0F));
      // от удовольствия щурится
      open = Math.min(open, 1.0F - 0.72F * Math.max(happy, this.eatAmount * 0.6F));
      ms.push();
      ms.translate(0.0F, rig.eyeY(), 0.0F);
      ms.scale(1.0F, open, 1.0F);
      ms.translate(0.0F, -rig.eyeY(), 0.0F);
      eyes.draw(ms, 1.0F, null, 0.0F);
      ms.pop();
      if (this.eatAmount > 0.03F && !this.eatStack.isEmpty()) {
         // еда перед мордочкой: подносит ко рту в такт жеванию
         float bite = (float)Math.abs(Math.sin(time * 9.0));
         ms.push();
         ms.translate(0.0F, rig.mouthY() + bite * 0.06F, rig.mouthZ() - bite * 0.22F);
         float fs = 1.9F * this.eatAmount;
         ms.scale(fs, fs, fs);
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
         VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         mc.getItemRenderer().renderItem(this.eatStack, net.minecraft.item.ModelTransformationMode.GROUND, 0xF000F0, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, ms, imm, mc.world, 0);
         imm.draw();
         ms.pop();
      }

      if (blade != null && this.sword.isEnabled() && sleep < 0.6F && this.eatAmount < 0.5F) {
         // меч в правой клешне: в покое торчит вверх-вперёд, при ударе описывает дугу сверху вниз
         float s = this.swing;
         float arc = s < 1.0F ? -100.0F + 190.0F * (1.0F - (1.0F - s) * (1.0F - s)) : 35.0F + (float)Math.sin(time * 2.4) * 4.0F;
         ms.push();
         ms.translate(rig.sword()[0], rig.sword()[1], rig.sword()[2]);
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(arc));
         ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-8.0F));
         ms.scale(0.85F, 0.85F, 0.85F);
         blade.draw(ms, 1.0F - sleep / 0.6F, null, 0.0F);
         ms.pop();
      }

      ms.pop();
      ms.pop();
      if (Pet.INSTANCE.names.isEnabled() && this.heldAmount < 0.5F) {
         this.drawName(ms, cam, camPos, unit);
      }

      if (sleep > 0.4F) {
         this.drawZzz(ms, cam, camPos, unit, time, (sleep - 0.4F) / 0.6F);
      }
   }

   /** Пламя из сопла: два вложенных мерцающих конуса, жёлтое ядро и оранжевый хвост. */
   private void drawFlame(MatrixStack ms, float x, float y, float z, double time, float power) {
      float flicker = 0.75F + 0.25F * (float)Math.sin(time * 37.0) + 0.12F * (float)Math.sin(time * 61.0);
      float len = (0.55F + 0.5F * this.flyTilt) * flicker * power;
      Matrix4f mat = ms.peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      int segments = 8;

      for (int layer = 0; layer < 2; ++layer) {
         float r = layer == 0 ? 0.17F : 0.09F;
         float l = layer == 0 ? len : len * 0.6F;
         int base = layer == 0 ? 0xE6FF7A1E : 0xFFFFE890;
         int tip = layer == 0 ? 0x00FF3C00 : 0x00FFB030;

         for (int i = 0; i < segments; ++i) {
            float a0 = (float)i / (float)segments * 6.2832F;
            float a1 = (float)(i + 1) / (float)segments * 6.2832F;
            bb.vertex(mat, x + MathHelper.cos(a0) * r, y, z + MathHelper.sin(a0) * r).color(base);
            bb.vertex(mat, x + MathHelper.cos(a1) * r, y, z + MathHelper.sin(a1) * r).color(base);
            bb.vertex(mat, x, y - l, z).color(tip);
         }
      }

      BufferRenderer.drawWithGlobalProgram(bb.end());
      RenderSystem.defaultBlendFunc();
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   /** Мягкое тёмное пятно под питомцем; бледнеет, когда он в прыжке. */
   private void drawShadow(MatrixStack ms, Vec3d camPos, float unit) {
      double ground = this.groundAt(this.pos.x, this.pos.y, this.pos.z);
      float air = (float)Math.max(0.0, this.pos.y - ground);
      float alpha = 0.42F * MathHelper.clamp(1.0F - air / 2.5F, 0.0F, 1.0F);
      if (alpha <= 0.01F) {
         return;
      }

      float r = unit * 1.35F * (1.0F + air * 0.25F);
      float x = (float)(this.pos.x - camPos.x);
      float y = (float)(ground - camPos.y) + 0.012F;
      float z = (float)(this.pos.z - camPos.z);
      Matrix4f mat = ms.peek().getPositionMatrix();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
      BufferBuilder bb = Tessellator.getInstance().begin(DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
      int center = (int)(alpha * 255.0F) << 24;
      int segments = 14;

      for (int i = 0; i < segments; ++i) {
         float a0 = (float)i / (float)segments * 6.2832F;
         float a1 = (float)(i + 1) / (float)segments * 6.2832F;
         bb.vertex(mat, x, y, z).color(center);
         bb.vertex(mat, x + MathHelper.cos(a0) * r, y, z + MathHelper.sin(a0) * r * 0.8F).color(0);
         bb.vertex(mat, x + MathHelper.cos(a1) * r, y, z + MathHelper.sin(a1) * r * 0.8F).color(0);
      }

      BufferRenderer.drawWithGlobalProgram(bb.end());
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
   }

   /** Имя вида над питомцем, как табличка у мобов. */
   private void drawName(MatrixStack ms, Camera cam, Vec3d camPos, float unit) {
      TextRenderer tr = mc.textRenderer;
      VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
      String name = this.rig.name();
      float s = 0.022F * Math.max(1.0F, this.size.getCurrent() * 0.6F);
      ms.push();
      ms.translate(this.pos.x - camPos.x, this.pos.y - camPos.y + (double)(unit * (this.rig.height() + 0.5F)) + 0.12, this.pos.z - camPos.z);
      ms.multiply(cam.getRotation());
      ms.scale(s, -s, s);
      float w = (float)tr.getWidth(name);
      tr.draw(name, -w / 2.0F, 0.0F, 0xFFFFFFFF, false, ms.peek().getPositionMatrix(), imm, TextRenderer.TextLayerType.NORMAL, 0x66000000, 0xF000F0);
      ms.pop();
      imm.draw();
   }

   /** Три буквы «z» всплывают над спящим питомцем. */
   private void drawZzz(MatrixStack ms, Camera cam, Vec3d camPos, float unit, double time, float alpha) {
      TextRenderer tr = mc.textRenderer;
      VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();

      for (int i = 0; i < 3; ++i) {
         float t = (float)((time * 0.45 + (double)i / 3.0) % 1.0);
         float a = alpha * (float)Math.sin((double)t * Math.PI);
         if (a <= 0.05F) {
            continue;
         }

         float s = unit * (0.05F + t * 0.06F);
         ms.push();
         ms.translate(this.pos.x - camPos.x + (double)(unit * (0.9F + t * 1.2F)), this.pos.y - camPos.y + (double)(unit * (1.9F + t * 2.6F)), this.pos.z - camPos.z);
         ms.multiply(cam.getRotation());
         ms.scale(s, -s, s);
         int color = (int)(a * 255.0F) << 24 | 0xFFFFFF;
         tr.draw(i == 2 ? "Z" : "z", -3.0F, 0.0F, color, false, ms.peek().getPositionMatrix(), imm, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
         ms.pop();
      }

      imm.draw();
   }

   private static float lerpAngle(float from, float to, float t) {
      return from + MathHelper.wrapDegrees(to - from) * t;
   }
}
