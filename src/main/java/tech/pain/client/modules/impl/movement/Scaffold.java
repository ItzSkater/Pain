package tech.pain.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import tech.pain.base.events.impl.player.EventMoveInput;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.component.RotationComponent;
import tech.pain.utility.game.player.rotation.Rotation;

/**
 * Scaffold — строит мост под ногами. Режимы отличаются тем, как поворачивается голова и как ставятся блоки:
 * <ul>
 * <li>GodBridge (по умолчанию) — как строят руками: взгляд назад под 45° к мосту и ~76° вниз, угол почти не
 * меняется, блоки ставятся только когда прицел реально попал в грань, клики с человеческим разбросом;</li>
 * <li>Fast Bridge — взгляд строго назад, на краю блока сам приседает;</li>
 * <li>Smooth — плавно наводится на нужную грань;</li>
 * <li>Expand (HvH) — мгновенные повороты, блоки ставятся на несколько вперёд;</li>
 * <li>Grim Telly — готовый пресет настроек Custom, подобранных пользователем;</li>
 * <li>Custom — всё настраивается: углы, скорость поворота, тряска камеры, стрейфы, CPS.</li>
 * </ul>
 */
@ModuleAnnotation(
   name = "Scaffold",
   category = Category.MOVEMENT,
   description = "Строит мост под ногами: GodBridge, Fast Bridge, Smooth, Expand (HvH) и полностью настраиваемый Custom"
)
public final class Scaffold extends Module {
   public static final Scaffold INSTANCE = new Scaffold();
   private static final double REACH = 4.5;
   private final ModeSetting mode = new ModeSetting("Режим", "GodBridge", "Fast Bridge", "Smooth", "Expand (HvH)", "Grim Telly", "Custom");
   private final NumberSetting expand = new NumberSetting("Длина Expand", 3.0F, 1.0F, 6.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Expand (HvH)") || this.mode.is("Custom"));
   // --- Custom ---
   private final ModeSetting customAim = new ModeSetting("Взгляд", () -> this.mode.is("Custom"), "Диагональ 45°", "Строго назад", "Точно в грань");
   private final BooleanSetting lookBack = new BooleanSetting("Смотреть назад (камера остаётся спереди)", true, () -> this.mode.is("Custom"));
   private final NumberSetting customPitch = new NumberSetting("Наклон вниз", 76.0F, 60.0F, 89.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom") && !this.customAim.is("Точно в грань"));
   private final NumberSetting customSpeed = new NumberSetting("Скорость поворота", 35.0F, 5.0F, 180.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final NumberSetting shakeYaw = new NumberSetting("Тряска камеры: в стороны", 0.6F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final NumberSetting shakePitch = new NumberSetting("Тряска камеры: вверх-вниз", 0.4F, 0.0F, 5.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final NumberSetting shakeSpeed = new NumberSetting("Скорость тряски", 1.0F, 0.2F, 4.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final NumberSetting strafes = new NumberSetting("Стрейфы (сила)", 0.0F, 0.0F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final NumberSetting strafePeriod = new NumberSetting("Стрейфы (период, тики)", 8.0F, 2.0F, 30.0F, 1.0F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom") && this.strafes.getCurrent() > 0.0F);
   private final NumberSetting cpsMin = new NumberSetting("Мин. CPS", 11.0F, 1.0F, 20.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final NumberSetting cpsMax = new NumberSetting("Макс. CPS", 15.0F, 1.0F, 20.0F, 0.5F, (java.util.function.Supplier<Boolean>)() -> this.mode.is("Custom"));
   private final BooleanSetting customEagle = new BooleanSetting("Приседать на краю", false, () -> this.mode.is("Custom"));
   private final BooleanSetting customStrict = new BooleanSetting("Ставить только при попадании прицела", true, () -> this.mode.is("Custom"));
   // --- общие ---
   private final ModeSetting direction = new ModeSetting("Куда строить", "Куда смотрю", "Куда иду");
   private final BooleanSetting edgeBrake = new BooleanSetting("Не падать с края", true);
   private final BooleanSetting autoBlock = new BooleanSetting("Сам берёт блоки из хотбара", true);
   private final BooleanSetting sameY = new BooleanSetting("Держать высоту моста", true);
   private final BooleanSetting swing = new BooleanSetting("Взмах рукой", true);
   private int startSlot = -1;
   private int lockedY = Integer.MIN_VALUE;
   private long nextPlace;
   private int tick;
   private float lastYaw;
   private float lastPitch;
   private boolean haveLast;
   private boolean sneaking;
   private float keptYaw;
   private float keptPitch = 76.0F;
   private final double[] phases = new double[6];
   private float rawForward;
   private float rawStrafe;
   private float snapped = Float.NaN;
   private boolean hasBlocks;
   private int missing;

   private Scaffold() {
   }

   /** Параметры текущего режима. */
   private record Profile(int aim, float pitch, float speed, float shakeYaw, float shakePitch, float shakeSpeed, float cpsMin, float cpsMax, boolean eagle, boolean strict, int expand) {
   }

   private static final int AIM_DIAGONAL = 0;
   private static final int AIM_BACK = 1;
   private static final int AIM_EXACT = 2;

   private Profile profile() {
      if (this.mode.is("Fast Bridge")) {
         return new Profile(AIM_BACK, 79.0F, 30.0F, 0.35F, 0.3F, 1.0F, 8.0F, 12.0F, true, true, 0);
      } else if (this.mode.is("Smooth")) {
         return new Profile(AIM_EXACT, 80.0F, 28.0F, 0.2F, 0.2F, 1.0F, 12.0F, 16.0F, false, true, 0);
      } else if (this.mode.is("Expand (HvH)")) {
         return new Profile(AIM_EXACT, 80.0F, 180.0F, 0.0F, 0.0F, 1.0F, 20.0F, 20.0F, false, false, (int)this.expand.getCurrent());
      } else if (this.mode.is("Grim Telly")) {
         // пресет пользователя: его настройки Custom, сохранённые как готовый режим —
         // точное наведение в грань, поворот 133°/тик, без тряски, 12.5–14 CPS, до 5 блоков вперёд
         return new Profile(AIM_EXACT, 80.0F, 133.0F, 0.0F, 0.0F, 1.3F, 12.5F, 14.0F, false, true, 5);
      } else if (this.mode.is("Custom")) {
         int aim = this.customAim.is("Диагональ 45°") ? AIM_DIAGONAL : (this.customAim.is("Строго назад") ? AIM_BACK : AIM_EXACT);
         float lo = Math.min(this.cpsMin.getCurrent(), this.cpsMax.getCurrent());
         float hi = Math.max(this.cpsMin.getCurrent(), this.cpsMax.getCurrent());
         return new Profile(aim, this.customPitch.getCurrent(), this.customSpeed.getCurrent(), this.shakeYaw.getCurrent(), this.shakePitch.getCurrent(), this.shakeSpeed.getCurrent(), lo, hi,
            this.customEagle.isEnabled(), this.customStrict.isEnabled(), (int)this.expand.getCurrent() - 1);
      } else {
         // GodBridge: диагональный взгляд, лёгкая дрожь руки, клики 12–16 в секунду
         return new Profile(AIM_DIAGONAL, 76.0F, 30.0F, 0.4F, 0.25F, 1.0F, 13.0F, 18.0F, false, true, 0);
      }
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.startSlot = -1;
      this.lockedY = Integer.MIN_VALUE;
      this.haveLast = false;
      this.tick = 0;
      this.nextPlace = 0L;
      this.snapped = Float.NaN;
      ThreadLocalRandom rnd = ThreadLocalRandom.current();

      for (int i = 0; i < this.phases.length; ++i) {
         this.phases[i] = rnd.nextDouble(Math.PI * 2.0);
      }

      if (mc.player != null) {
         this.keptYaw = mc.player.getYaw();
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (mc.player != null && this.startSlot >= 0) {
         mc.player.getInventory().selectedSlot = this.startSlot;
      }

      this.releaseSneak();
   }

   private void releaseSneak() {
      if (this.sneaking) {
         this.sneaking = false;
         mc.options.sneakKey.setPressed(false);
      }
   }

   private static boolean usable(ItemStack stack) {
      return stack.getItem() instanceof BlockItem item && !(item.getBlock() instanceof FallingBlock) && item.getBlock().getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
   }

   /** Рука с блоком; при необходимости переключает слот хотбара. null — блоков нет. */
   private Hand pickBlock() {
      if (usable(mc.player.getMainHandStack())) {
         return Hand.MAIN_HAND;
      }

      if (usable(mc.player.getOffHandStack())) {
         return Hand.OFF_HAND;
      }

      if (!this.autoBlock.isEnabled()) {
         return null;
      }

      for (int slot = 0; slot < 9; ++slot) {
         if (usable(mc.player.getInventory().getStack(slot))) {
            if (this.startSlot < 0) {
               this.startSlot = mc.player.getInventory().selectedSlot;
            }

            mc.player.getInventory().selectedSlot = slot;
            return Hand.MAIN_HAND;
         }
      }

      return null;
   }

   private static boolean replaceable(BlockPos pos) {
      return mc.world.getBlockState(pos).isReplaceable();
   }

   private static boolean solid(BlockPos pos) {
      BlockState state = mc.world.getBlockState(pos);
      return !state.isReplaceable() && !state.getCollisionShape(mc.world, pos).isEmpty();
   }

   /** Куда смотрит игрок на самом деле: свободная камера, а не повёрнутая Scaffold'ом голова. */
   private float viewYaw() {
      return this.cameraFollows() ? mc.player.getYaw() : tech.pain.utility.component.FreeLookComponent.getFreeYaw();
   }

   private boolean cameraFollows() {
      return this.mode.is("Custom") && !this.lookBack.isEnabled();
   }

   /**
    * Куда идём: направление по нажатым клавишам относительно камеры, градусы. NaN — стоим.
    * Скорость персонажа сюда не подмешивается: от неё направление скачет, и голова начинает крутиться.
    */
   private float moveYaw() {
      if (Math.abs(this.rawForward) < 0.01F && Math.abs(this.rawStrafe) < 0.01F) {
         return Float.NaN;
      }

      return (float)Math.toDegrees(RotationComponent.direction(this.viewYaw(), this.rawForward, this.rawStrafe));
   }

   /** Привязка к шагу 45° с запасом: пока взгляд не ушёл заметно, направление моста не меняется. */
   private float snap(float yaw) {
      if (!Float.isNaN(this.snapped) && Math.abs(MathHelper.wrapDegrees(yaw - this.snapped)) < 32.0F) {
         return this.snapped;
      }

      this.snapped = (float)Math.round(yaw / 45.0F) * 45.0F;
      return this.snapped;
   }

   /** Грань соседнего блока, щелчок по которой поставит блок в pos. */
   private BlockHitResult faceFor(BlockPos pos, Vec3d eye) {
      BlockHitResult best = null;
      double bestDist = REACH * REACH;

      for (Direction dir : Direction.values()) {
         BlockPos neighbor = pos.offset(dir);
         if (!solid(neighbor)) {
            continue;
         }

         Direction face = dir.getOpposite();
         // точка чуть в стороне от центра грани — человек не попадает ровно в середину
         Vec3d hit = Vec3d.ofCenter(neighbor).add(Vec3d.of(face.getVector()).multiply(0.5));
         double dist = eye.squaredDistanceTo(hit);
         if (dist < bestDist) {
            bestDist = dist;
            best = new BlockHitResult(hit, face, neighbor, false);
         }
      }

      return best;
   }

   private BlockHitResult raycast(float yaw, float pitch) {
      return this.raycast(mc.player.getEyePos(), yaw, pitch);
   }

   private BlockHitResult raycast(Vec3d eye, float yaw, float pitch) {
      Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(REACH));
      BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
      return hit.getType() == HitResult.Type.BLOCK ? hit : null;
   }

   /** Плавный «шум руки»: сумма нескольких медленных волн, значения примерно в −1..1. */
   private float noise(int channel, float speed) {
      double t = (double)System.currentTimeMillis() / 1000.0 * (double)speed;
      return (float)(Math.sin(t * 2.3 + this.phases[channel]) * 0.5 + Math.sin(t * 5.1 + this.phases[channel + 2]) * 0.3 + Math.sin(t * 11.7 + this.phases[channel + 4]) * 0.2);
   }

   @EventTarget
   public void onMoveInput(EventMoveInput event) {
      if (mc.player == null) {
         return;
      }

      float forward = event.getForward();
      float strafe = event.getStrafe();
      this.rawForward = forward;
      this.rawStrafe = strafe;
      if (this.mode.is("Custom") && this.strafes.getCurrent() > 0.0F && Math.abs(forward) > 0.01F && Math.abs(strafe) < 0.01F) {
         // короткие шаги влево-вправо, как это делают при постройке моста руками
         int period = Math.max(2, (int)this.strafePeriod.getCurrent());
         strafe = (this.tick / period % 2 == 0 ? 1.0F : -1.0F) * this.strafes.getCurrent();
      }

      if (this.cameraFollows() || Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) {
         event.setStrafe(strafe);
         return;
      }

      // голова повёрнута к мосту, а идти надо туда, куда смотрит камера: подбираем клавиши,
      // которые относительно головы дают то же направление, что нажатые — относительно камеры
      double want = Math.toDegrees(RotationComponent.direction(this.viewYaw(), forward, Math.signum(strafe)));
      if (this.edgeBrake.isEnabled() && this.hasBlocks && mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
         // середина игрока уже вышла за край, а блок впереди ещё не поставлен — на тик останавливаемся,
         // чтобы не упасть; как только блок встанет, идём дальше
         double rad = Math.toRadians(want);
         double dx = -Math.sin(rad);
         double dz = Math.cos(rad);
         Vec3d at = mc.player.getPos();
         boolean behind = solid(BlockPos.ofFloored(at.x - dx * 0.12, at.y - 0.5, at.z - dz * 0.12));
         boolean front = solid(BlockPos.ofFloored(at.x + dx * 0.3, at.y - 0.5, at.z + dz * 0.3));
         if (!behind && !front) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x * 0.2, v.y, v.z * 0.2);
            return;
         }
      }

      float bestForward = forward;
      float bestStrafe = strafe;
      float bestDiff = Float.MAX_VALUE;

      for (int f = -1; f <= 1; ++f) {
         for (int st = -1; st <= 1; ++st) {
            if (f == 0 && st == 0) {
               continue;
            }

            double got = Math.toDegrees(RotationComponent.direction(mc.player.getYaw(), (float)f, (float)st));
            float diff = Math.abs(MathHelper.wrapDegrees((float)(want - got)));
            if (diff < bestDiff) {
               bestDiff = diff;
               bestForward = (float)f;
               bestStrafe = (float)st;
            }
         }
      }

      event.setForward(bestForward);
      event.setStrafe(bestStrafe);
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null) {
         return;
      }

      ++this.tick;
      Profile p = this.profile();
      Hand hand = this.pickBlock();
      this.hasBlocks = hand != null;
      if (hand == null) {
         this.releaseSneak();
         return;
      }

      // высота моста: фиксируется, пока не прыгаешь (так блоки не лезут вверх при подпрыгивании)
      // блок, на который можно встать: строго ниже ступней. Во время прыжка ноги бывают внутри следующего блока —
      // ставить туда нельзя: сервер такой блок отклоняет (он пересекается с игроком), и начинаются откаты
      int feetY = MathHelper.floor(mc.player.getY() + 1.0E-4) - 1;
      if (!this.sameY.isEnabled() || mc.options.jumpKey.isPressed() || this.lockedY == Integer.MIN_VALUE || mc.player.isOnGround() && feetY < this.lockedY) {
         this.lockedY = mc.player.isOnGround() || this.lockedY == Integer.MIN_VALUE || mc.options.jumpKey.isPressed() ? feetY : this.lockedY;
      }

      int y = this.sameY.isEnabled() ? Math.min(this.lockedY, feetY) : feetY;
      float moveYaw = this.moveYaw();
      boolean moving = !Float.isNaN(moveYaw);
      // когда камера поворачивается вместе с головой, «по взгляду» строить нельзя: взгляд уже повёрнут
      // назад, и мост каждый тик разворачивался бы — в этом случае направление берётся по движению
      if (this.direction.is("Куда смотрю") && !(this.mode.is("Custom") && !this.lookBack.isEnabled())) {
         // мост идёт туда, куда смотрит камера (она остаётся свободной, пока Scaffold крутит голову)
         moveYaw = this.viewYaw();
      }

      if (this.cameraFollows()) {
         // камера идёт за головой: направление задаёшь ты сам поворотом, мост строится от тебя назад
         moveYaw = mc.player.getYaw() + 180.0F;
      }

      boolean aimed = !Float.isNaN(moveYaw);
      Direction ahead = aimed ? Direction.fromHorizontalDegrees((double)moveYaw) : mc.player.getHorizontalFacing();
      Vec3d pos = mc.player.getPos();
      Vec3d vel = mc.player.getVelocity();
      // где нужен блок: под ногами, под ногами через тик и (для Expand) дальше по ходу
      List<BlockPos> wanted = new ArrayList<>();
      BlockPos below = BlockPos.ofFloored(pos.x, (double)y, pos.z);
      wanted.add(below);
      BlockPos soon = BlockPos.ofFloored(pos.x + vel.x * 2.0, (double)y, pos.z + vel.z * 2.0);
      if (!soon.equals(below)) {
         wanted.add(soon);
      }

      // стоя на месте вперёд не строим, кроме Expand — он тянет мост по взгляду и без движения
      if (moving || aimed && p.expand > 0) {
         for (int i = 1; i <= Math.max(1, p.expand); ++i) {
            wanted.add(below.offset(ahead, i));
         }
      }

      Vec3d eye = mc.player.getEyePos();
      BlockPos target = null;
      BlockHitResult face = null;

      for (BlockPos w : wanted) {
         if (!replaceable(w)) {
            continue;
         }

         BlockHitResult f = this.faceFor(w, eye);
         if (f != null) {
            target = w;
            face = f;
            break;
         }
      }

      // приседание на краю: под ногами через пару тиков пусто
      if (p.eagle) {
         boolean edge = mc.player.isOnGround() && replaceable(BlockPos.ofFloored(pos.x + vel.x * 3.0, (double)y, pos.z + vel.z * 3.0));
         if (edge != this.sneaking) {
            this.sneaking = edge;
            mc.options.sneakKey.setPressed(edge);
         }
      } else {
         this.releaseSneak();
      }

      // куда смотреть
      float yaw;
      float pitch;
      float backYaw = aimed ? this.snap(moveYaw + 180.0F) : this.keptYaw;
      if (p.aim == AIM_EXACT) {
         if (face != null) {
            Rotation r = Rotation.lookingAt(face.getPos(), eye);
            yaw = r.getYaw();
            pitch = r.getPitch();
         } else {
            yaw = this.keptYaw;
            pitch = this.keptPitch;
         }
      } else {
         boolean straight = Math.abs(MathHelper.wrapDegrees(backYaw) % 90.0F) < 1.0F;
         // при постройке прямо голова повёрнута на 45° к мосту; по диагонали — ровно назад
         yaw = p.aim == AIM_DIAGONAL && straight && aimed ? backYaw + 45.0F : backYaw;
         if (this.cameraFollows()) {
            // поворот в стороны остаётся за игроком, модуль подбирает только наклон
            yaw = mc.player.getYaw();
         }

         pitch = p.pitch;
         if (target != null) {
            // наклон подбираем так, чтобы прицел при этом повороте попал в нужную грань. Грань моста видна только
            // когда глаза уже за краем блока, поэтому пробуем и точки чуть впереди — голова наводится заранее
            float found = Float.NaN;
            double rad = Math.toRadians(aimed ? (double)moveYaw : 0.0);
            Vec3d step = aimed ? new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad)) : Vec3d.ZERO;

            for (int lead = 0; lead <= 4 && Float.isNaN(found); ++lead) {
               Vec3d from = eye.add(step.multiply((double)lead * 0.1));

               for (float d = 0.0F; d <= 14.0F && Float.isNaN(found); d += 0.25F) {
                  for (int sign = -1; sign <= 1 && Float.isNaN(found); sign += 2) {
                     float tryPitch = MathHelper.clamp(p.pitch + d * (float)sign, 45.0F, 89.5F);
                     BlockHitResult hit = this.raycast(from, yaw, tryPitch);
                     if (hit != null && wanted.contains(hit.getBlockPos().offset(hit.getSide())) && replaceable(hit.getBlockPos().offset(hit.getSide()))) {
                        found = tryPitch;
                     }
                  }
               }

               if (!aimed) {
                  break;
               }
            }

            if (!Float.isNaN(found)) {
               pitch = found;
               this.missing = 0;
            } else if (++this.missing > 12 && face != null) {
               // долго не удаётся попасть с этим поворотом (стоим боком, начали с неудобного места) — наводимся точно
               Rotation r = Rotation.lookingAt(face.getPos(), eye);
               yaw = r.getYaw();
               pitch = r.getPitch();
            } else {
               pitch = this.keptPitch;
            }
         } else {
            this.missing = 0;
         }
      }

      this.keptYaw = yaw;
      this.keptPitch = pitch;
      // дрожь руки
      yaw += this.noise(0, p.shakeSpeed) * p.shakeYaw;
      pitch = MathHelper.clamp(pitch + this.noise(1, p.shakeSpeed * 1.3F) * p.shakePitch, -89.5F, 89.5F);
      // скорость поворота немного гуляет — рука не двигается с идеально ровной скоростью
      float speed = p.speed >= 180.0F ? 180.0F : p.speed * (0.8F + 0.4F * ThreadLocalRandom.current().nextFloat());
      // «Смотреть назад»: голова персонажа повёрнута к мосту, а камера свободна и показывает то, что впереди.
      // Без этой опции камера поворачивается вместе с головой — видно ровно то, куда она смотрит.
      boolean cameraFollows = this.cameraFollows();
      if (!cameraFollows) {
         // свободную камеру включаем сами: если она не активна, взгляд совпадает с головой и мост начинает вертеться
         tech.pain.utility.component.FreeLookComponent.setActive(true);
      }

      RotationComponent.update(new Rotation(yaw, pitch), speed, speed, 30.0F, 30.0F, 6, 3, cameraFollows);
      if (target == null || face == null) {
         this.rememberRotation();
         return;
      }

      long now = System.currentTimeMillis();
      if (now < this.nextPlace) {
         this.rememberRotation();
         return;
      }

      int perTick = p.strict ? 1 : 3;

      for (int n = 0; n < perTick; ++n) {
         BlockHitResult click;
         if (p.strict) {
            // ставим только туда, куда прицел действительно смотрит: сейчас или тик назад
            BlockHitResult hit = this.goodHit(this.raycast(mc.player.getYaw(), mc.player.getPitch()), wanted);
            if (hit == null && this.haveLast) {
               hit = this.goodHit(this.raycast(this.lastYaw, this.lastPitch), wanted);
            }

            if (hit == null) {
               break;
            }

            click = hit;
         } else {
            click = face;
         }

         if (mc.interactionManager.interactBlock(mc.player, hand, click).isAccepted()) {
            if (this.swing.isEnabled()) {
               mc.player.swingHand(hand);
            }

            float cps = p.cpsMin + ThreadLocalRandom.current().nextFloat() * (p.cpsMax - p.cpsMin);
            this.nextPlace = now + (long)(1000.0F / Math.max(1.0F, cps));
         }

         if (p.strict) {
            break;
         }

         // Expand: сразу ищем следующий пустой блок дальше по ходу
         target = null;

         for (BlockPos w : wanted) {
            if (replaceable(w)) {
               BlockHitResult f = this.faceFor(w, eye);
               if (f != null) {
                  target = w;
                  face = f;
                  break;
               }
            }
         }

         if (target == null) {
            break;
         }

         this.nextPlace = 0L;
      }

      this.rememberRotation();
   }

   /** Попадание годится, если щелчок по этой грани поставит блок в одно из нужных мест. */
   private BlockHitResult goodHit(BlockHitResult hit, List<BlockPos> wanted) {
      if (hit == null) {
         return null;
      }

      BlockPos placed = hit.getBlockPos().offset(hit.getSide());
      return wanted.contains(placed) && replaceable(placed) ? hit : null;
   }

   private void rememberRotation() {
      this.lastYaw = mc.player.getYaw();
      this.lastPitch = mc.player.getPitch();
      this.haveLast = true;
   }
}
