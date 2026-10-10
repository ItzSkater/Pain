package tech.pain.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.other.EventGameUpdate;
import tech.pain.base.events.impl.other.EventTick;
import tech.pain.base.events.impl.other.EventTickMovement;
import tech.pain.base.events.impl.player.EventMoveInput;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.player.AttackUtil;
import tech.pain.base.request.ScriptManager;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.movement.AutoSprint;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.MovingUtil;
import tech.pain.utility.game.player.PlayerInventoryUtil;
import tech.pain.utility.game.player.RaytracingUtil;
import tech.pain.utility.game.player.rotation.Rotation;
import tech.pain.utility.game.player.rotation.RotationUtil;
import tech.pain.utility.math.MultipointUtils;
import tech.pain.utility.math.Timer;
import tech.pain.utility.predict.PredictUtils;

@ModuleAnnotation(
        name = "Aura",
        category = Category.COMBAT,
        description = "Автоматически бьет цель"
)
public final class Aura extends Module {
   public static final Aura INSTANCE = new Aura();
   private final MultiBooleanSetting targetTypeSetting = MultiBooleanSetting.create("Атаковать", List.of("Игроков", "Мобов", "Животных"));
   public final ModeSetting rotationMode = new ModeSetting("Ротация", new String[0]);
   public final ModeSetting attackSpeed = new ModeSetting("Скорость атаки", "Перезарядка", "Быстрая перезарядка", "CPS", "По урону", "Без задержки");
   private final NumberSetting minCps = new NumberSetting("Мин. CPS", 8.0F, 1.0F, 20.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.attackSpeed.is("CPS"));
   private final NumberSetting maxCps = new NumberSetting("Макс. CPS", 12.0F, 1.0F, 20.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.attackSpeed.is("CPS"));
   private long lastCpsAttack;
   private long cpsDelay;
   private final ModeSetting.Value hvh;
   private final ModeSetting.Value lonyJir;
   private final ModeSetting.Value cake;
   private final ModeSetting.Value legendsGrief;
   private final ModeSetting.Value funtime;
   private final ModeSetting.Value shard;
   private final ModeSetting.Value spookyTime;
   private final ModeSetting.Value none;
   private final ModeSetting.Value neuro;
   private final ModeSetting.Value custom;
   // --- Ротация «Custom»: всё настраивается ---
   private final ModeSetting cPoint = new ModeSetting("Точка прицела", () -> this.customOn(), "Ближайшая", "Центр", "Голова", "Грудь", "Ноги", "Случайная");
   private final NumberSetting cSpread = new NumberSetting("Разброс точки (блоки)", 0.05F, 0.0F, 0.5F, 0.01F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cPointTicks = new NumberSetting("Смена точки (тики)", 12.0F, 2.0F, 60.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cPredict = new NumberSetting("Упреждение цели (тики)", 0.0F, 0.0F, 6.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cYawMin = new NumberSetting("Скорость по горизонтали от (°)", 25.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cYawMax = new NumberSetting("Скорость по горизонтали до (°)", 45.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cPitchMin = new NumberSetting("Скорость по вертикали от (°)", 12.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cPitchMax = new NumberSetting("Скорость по вертикали до (°)", 25.0F, 1.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final ModeSetting cInterp = new ModeSetting("Интерполяция поворота", () -> this.customOn(), "Кубическая", "Синусоидная", "Линейная", "Экспоненциальная");
   private final NumberSetting cExp = new NumberSetting("Доля поворота за тик", 0.45F, 0.05F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.customOn() && this.cInterp.is("Экспоненциальная"));
   private final NumberSetting cAccel = new NumberSetting("Разгон поворота (тики)", 4.0F, 0.0F, 20.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn() && !this.cInterp.is("Экспоненциальная"));
   private final NumberSetting cSlowZone = new NumberSetting("Замедление у цели (°)", 8.0F, 0.0F, 45.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cSlowFactor = new NumberSetting("Сила замедления", 0.4F, 0.1F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.customOn() && this.cSlowZone.getCurrent() > 0.0F);
   private final NumberSetting cFarBoost = new NumberSetting("Ускорение при большом угле (x)", 1.0F, 1.0F, 3.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cJitterYaw = new NumberSetting("Дрожание по горизонтали (°)", 0.6F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cJitterPitch = new NumberSetting("Дрожание по вертикали (°)", 0.4F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cJitterSpeed = new NumberSetting("Скорость дрожания", 1.0F, 0.2F, 4.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cOvershoot = new NumberSetting("Перелёт мимо цели (%)", 0.0F, 0.0F, 40.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cPauseChance = new NumberSetting("Шанс паузы (%)", 0.0F, 0.0F, 30.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cPauseTicks = new NumberSetting("Длина паузы (тики)", 2.0F, 1.0F, 10.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn() && this.cPauseChance.getCurrent() > 0.0F);
   private final NumberSetting cReaction = new NumberSetting("Реакция на новую цель (тики)", 0.0F, 0.0F, 15.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final NumberSetting cFov = new NumberSetting("Наводиться в пределах угла (°)", 360.0F, 30.0F, 360.0F, 5.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final BooleanSetting cOnlyBeforeHit = new BooleanSetting("Наводиться только перед ударом", false, () -> this.customOn());
   private final NumberSetting cHitCharge = new NumberSetting("Начинать при заряде удара от", 0.7F, 0.0F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.customOn() && this.cOnlyBeforeHit.isEnabled());
   private final BooleanSetting cHold = new BooleanSetting("Не двигать, если прицел уже на цели", true, () -> this.customOn());
   private final BooleanSetting cGcd = new BooleanSetting("Шаг как у мыши (GCD)", true, () -> this.customOn());
   private final NumberSetting cReturn = new NumberSetting("Скорость возврата камеры (°)", 40.0F, 5.0F, 360.0F, 5.0F, (java.util.function.Supplier<Boolean>)() -> this.customOn());
   private final BooleanSetting cVisible = new BooleanSetting("Видимый поворот (камера крутится)", false, () -> this.customOn());
   private LivingEntity cTarget;
   private Vec3d cOffset = Vec3d.ZERO;
   private float cYaw;
   private float cPitch;
   private long cLastCall;
   private float cMoveTicks;
   private float cPointAge;
   private float cReactionLeft;
   private float cPauseLeft;
   private boolean cOvershooting;
   private final double[] cPhases = new double[]{Math.random() * 6.28, Math.random() * 6.28, Math.random() * 6.28, Math.random() * 6.28};
   private final tech.pain.utility.game.player.rotation.custom.NeuroRotation neuroRotation = new tech.pain.utility.game.player.rotation.custom.NeuroRotation();
   private final tech.pain.utility.game.player.rotation.custom.FuntimeRotation funtimeRotation = new tech.pain.utility.game.player.rotation.custom.FuntimeRotation();
   private final tech.pain.utility.game.player.rotation.custom.ShardRotation shardRotation = new tech.pain.utility.game.player.rotation.custom.ShardRotation();
   private final tech.pain.utility.game.player.rotation.custom.SpookyTimeRotation spookyTimeRotation = new tech.pain.utility.game.player.rotation.custom.SpookyTimeRotation();
   private final ModeSetting correction;
   private final ModeSetting.Value correctionFocus;
   private final ModeSetting.Value correctionGood;
   private final ModeSetting.Value correctionNone;
   private final NumberSetting distance;
   private final NumberSetting distanceRotation;
   private final BooleanSetting shieldBreak;
   private final BooleanSetting legitSwap;
   private final BooleanSetting raycastCheck;
   private final BooleanSetting predictOnElytra;
   public final NumberSetting predict;
   public final BooleanSetting critsOnlyWithSpace;
   private LivingEntity target;
   private float acceleration;
   private boolean isBack;
   private final Timer hurtTimer;
   private final ScriptManager.ScriptTask script;
   private int lastSlot;
   public float lastYaw;
   public float lastPitch;

   private Aura() {
      this.hvh = new ModeSetting.Value(this.rotationMode, "Vanilla");
      this.lonyJir = (new ModeSetting.Value(this.rotationMode, "LonyGrief")).select();
      this.cake = (new ModeSetting.Value(this.rotationMode, "CakeWorld")).select();
      this.legendsGrief = (new ModeSetting.Value(this.rotationMode, "LegendsGrief")).select();
      this.funtime = new ModeSetting.Value(this.rotationMode, "Funtime");
      this.shard = new ModeSetting.Value(this.rotationMode, "Shard");
      this.spookyTime = new ModeSetting.Value(this.rotationMode, "SpookyTime");
      this.none = new ModeSetting.Value(this.rotationMode, "None");
      this.neuro = new ModeSetting.Value(this.rotationMode, "Neuro");
      // ротация Custom есть только в версии Alpha
      this.custom = tech.pain.Edition.ALPHA ? new ModeSetting.Value(this.rotationMode, "Custom") : null;
      this.correction = new ModeSetting("Коррекция", new String[0]);
      this.correctionFocus = new ModeSetting.Value(this.correction, "Фокус");
      this.correctionGood = (new ModeSetting.Value(this.correction, "Свободная")).select();
      this.correctionNone = new ModeSetting.Value(this.correction, "Нет");
      this.distance = new NumberSetting("Дистанция", 3.0F, 0.5F, 6.0F, 0.1F, "Дистанция атаки");
      this.distanceRotation = new NumberSetting("Дистанция аима", 0.1F, 0.0F, 6.0F, 0.1F);
      this.shieldBreak = new BooleanSetting("Ломать щит", true);
      BooleanSetting var10005 = this.shieldBreak;
      Objects.requireNonNull(var10005);
      this.legitSwap = new BooleanSetting("Легитно ломать", true, var10005::isEnabled);
      this.raycastCheck = new BooleanSetting("Проверка на наведение", false);
      this.predictOnElytra = new BooleanSetting("Перегонять противника", true);
      this.predict = new NumberSetting("Насколько перегонять", 2.0F, 1.0F, 4.0F, 0.1F);
      this.critsOnlyWithSpace = new BooleanSetting("Только с пробелом", true);
      this.target = null;
      this.hurtTimer = new Timer();
      this.script = new ScriptManager.ScriptTask();
      this.lastSlot = -1;
   }

   private void breakShieldAndAttack() {
      boolean wasSwapped = false;
      boolean wasSwappedInventory = false;
      int slotHotbar = PlayerInventoryUtil.find((List)List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE), 0, 8);
      int slotInventory = PlayerInventoryUtil.find((List)List.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE), 8, 35);
      if (slotHotbar != -1 && this.shieldBreak.isEnabled() && this.target.isBlocking()) {
         if (this.legitSwap.isEnabled()) {
            this.lastSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slotHotbar;
         } else {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotHotbar));
         }

         wasSwapped = true;
      }

      if (slotHotbar == -1 && slotInventory != -1 && this.shieldBreak.isEnabled() && this.target.isBlocking()) {
         if (this.legitSwap.isEnabled()) {
            mc.interactionManager.clickSlot(0, slotInventory, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
            this.lastSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = 8;
         } else {
            mc.interactionManager.clickSlot(0, slotInventory, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotHotbar));
         }

         wasSwappedInventory = true;
      }

      mc.interactionManager.attackEntity(mc.player, this.target);
      mc.player.swingHand(Hand.MAIN_HAND);
      if (wasSwapped) {
         if (this.legitSwap.isEnabled()) {
            Pain.getInstance().getScriptManager().addTask(this.script);
            this.script.schedule(EventUpdate.class, (eventUpdate) -> {
               mc.player.getInventory().selectedSlot = this.lastSlot;
               this.lastSlot = -1;
               return true;
            });
         } else {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
         }
      }

      if (wasSwappedInventory) {
         if (this.legitSwap.isEnabled()) {
            Pain.getInstance().getScriptManager().addTask(this.script);
            this.script.schedule(EventUpdate.class, (eventUpdate) -> {
               mc.player.getInventory().selectedSlot = this.lastSlot;
               mc.interactionManager.clickSlot(0, slotInventory, 8, SlotActionType.SWAP, mc.player);
               mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
               this.lastSlot = -1;
               return true;
            });
         } else {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            mc.interactionManager.clickSlot(0, slotInventory, 8, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
         }
      }

   }

   @EventTarget
   public void onTick(EventTick e) {
      if (this.target == null || !this.isValid(this.target)) {
         this.target = this.updateTarget();
      }

      if (this.target != null) {
         // в быстрых режимах паузу между ударами задаёт сам режим, а не общий таймер
         boolean fast = this.attackSpeed.is("CPS") || this.attackSpeed.is("По урону") || this.attackSpeed.is("Без задержки");
         if (this.isCanAttack() && (fast || this.hurtTimer.finished(458L)) && !this.target.isBlocking()) {
            if (mc.player.isSprinting() && !mc.player.isOnGround() && !mc.player.isSwimming()) {
               mc.player.setSprinting(false);
               mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));
               if (!AutoSprint.INSTANCE.isEnabled()) {
                  mc.options.sprintKey.setPressed(false);
               }
            }

            mc.interactionManager.attackEntity(mc.player, this.target);
            mc.player.swingHand(Hand.MAIN_HAND);
            this.neuroRotation.notifyAttack();
            this.hurtTimer.reset();
            this.markAttack();
         }

      }
   }

   @EventTarget
   public void onTickMovement(EventTickMovement e) {
      if (this.target != null) {
         if (this.target.isBlocking() && this.hurtTimer.finished(50L)) {
            if (mc.player.isSprinting() && !mc.player.isOnGround() && !mc.player.isSwimming()) {
               mc.player.setSprinting(false);
               mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));
               if (!AutoSprint.INSTANCE.isEnabled()) {
                  mc.options.sprintKey.setPressed(false);
               }
            }

            this.breakShieldAndAttack();
            this.hurtTimer.reset();
         }

      }
   }

   @EventTarget
   public void eventRotate(EventGameUpdate e) {
      if (this.target != null) {
         Pain.getInstance().getModuleManager().setAcceleration(0.0F);
         Box box = this.target.getBoundingBox();
         Vec3d eyes = mc.player.getEyePos();
         Vec3d point = this.hvh.isSelected() ? this.target.getBoundingBox().getCenter() : (this.legendsGrief.isSelected() ? MultipointUtils.getNearestPoint(this.target, (double)this.distance.getCurrent()) : MultipointUtils.getMultipoint(this.target, (double)this.distance.getCurrent()));
         if (this.target instanceof PlayerEntity && this.predictOnElytra.isEnabled() && mc.player.isGliding() && this.target.isGliding()) {
            point = PredictUtils.predict(this.target, this.target.getPos(), mc.player.getEyePos().distanceTo(this.target.getBoundingBox().getCenter()) > 8.0D ? 8.0F : this.predict.getCurrent());
         }

         Rotation angle = RotationUtil.fromVec3d(point.subtract(eyes));
         float deltaYaw;
         float deltaPitch;
         float smooth;
         float newYaw;
         if (this.hvh.isSelected()) {
            deltaYaw = MathHelper.wrapDegrees(angle.getYaw() - this.lastYaw);
            deltaPitch = angle.getPitch() - this.lastPitch;
            smooth = this.lastYaw + deltaYaw;
            newYaw = this.lastPitch + deltaPitch;
            smooth -= (smooth - this.lastYaw) % Rotation.gcd();
            newYaw -= (newYaw - this.lastPitch) % Rotation.gcd();
            Rotation smoothRot = new Rotation(smooth, newYaw);
            RotationComponent.update(new Rotation(smoothRot.getYaw(), smoothRot.getPitch()), 360.0F, 360.0F, 360.0F, 360.0F, 0, 1, false);
            this.lastYaw = smoothRot.getYaw();
            this.lastPitch = smoothRot.getPitch();
         }

         Rotation smoothRot;
         float deltaYaw2;
         float deltaPitch2;
         float newPitch;
         if (this.lonyJir.isSelected()) {
            if (mc.player.isGliding()) {
               if (!this.isBack) {
                  this.acceleration += 0.005F;
                  if (this.acceleration >= 0.13F) {
                     this.isBack = true;
                  }
               } else {
                  if (this.acceleration >= -0.02F) {
                     this.acceleration -= 0.005F;
                  }

                  if (this.acceleration <= -0.02F) {
                     this.isBack = false;
                  }
               }
            } else if (!RaytracingUtil.rayTrace(mc.player.getRotationVector(), 1488.0D, this.target.getBoundingBox())) {
               this.acceleration += 0.0015F;
            } else if (this.acceleration > 0.0F) {
               this.acceleration -= 0.01F;
            }

            deltaYaw = MathHelper.wrapDegrees(angle.getYaw() - this.lastYaw);
            deltaPitch = angle.getPitch() - this.lastPitch;
            smooth = Math.max(this.acceleration, 0.0F);
            newYaw = this.lastYaw + deltaYaw * Math.min(Math.max(smooth, 0.0F), 1.0F);
            newPitch = this.lastPitch + deltaPitch * Math.min(Math.max(smooth / 2.0F, 0.0F), 1.0F);
            newYaw -= (newYaw - this.lastYaw) % Rotation.gcd();
            newPitch -= (newPitch - this.lastPitch) % Rotation.gcd();
            smoothRot = new Rotation(newYaw, newPitch);
            deltaYaw2 = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() - this.lastYaw);
            deltaPitch2 = mc.gameRenderer.getCamera().getPitch() - this.lastPitch;
            if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
               deltaYaw2 = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() - 180.0F - this.lastYaw);
               deltaPitch2 = -mc.gameRenderer.getCamera().getPitch() - this.lastPitch;
            }

            RotationComponent.update(new Rotation(smoothRot.getYaw(), smoothRot.getPitch()), 360.0F, 360.0F, !(Math.abs(deltaYaw2) > 3.0F) && !(Math.abs(deltaPitch2) > 3.0F) ? 360.0F : 0.0F, !(Math.abs(deltaYaw2) > 3.0F) && !(Math.abs(deltaPitch2) > 3.0F) ? 360.0F : 0.0F, 0, 1, false);
            this.lastYaw = smoothRot.getYaw();
            this.lastPitch = smoothRot.getPitch();
         }

         if (this.legendsGrief.isSelected() || this.cake.isSelected()) {
            if (mc.player.isGliding() && this.target.isGliding()) {
               if (this.isBack) {
                  if (this.acceleration >= -0.02F) {
                     this.acceleration -= Math.abs(MathHelper.wrapDegrees(angle.getYaw() - this.lastYaw)) > 80.0F ? 0.1F : 0.01F;
                  }

                  if (this.acceleration <= -0.02F) {
                     this.isBack = false;
                  }
               } else {
                  this.acceleration += 0.004F;
                  if (this.acceleration >= 0.17F || RaytracingUtil.rayTrace(mc.player.getRotationVector(), 1488.0D, box.offset(mc.player.isGliding() && this.target instanceof PlayerEntity && this.target.isGliding() ? PredictUtils.predict(this.target, this.target.getPos(), this.predict.getCurrent()) : Vec3d.ZERO))) {
                     this.isBack = true;
                  }
               }
            } else if (this.isBack) {
               if (this.acceleration >= -0.01F) {
                  this.acceleration -= Math.abs(MathHelper.wrapDegrees(angle.getYaw() - this.lastYaw)) > 80.0F ? 0.1F : 0.01F;
               }

               if (this.acceleration <= -0.01F) {
                  this.isBack = false;
               }
            } else {
               this.acceleration += 0.004F;
               if (this.acceleration >= 0.18F || RaytracingUtil.rayTrace(mc.player.getRotationVector(), 999.0D, box.offset(mc.player.isGliding() && this.target instanceof PlayerEntity && this.target.isGliding() ? PredictUtils.predict(this.target, this.target.getPos(), this.predict.getCurrent()) : Vec3d.ZERO).expand(-0.5D))) {
                  this.isBack = true;
               }
            }

            deltaYaw = MathHelper.wrapDegrees(angle.getYaw() - this.lastYaw);
            deltaPitch = angle.getPitch() - this.lastPitch;
            smooth = Math.max(this.acceleration, 0.0F);
            newYaw = this.lastYaw + deltaYaw * Math.min(Math.max(smooth, 0.0F), 1.0F);
            newPitch = this.lastPitch + deltaPitch * Math.min(Math.max(smooth / 2.0F, 0.0F), 1.0F);
            newYaw -= (newYaw - this.lastYaw) % Rotation.gcd();
            newPitch -= (newPitch - this.lastPitch) % Rotation.gcd();
            smoothRot = new Rotation(newYaw, newPitch);
            deltaYaw2 = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() - this.lastYaw);
            deltaPitch2 = mc.gameRenderer.getCamera().getPitch() - this.lastPitch;
            if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
               deltaYaw2 = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() - 180.0F - this.lastYaw);
               deltaPitch2 = -mc.gameRenderer.getCamera().getPitch() - this.lastPitch;
            }

            RotationComponent.update(new Rotation(smoothRot.getYaw(), smoothRot.getPitch()), 360.0F, 360.0F, !(Math.abs(deltaYaw2) > 3.0F) && !(Math.abs(deltaPitch2) > 3.0F) ? 360.0F : 0.0F, !(Math.abs(deltaYaw2) > 3.0F) && !(Math.abs(deltaPitch2) > 3.0F) ? 360.0F : 0.0F, 0, 1, false);
            this.lastYaw = smoothRot.getYaw();
            this.lastPitch = smoothRot.getPitch();
         }

         if (this.funtime.isSelected()) {
            this.funtimeRotation.updateRotations(this.target);
         }

         if (this.shard.isSelected()) {
            this.shardRotation.updateRotations(this.target);
         }

         if (this.spookyTime.isSelected()) {
            this.spookyTimeRotation.updateRotations(this.target);
         }

         if (this.neuro.isSelected()) {
            this.neuroRotation.updateRotations(this.target);
         }

         if (this.custom != null && this.custom.isSelected()) {
            this.customRotation();
         }

      }
   }

   private boolean customOn() {
      return this.custom != null && this.custom.isSelected();
   }

   /** Точка на цели, в которую целится ротация Custom (без случайного разброса). */
   private Vec3d customPoint() {
      Box box = this.target.getBoundingBox();
      double h = box.getLengthY();
      Vec3d center = box.getCenter();
      if (this.cPoint.is("Голова")) {
         return new Vec3d(center.x, box.minY + h * 0.9, center.z);
      } else if (this.cPoint.is("Грудь")) {
         return new Vec3d(center.x, box.minY + h * 0.7, center.z);
      } else if (this.cPoint.is("Ноги")) {
         return new Vec3d(center.x, box.minY + h * 0.2, center.z);
      } else if (this.cPoint.is("Центр")) {
         return center;
      } else if (this.cPoint.is("Случайная")) {
         // случайная точка внутри хитбокса задаётся смещением cOffset, здесь — его основа
         return center;
      } else {
         return MultipointUtils.getNearestPoint(this.target, (double)this.distance.getCurrent());
      }
   }

   private float customNoise(int channel) {
      double t = (double)System.currentTimeMillis() / 1000.0 * (double)this.cJitterSpeed.getCurrent();
      return (float)(Math.sin(t * 2.7 + this.cPhases[channel]) * 0.6 + Math.sin(t * 7.3 + this.cPhases[channel + 2]) * 0.4);
   }

   /**
    * Ротация Custom: каждый тик считает, куда и насколько довернуть голову, по настройкам модуля —
    * точка прицела, скорость и кривая разгона, замедление у цели, дрожание, перелёт, паузы, реакция.
    */
   private void customRotation() {
      java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
      // метод вызывается каждый кадр, а настройки заданы «за тик» — считаем, какая доля тика прошла
      long nowMs = System.currentTimeMillis();
      float dt = this.cLastCall == 0L ? 1.0F : MathHelper.clamp((float)(nowMs - this.cLastCall) / 50.0F, 0.0F, 2.0F);
      boolean idle = nowMs - this.cLastCall > 200L;
      this.cLastCall = nowMs;
      // после простоя начинаем от того, куда игрок смотрит сейчас
      if (idle) {
         this.cYaw = mc.player.getYaw();
         this.cPitch = mc.player.getPitch();
         this.cMoveTicks = 0.0F;
         this.cOvershooting = false;
      }

      if (this.target != this.cTarget) {
         this.cTarget = this.target;
         this.cReactionLeft = this.cReaction.getCurrent();
         this.cMoveTicks = 0.0F;
         this.cPointAge = 1000.0F;
         this.cOvershooting = this.cOvershoot.getCurrent() > 0.0F;
      }

      if (this.cOnlyBeforeHit.isEnabled() && mc.player.getAttackCooldownProgress(0.5F) < this.cHitCharge.getCurrent()) {
         this.cLastCall = 0L;
         return;
      }

      // смена точки прицела: разброс вокруг основной точки, а для «Случайной» — по всему хитбоксу
      this.cPointAge += dt;
      if (this.cPointAge >= this.cPointTicks.getCurrent()) {
         this.cPointAge = 0.0F;
         Box box = this.target.getBoundingBox();
         double spread = (double)this.cSpread.getCurrent();
         if (this.cPoint.is("Случайная")) {
            this.cOffset = new Vec3d(rnd.nextDouble(-0.35, 0.35) * box.getLengthX(), rnd.nextDouble(-0.4, 0.4) * box.getLengthY(), rnd.nextDouble(-0.35, 0.35) * box.getLengthZ());
         } else {
            this.cOffset = spread <= 0.0 ? Vec3d.ZERO : new Vec3d(rnd.nextDouble(-spread, spread), rnd.nextDouble(-spread, spread), rnd.nextDouble(-spread, spread));
         }
      }

      Vec3d aim = this.customPoint().add(this.cOffset);
      if (this.cPredict.getCurrent() > 0.0F) {
         Vec3d motion = new Vec3d(this.target.getX() - this.target.prevX, this.target.getY() - this.target.prevY, this.target.getZ() - this.target.prevZ);
         aim = aim.add(motion.multiply((double)this.cPredict.getCurrent()));
      }

      Rotation want = Rotation.lookingAt(aim, mc.player.getEyePos());
      // дрожание руки — небольшое плавающее смещение точки, а не случайные рывки
      float dy = MathHelper.wrapDegrees(want.getYaw() + this.customNoise(0) * this.cJitterYaw.getCurrent() - this.cYaw);
      float dp = MathHelper.clamp(want.getPitch() + this.customNoise(1) * this.cJitterPitch.getCurrent(), -90.0F, 90.0F) - this.cPitch;
      float stepYaw = 0.0F;
      float stepPitch = 0.0F;
      boolean onTarget = this.cHold.isEnabled() && RaytracingUtil.rayTrace(Vec3d.fromPolar(this.cPitch, this.cYaw), (double)this.distance.getCurrent() + 1.0, this.target.getBoundingBox());
      boolean outOfFov = Math.abs(MathHelper.wrapDegrees(want.getYaw() - mc.gameRenderer.getCamera().getYaw())) > this.cFov.getCurrent() / 2.0F;
      if (this.cReactionLeft > 0.0F) {
         this.cReactionLeft -= dt;
      } else if (this.cPauseLeft > 0.0F) {
         this.cPauseLeft -= dt;
      } else if (!onTarget && !outOfFov) {
         // шанс задан на тик, поэтому на кадр он пропорционально меньше
         if (this.cPauseChance.getCurrent() > 0.0F && rnd.nextFloat() * 100.0F < this.cPauseChance.getCurrent() * dt) {
            this.cPauseLeft = this.cPauseTicks.getCurrent();
         }

         // перелёт: первый рывок к цели уходит чуть дальше неё, потом голова возвращается
         if (this.cOvershooting) {
            if (Math.abs(dy) < 3.0F) {
               this.cOvershooting = false;
            } else {
               dy += dy * this.cOvershoot.getCurrent() / 100.0F;
            }
         }

         float yawSpeed = Math.min(this.cYawMin.getCurrent(), this.cYawMax.getCurrent()) + rnd.nextFloat() * Math.abs(this.cYawMax.getCurrent() - this.cYawMin.getCurrent());
         float pitchSpeed = Math.min(this.cPitchMin.getCurrent(), this.cPitchMax.getCurrent()) + rnd.nextFloat() * Math.abs(this.cPitchMax.getCurrent() - this.cPitchMin.getCurrent());
         float angle = (float)Math.hypot((double)dy, (double)dp);
         float factor = 1.0F;
         if (angle > 60.0F) {
            factor *= this.cFarBoost.getCurrent();
         }

         if (this.cSlowZone.getCurrent() > 0.0F && angle < this.cSlowZone.getCurrent()) {
            // рядом с целью рука замедляется
            factor *= this.cSlowFactor.getCurrent() + (1.0F - this.cSlowFactor.getCurrent()) * angle / this.cSlowZone.getCurrent();
         }

         if (this.cInterp.is("Экспоненциальная")) {
            // каждый тик проходим долю оставшегося угла: быстро вначале, всё медленнее к цели
            float part = 1.0F - (float)Math.pow((double)(1.0F - Math.min(0.999F, this.cExp.getCurrent())), (double)dt);
            stepYaw = MathHelper.clamp(dy * part, -yawSpeed * factor * dt, yawSpeed * factor * dt);
            stepPitch = MathHelper.clamp(dp * part, -pitchSpeed * factor * dt, pitchSpeed * factor * dt);
         } else {
            float t = this.cAccel.getCurrent() <= 0.0F ? 1.0F : Math.min(1.0F, (this.cMoveTicks + 1.0F) / this.cAccel.getCurrent());
            float ease;
            if (this.cInterp.is("Кубическая")) {
               ease = t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow((double)(-2.0F * t + 2.0F), 3.0) / 2.0F;
            } else if (this.cInterp.is("Синусоидная")) {
               ease = 0.5F - 0.5F * (float)Math.cos(Math.PI * (double)t);
            } else {
               ease = t;
            }

            // совсем с нуля не стартуем, иначе первый тик пропадает
            ease = Math.max(ease, 0.12F);
            stepYaw = MathHelper.clamp(dy, -yawSpeed * ease * factor * dt, yawSpeed * ease * factor * dt);
            stepPitch = MathHelper.clamp(dp, -pitchSpeed * ease * factor * dt, pitchSpeed * ease * factor * dt);
         }

         this.cMoveTicks += dt;
      } else {
         this.cMoveTicks = 0.0F;
      }

      float newYaw = this.cYaw + stepYaw;
      float newPitch = MathHelper.clamp(this.cPitch + stepPitch, -90.0F, 90.0F);
      if (this.cGcd.isEnabled()) {
         newYaw -= (newYaw - this.cYaw) % Rotation.gcd();
         newPitch -= (newPitch - this.cPitch) % Rotation.gcd();
      }

      this.cYaw = newYaw;
      this.cPitch = newPitch;
      float back = this.cReturn.getCurrent();
      RotationComponent.update(new Rotation(newYaw, newPitch), 360.0F, 360.0F, back, back, 2, 1, this.cVisible.isEnabled());
   }

   /** Готовность удара по выбранной скорости атаки. */
   private boolean attackReady() {
      if (this.attackSpeed.is("Быстрая перезарядка")) {
         return mc.player.getAttackCooldownProgress(0.5F) >= 0.8F;
      }

      if (this.attackSpeed.is("CPS")) {
         long now = System.currentTimeMillis();
         if (now - this.lastCpsAttack < this.cpsDelay) {
            return false;
         }

         return true;
      }

      if (this.attackSpeed.is("Без задержки")) {
         return true;
      }

      if (this.attackSpeed.is("По урону")) {
         return this.target == null || this.target.hurtTime <= 5;
      }

      return mc.player.getAttackCooldownProgress(0.5F) >= 0.9F;
   }

   /** Запоминает удар для режима CPS и выбирает паузу до следующего. */
   private void markAttack() {
      float lo = Math.min(this.minCps.getCurrent(), this.maxCps.getCurrent());
      float hi = Math.max(this.minCps.getCurrent(), this.maxCps.getCurrent());
      float cps = lo + java.util.concurrent.ThreadLocalRandom.current().nextFloat() * (hi - lo);
      this.lastCpsAttack = System.currentTimeMillis();
      this.cpsDelay = (long)(1000.0F / Math.max(1.0F, cps));
   }

   /** Ждать ли падения для крита: в быстрых режимах и с пакетными критами на земле — нет. */
   private boolean waitsForCrit() {
      if (!this.attackSpeed.is("Перезарядка") && !this.attackSpeed.is("Быстрая перезарядка")) {
         return false;
      }

      return !(Criticals.INSTANCE.isEnabled() && mc.player.isOnGround());
   }

   private boolean isCanAttack() {
      if (!this.attackReady()) {
         return false;
      } else if (this.waitsForCrit() && !AttackUtil.canAttack()) {
         return false;
      } else if (this.target instanceof PlayerEntity && this.predictOnElytra.isEnabled() && mc.player.isGliding() && this.target.isGliding() && mc.player.getEyePos().distanceTo(PredictUtils.predict(this.target, this.target.getPos(), this.predict.getCurrent())) > 3.0D && mc.player.getEyePos().distanceTo(this.target.getBoundingBox().getCenter()) > 3.0D) {
         return false;
      } else if ((!mc.player.isGliding() || !this.target.isGliding()) && mc.player.getEyePos().distanceTo(MultipointUtils.getNearestPoint(this.target, (double)this.distance.getCurrent())) > (double)this.distance.getCurrent()) {
         return false;
      } else {
         return this.none.isSelected() || !this.raycastCheck.isEnabled() || RaytracingUtil.rayTrace(mc.player.getRotationVector(), (double)this.distance.getCurrent(), this.target.getBoundingBox()) || mc.targetedEntity != null || mc.player.isGliding() || this.target.isGliding();
      }
   }

   private LivingEntity updateTarget() {
      List<LivingEntity> targets = new ArrayList();
      Iterator var2 = mc.world.getEntities().iterator();

      while(var2.hasNext()) {
         Entity entity = (Entity)var2.next();
         if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            if (this.isValid(living)) {
               targets.add(living);
            }
         }
      }

      if (!targets.isEmpty() && this.isEnabled()) {
         targets.sort(Comparator.comparingDouble((entityx) -> {
            Rotation vec = Rotation.getRotations(entityx.getBoundingBox().getCenter());
            double dy = (double)Math.abs(MathHelper.wrapDegrees(vec.getYaw() - mc.player.getYaw()));
            double dp = (double)Math.abs(MathHelper.wrapDegrees(vec.getPitch() - mc.player.getPitch()));
            return dy + dp;
         }));
         return targets.isEmpty() ? null : (LivingEntity)targets.get(0);
      } else {
         return null;
      }
   }

   public boolean isValid(LivingEntity entity) {
      if (entity == mc.player) {
         return false;
      } else if (entity.isAlive() && !(entity.getHealth() <= 0.0F)) {
         if (mc.player.isAlive() && !(mc.player.getHealth() <= 0.0F)) {
            if (entity instanceof PlayerEntity) {
               PlayerEntity player = (PlayerEntity)entity;
               if (!this.targetTypeSetting.isEnable("Игроков")) {
                  return false;
               }

               if (Pain.getInstance().getFriendManager().isFriend(entity.getName().getString())) {
                  return false;
               }

               if (!tech.pain.client.modules.impl.misc.FakePlayer.isFake(player) && AntiBot.INSTANCE.isBot(player)) {
                  return false;
               }
            }

            if (!(entity instanceof PassiveEntity) && !(entity instanceof FishEntity) || this.targetTypeSetting.isEnable("Животных") && !Pain.getInstance().getServerHandler().isPvp()) {
               if (!(entity instanceof HostileEntity) && !(entity instanceof AmbientEntity) || this.targetTypeSetting.isEnable("Мобов") && !Pain.getInstance().getServerHandler().isPvp()) {
                  if (mc.player.getEyePos().distanceTo(MultipointUtils.getNearestPoint(entity, (double)(this.distance.getCurrent() + this.distanceRotation.getCurrent()))) > (double)(mc.player.isGliding() ? 20.0F : this.distance.getCurrent() + this.distanceRotation.getCurrent())) {
                     return false;
                  } else {
                     return !(entity instanceof ArmorStandEntity);
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @EventTarget
   private void setCorrection(EventMoveInput eventMoveInput) {
      if (!this.correctionNone.isSelected() && this.target != null) {
         if (this.correctionFocus.isSelected()) {
            MovingUtil.fixMovementFocus(eventMoveInput, mc.player.getYaw());
         } else {
            MovingUtil.fixMovementFree(eventMoveInput);
         }

      }
   }

   public LivingEntity getTarget() {
      return this.isEnabled() ? this.target : null;
   }

   public void onEnable() {
      this.target = null;
      super.onEnable();
   }

   public void onDisable() {
      Pain.getInstance().getModuleManager().setAcceleration(0.0F);
      super.onDisable();
   }
}
