package tech.pain.base.neuro;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.base.events.impl.other.EventGameUpdate;
import tech.pain.base.events.impl.player.EventAttack;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.utility.game.other.MessageUtil;
import tech.pain.utility.interfaces.IMinecraft;

public class NeuroManager implements IMinecraft {
   private static NeuroManager instance;
   private final Map<String, NeuroModel> models = new LinkedHashMap<>();
   private NeuroModel current;
   private boolean recording;
   private OtherClientPlayerEntity dummy;
   private Vec3d dummyCenter;
   private boolean spawnedDummy;
   private final List<double[]> recX = new ArrayList<>();
   private final List<double[]> recY = new ArrayList<>();
   private float lastYaw;
   private float lastPitch;
   private float prevAppliedYaw;
   private float prevAppliedPitch;
   private float lastHitX = 0.5F;
   private float lastHitY = 0.5F;
   private float lastHitZ = 0.5F;
   private long trainStart;
   private float frameLastYaw;
   private float frameLastPitch;
   private boolean frameInit;
   private float framePath;
   private float framePeak;
   private int frameCount;
   private float prevSmoothness = 1.0F;
   private float prevPeak = 0.0F;
   private float emaYawSpeed;
   private float emaPitchSpeed;
   private final List<float[]> recStrokes = new ArrayList<>();
   private float emaSignedYaw;
   private float emaSignedPitch;
   private final List<float[]> recTraj = new ArrayList<>();
   private int attackTicksAgo = 999;
   private boolean trajGapPending;

   public static NeuroManager get() {
      if (instance == null) {
         instance = new NeuroManager();
         EventManager.register(instance);
      }

      return instance;
   }

   private NeuroManager() {
      this.loadModels();
   }

   private static float cameraYaw() {
      return mc.gameRenderer.getCamera().getYaw();
   }

   private static float cameraPitch() {
      return mc.gameRenderer.getCamera().getPitch();
   }

   private static void msg(String s) {
      MessageUtil.displayInfo(s.replaceAll("\u00a7.", ""));
   }

   public boolean isRecording() {
      return this.recording;
   }

   public NeuroModel getCurrent() {
      return this.current;
   }

   public Collection<NeuroModel> all() {
      return this.models.values();
   }

   public boolean create(String name) {
      if (this.models.containsKey(name.toLowerCase())) {
         return false;
      } else {
         NeuroModel m = new NeuroModel(name);
         this.models.put(name.toLowerCase(), m);
         this.current = m;
         m.save();
         NeuroModel.saveCurrentName(name);
         return true;
      }
   }

   public boolean select(String name) {
      NeuroModel m = this.models.get(name.toLowerCase());
      if (m == null) {
         m = NeuroModel.load(name);
      }

      if (m == null) {
         return false;
      } else {
         this.models.put(name.toLowerCase(), m);
         this.current = m;
         NeuroModel.saveCurrentName(m.name);
         return true;
      }
   }

   public boolean delete(String name) {
      NeuroModel m = this.models.remove(name.toLowerCase());

      try {
         Files.deleteIfExists(NeuroModel.DIR.resolve(name + ".neuro"));
      } catch (Exception var4) {
      }

      if (m != null && this.current == m) {
         this.current = this.models.values().stream().max(Comparator.comparingInt(model -> model.samples)).orElse(null);
         NeuroModel.saveCurrentName(this.current == null ? null : this.current.name);
      }

      return m != null;
   }

   private void loadModels() {
      try {
         if (!Files.isDirectory(NeuroModel.DIR)) {
            return;
         }

         try (Stream<Path> stream = Files.list(NeuroModel.DIR)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".neuro")).forEach(path -> {
               String file = path.getFileName().toString();
               String name = file.substring(0, file.length() - ".neuro".length());
               NeuroModel model = NeuroModel.load(name);
               if (model != null) {
                  this.models.put(name.toLowerCase(), model);
               }
            });
         }

         String selected = NeuroModel.loadCurrentName();
         if (selected != null) {
            this.current = this.models.get(selected.toLowerCase());
         }

         if (this.current == null) {
            this.current = this.models.values().stream().max(Comparator.comparingInt(model -> model.samples)).orElse(null);
         }
      } catch (Exception var6) {
      }
   }

   public void startTrain() {
      if (this.current == null) {
         msg("§cСначала создай/выбери модель: §f.neuro create <имя>");
      } else if (mc.player != null && mc.world != null) {
         if (this.recording) {
            msg("§7Тренировка уже идет. §f.neuro stop §7чтобы закончить");
         } else {
            this.spawnDummy();

            this.recX.clear();
            this.recY.clear();
            this.lastYaw = cameraYaw();
            this.lastPitch = cameraPitch();
            this.prevAppliedYaw = 0.0F;
            this.prevAppliedPitch = 0.0F;
            this.lastHitX = 0.5F;
            this.lastHitY = 0.5F;
            this.lastHitZ = 0.5F;
            this.frameInit = false;
            this.framePath = 0.0F;
            this.framePeak = 0.0F;
            this.frameCount = 0;
            this.prevSmoothness = 1.0F;
            this.prevPeak = 0.0F;
            this.emaYawSpeed = 0.0F;
            this.emaPitchSpeed = 0.0F;
            this.recStrokes.clear();
            this.emaSignedYaw = 0.0F;
            this.emaSignedPitch = 0.0F;
            this.recTraj.clear();
            this.attackTicksAgo = 999;
            this.trajGapPending = false;
            this.trainStart = System.currentTimeMillis();
            this.recording = true;
            msg(
               "§a[Neuro] §7Тренировка модели §f" + this.current.name + "§7 началась: веди прицелом по хитбоксу. §f.neuro stop§7 для завершения"
            );
         }
      }
   }

   public void stopTrain() {
      if (!this.recording) {
         msg("§7Тренировка не запущена");
      } else {
         this.recording = false;
         this.removeDummy();
         int n = this.recX.size();
         if (this.current != null && n >= 10) {
            this.current.ensureModernShape();
            double[][] X = this.recX.toArray(new double[0][]);
            double[][] Y = this.recY.toArray(new double[0][]);
            double loss = this.current.net.train(X, Y, 650, 0.18);
            this.current.remember(X, Y);
            this.current.addStrokes(this.recStrokes);
            this.current.addTrajectory(this.recTraj);
            this.current.samples += n;
            this.current.loss = loss;
            this.current.save();
            msg(
               String.format(
                  "§a[Neuro] §7Модель §f%s§7 обучена: §f%d§7 образцов, §f%d§7 штрихов, §f%d§7 тиков траектории, ошибка §f%.4f",
                  this.current.name,
                  n,
                  this.current.strokeCount(),
                  this.current.trajCount(),
                  loss
               )
            );
         } else {
            msg("§cМало данных (" + n + "), модель не обучена. Поводи прицелом подольше");
         }
      }
   }

   private void spawnDummy() {
      double rad = Math.toRadians(cameraYaw());
      double dx = -Math.sin(rad) * 3.5;
      double dz = Math.cos(rad) * 3.5;
      this.dummyCenter = new Vec3d(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz);
      this.spawnedDummy = true;
      GameProfile profile = new GameProfile(UUID.fromString("32d0a964-137a-2c03-7cc9-df6700000001"), "NEURO_DUMMY");
      this.dummy = new OtherClientPlayerEntity(mc.world, profile);
      this.dummy.setId(-14500);
      this.dummy.refreshPositionAndAngles(this.dummyCenter.x, this.dummyCenter.y, this.dummyCenter.z, 0.0F, 0.0F);
      this.dummy.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
      this.dummy.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
      this.dummy.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
      this.dummy.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
      this.dummy.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
      this.dummy.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
      this.dummy.setHealth(20.0F);
      this.dummy.setInvisible(true);
      mc.world.addEntity(this.dummy);
   }

   private void removeDummy() {
      if (this.dummy != null && this.spawnedDummy) {
         this.dummy.discard();
      }

      this.dummy = null;
      this.spawnedDummy = false;
   }

   @EventTarget
   public void onAttack(EventAttack event) {
      if (this.recording) {
         this.attackTicksAgo = 0;
      }
   }

   @EventTarget
   public void onDisplay(EventGameUpdate e) {
      if (this.recording && mc.player != null) {
         float yaw = cameraYaw();
         float pitch = cameraPitch();
         if (!this.frameInit) {
            this.frameLastYaw = yaw;
            this.frameLastPitch = pitch;
            this.frameInit = true;
         } else {
            float dYaw = MathHelper.wrapDegrees(yaw - this.frameLastYaw);
            float dPitch = pitch - this.frameLastPitch;
            float step = (float)Math.hypot(dYaw, dPitch);
            this.framePath += step;
            this.framePeak = Math.max(this.framePeak, step);
            this.frameCount++;
            this.frameLastYaw = yaw;
            this.frameLastPitch = pitch;
         }
      }
   }

   @EventTarget
   public void onTick(EventUpdate e) {
      if (this.recording && mc.player != null && mc.world != null && this.dummy != null && this.dummyCenter != null) {
         if (mc.currentScreen == null && mc.isWindowFocused()) {
            long now = System.currentTimeMillis() - this.trainStart;
            double ang = now / 650.0;
            double offX = Math.cos(ang) * 1.8;
            double offZ = Math.sin(ang * 0.8) * 1.4;
            double offY = Math.sin(now / 500.0) * 0.7;
            if (this.spawnedDummy) {
               this.dummy.refreshPositionAndAngles(this.dummyCenter.x + offX, this.dummyCenter.y + offY, this.dummyCenter.z + offZ, 0.0F, 0.0F);
            }

            this.dummy.setHealth(20.0F);
            float curYaw = cameraYaw();
            float curPitch = cameraPitch();
            Vec3d centerAim = this.dummy.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
            float centerYaw = yawTo(centerAim);
            float centerPitch = pitchTo(centerAim);
            Vec3d hitPoint = this.currentHitPoint(this.dummy, curYaw, curPitch);
            float[] hitNorm = normalizeHitPoint(this.dummy.getBoundingBox(), hitPoint);
            this.lastHitX = hitNorm[0];
            this.lastHitY = hitNorm[1];
            this.lastHitZ = hitNorm[2];
            Vec3d pointAim = hitPoint.subtract(mc.player.getEyePos());
            float pointYaw = yawTo(pointAim);
            float pointPitch = pitchTo(pointAim);
            float yawErr = MathHelper.wrapDegrees(centerYaw - this.lastYaw);
            float pitchErr = centerPitch - this.lastPitch;
            float pointYawErr = MathHelper.wrapDegrees(pointYaw - this.lastYaw);
            float pointPitchErr = pointPitch - this.lastPitch;
            float appliedYaw = MathHelper.wrapDegrees(curYaw - this.lastYaw);
            float appliedPitch = curPitch - this.lastPitch;
            float yawShake = MathHelper.wrapDegrees(curYaw - pointYaw);
            float pitchShake = curPitch - pointPitch;
            float distance = (float)mc.player.getEyePos().distanceTo(hitPoint);
            this.emaYawSpeed = this.emaYawSpeed + (Math.abs(appliedYaw) - this.emaYawSpeed) * 0.45F;
            this.emaPitchSpeed = this.emaPitchSpeed + (Math.abs(appliedPitch) - this.emaPitchSpeed) * 0.45F;
            float strokeJitterYaw = appliedYaw - this.emaSignedYaw;
            float strokeJitterPitch = appliedPitch - this.emaSignedPitch;
            this.emaSignedYaw = this.emaSignedYaw + (appliedYaw - this.emaSignedYaw) * 0.35F;
            this.emaSignedPitch = this.emaSignedPitch + (appliedPitch - this.emaSignedPitch) * 0.35F;
            this.recStrokes.add(new float[]{strokeJitterYaw, strokeJitterPitch, (float)Math.hypot(this.emaSignedYaw, this.emaSignedPitch)});
            this.attackTicksAgo = Math.min(this.attackTicksAgo + 1, 999);
            if (this.trajGapPending) {
               this.recTraj.add(new float[]{0.0F, 0.0F, 0.0F, 0.0F, -1.0F});
               this.trajGapPending = false;
            }

            this.recTraj.add(new float[]{yawErr, pitchErr, appliedYaw, appliedPitch, Math.min(this.attackTicksAgo, 60)});
            float displacement = (float)Math.hypot(appliedYaw, appliedPitch);
            float smoothness = this.framePath < 0.02F ? 1.0F : MathHelper.clamp(displacement / this.framePath, 0.0F, 1.0F);
            float peak = this.frameCount <= 0 ? 0.0F : MathHelper.clamp(this.framePeak * this.frameCount / 90.0F, 0.0F, 1.0F);
            this.framePath = 0.0F;
            this.framePeak = 0.0F;
            this.frameCount = 0;
            this.recX
               .add(
                  NeuroModel.features(
                     yawErr,
                     pitchErr,
                     appliedYaw,
                     appliedPitch,
                     this.prevAppliedYaw,
                     this.prevAppliedPitch,
                     this.lastHitX,
                     this.lastHitY,
                     this.lastHitZ,
                     distance,
                     this.prevSmoothness,
                     this.prevPeak
                  )
               );
            this.recY
               .add(
                  new double[]{
                     MathHelper.clamp(this.emaYawSpeed, 0.0F, 180.0F) / 180.0F,
                     MathHelper.clamp(this.emaPitchSpeed, 0.0F, 120.0F) / 120.0F,
                     this.lastHitX,
                     this.lastHitY,
                     this.lastHitZ,
                     NeuroModel.signedToUnit(yawShake, 8.0F),
                     NeuroModel.signedToUnit(pitchShake, 5.0F),
                     NeuroModel.signedToUnit(appliedYaw, 35.0F),
                     NeuroModel.signedToUnit(appliedPitch, 22.0F),
                     NeuroModel.signedToUnit(appliedYaw - this.prevAppliedYaw, 35.0F),
                     NeuroModel.signedToUnit(appliedPitch - this.prevAppliedPitch, 22.0F),
                     smoothness,
                     peak
                  }
               );
            this.prevSmoothness = smoothness;
            this.prevPeak = peak;
            this.prevAppliedYaw = appliedYaw;
            this.prevAppliedPitch = appliedPitch;
            this.lastYaw = curYaw;
            this.lastPitch = curPitch;
         } else {
            this.lastYaw = cameraYaw();
            this.lastPitch = cameraPitch();
            this.prevAppliedYaw = 0.0F;
            this.prevAppliedPitch = 0.0F;
            this.framePath = 0.0F;
            this.framePeak = 0.0F;
            this.frameCount = 0;
            this.frameInit = false;
            this.emaSignedYaw = 0.0F;
            this.emaSignedPitch = 0.0F;
            this.trajGapPending = true;
         }
      }
   }

   private Vec3d currentHitPoint(OtherClientPlayerEntity target, float yaw, float pitch) {
      Vec3d eye = mc.player.getEyePos();
      Vec3d end = eye.add(rotationVector(yaw, pitch).multiply(8.0));
      Optional<Vec3d> hit = target.getBoundingBox().raycast(eye, end);
      if (hit.isPresent()) {
         return hit.get();
      } else {
         Box box = target.getBoundingBox();
         return new Vec3d(
            MathHelper.lerp(this.lastHitX, box.minX, box.maxX),
            MathHelper.lerp(this.lastHitY, box.minY, box.maxY),
            MathHelper.lerp(this.lastHitZ, box.minZ, box.maxZ)
         );
      }
   }

   private static float[] normalizeHitPoint(Box box, Vec3d point) {
      return new float[]{
         normalize((float)((point.x - box.minX) / Math.max(0.001, box.maxX - box.minX))),
         normalize((float)((point.y - box.minY) / Math.max(0.001, box.maxY - box.minY))),
         normalize((float)((point.z - box.minZ) / Math.max(0.001, box.maxZ - box.minZ)))
      };
   }

   private static float normalize(float value) {
      return MathHelper.clamp(value, 0.0F, 1.0F);
   }

   private static Vec3d rotationVector(float yaw, float pitch) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double cosPitch = Math.cos(pitchRad);
      return new Vec3d(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
   }

   private static float usefulMovement(float applied, float error) {
      if (!(Math.abs(applied) < 0.001F) && !(Math.abs(error) < 0.001F)) {
         return Math.signum(applied) == Math.signum(error) ? Math.abs(applied) : 0.0F;
      } else {
         return 0.0F;
      }
   }

   private static float yawTo(Vec3d aim) {
      return (float)Math.toDegrees(Math.atan2(-aim.x, aim.z));
   }

   private static float pitchTo(Vec3d aim) {
      return (float)MathHelper.clamp(-Math.toDegrees(Math.atan2(aim.y, Math.hypot(aim.x, aim.z))), -90.0, 90.0);
   }

   public float[] predict(float yawErr, float pitchErr) {
      return this.current != null && this.current.samples > 0 ? this.current.predict(yawErr, pitchErr) : new float[]{90.0F, 90.0F};
   }

   public NeuroModel.Prediction predictDetailed(float yawErr, float pitchErr) {
      return this.current != null && this.current.samples > 0
         ? this.current.predictDetailed(yawErr, pitchErr)
         : new NeuroModel.Prediction(90.0F, 90.0F, 0.5F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, false);
   }

   public NeuroModel.Prediction predictDetailed(double[] features) {
      return this.current != null && this.current.samples > 0
         ? this.current.predictDetailed(features)
         : new NeuroModel.Prediction(90.0F, 90.0F, 0.5F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, false);
   }

   public float[] nextMove(float yawErr, float pitchErr, float sinceAttack) {
      return this.current == null ? null : this.current.nextMove(yawErr, pitchErr, sinceAttack);
   }

   public float[] nextJitter(float speed) {
      return this.current == null ? new float[]{0.0F, 0.0F} : this.current.nextJitter(speed);
   }

   public boolean hasTrainedModel() {
      return this.current != null && this.current.samples > 0;
   }
}
