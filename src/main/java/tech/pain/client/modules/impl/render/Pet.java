package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
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
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.combat.Aura;
import tech.pain.utility.math.MathUtil;
import tech.pain.utility.render.level.Model3D;

/**
 * Питомцы Claw'd: 3D-модели по частям, живут только на клиенте.
 * Бегают за игроком, прыгают вместе с ним, спят, едят, летают на джетпаке и машут мечом (визуально).
 * Тремя биндами можно заспавнить ещё одного, убрать одного и взять питомца в руки.
 */
@ModuleAnnotation(
   name = "Pet",
   category = Category.RENDER,
   description = "Питомцы Claw'd: бегают за тобой, прыгают, спят, едят; бинды — заспавнить, убрать, взять в руки"
)
public final class Pet extends Module {
   public static final Pet INSTANCE = new Pet();
   private static final int MAX_PETS = 40;
   final NumberSetting size = new NumberSetting("Размер", 1.0F, 0.5F, 25.0F, 0.25F);
   final NumberSetting sleepAfter = new NumberSetting("Засыпает через (сек)", 12.0F, 4.0F, 60.0F, 1.0F);
   final BooleanSetting attack = new BooleanSetting("Атаковать цель мечом", true);
   final BooleanSetting sword = new BooleanSetting("Показывать меч", true);
   final BooleanSetting shadow = new BooleanSetting("Тень", true);
   final BooleanSetting eatWithPlayer = new BooleanSetting("Ест вместе с тобой", true);
   final BooleanSetting jetpack = new BooleanSetting("Джетпак: летит за тобой", true);
   final BooleanSetting jetpackAlways = new BooleanSetting("Джетпак всегда на спине", false);
   final BooleanSetting parachute = new BooleanSetting("Парашют при падении", true);
   final BooleanSetting names = new BooleanSetting("Имя над питомцем", true);
   private final tech.pain.client.modules.api.setting.impl.ModeSetting kind = new tech.pain.client.modules.api.setting.impl.ModeSetting("Кого спавнить", "Claw'd", "Mauser");
   private final tech.pain.client.modules.api.setting.impl.KeySetting spawnKey = new tech.pain.client.modules.api.setting.impl.KeySetting("Заспавнить Claw'd");
   private final tech.pain.client.modules.api.setting.impl.KeySetting removeKey = new tech.pain.client.modules.api.setting.impl.KeySetting("Убрать Claw'd");
   private final tech.pain.client.modules.api.setting.impl.KeySetting holdKey = new tech.pain.client.modules.api.setting.impl.KeySetting("Взять в руки / отпустить");
   private final tech.pain.client.modules.api.setting.impl.KeySetting sitKey = new tech.pain.client.modules.api.setting.impl.KeySetting("Посадить / поднять");
   private final java.util.List<PetClawd> pets = new java.util.ArrayList<>();

   private Pet() {
   }

   @Override
   public void onEnable() {
      if (this.pets.isEmpty()) {
         this.pets.add(new PetClawd(0, PetClawd.rigByName(this.kind.get())));
      }

      super.onEnable();
   }

   @EventTarget
   public void onKey(tech.pain.base.events.impl.input.EventKey event) {
      if (mc.player == null || mc.currentScreen != null) {
         return;
      }

      if (event.isKeyDown(this.spawnKey.getKeyCode())) {
         if (this.pets.size() < MAX_PETS) {
            this.pets.add(new PetClawd(this.freeSlot(), PetClawd.rigByName(this.kind.get())));
            this.say(this.kind.get() + " — питомцев: " + this.pets.size());
         } else {
            this.say("Больше " + MAX_PETS + " нельзя");
         }
      } else if (event.isKeyDown(this.removeKey.getKeyCode())) {
         if (!this.pets.isEmpty()) {
            // сначала убираем того, на кого смотришь, иначе — последнего
            PetClawd target = this.pets.get(this.pets.size() - 1);

            for (PetClawd pet : this.pets) {
               if (pet.aimedAt(6.0)) {
                  target = pet;
                  break;
               }
            }

            this.pets.remove(target);
            this.say("Питомцев: " + this.pets.size());
         }
      } else if (event.isKeyDown(this.sitKey.getKeyCode())) {
         // сажаем того, на кого смотришь; если ни на кого — ближайшего
         PetClawd best = this.pick(12.0);
         if (best != null) {
            best.setSitting(!best.isSitting());
            this.say(best.isSitting() ? "Питомец сел и ждёт" : "Питомец встал");
         } else {
            this.say("Рядом нет питомца");
         }
      } else if (event.isKeyDown(this.holdKey.getKeyCode())) {
         for (PetClawd pet : this.pets) {
            if (pet.isHeld()) {
               pet.setHeld(false);
               return;
            }
         }

         // берём того, на кого смотришь; если ни на кого — ближайшего в пределах 6 блоков
         Vec3d eye = mc.gameRenderer.getCamera().getPos();
         PetClawd best = null;

         for (PetClawd pet : this.pets) {
            if (pet.aimedAt(6.0)) {
               best = pet;
               break;
            }
         }

         if (best == null) {
            double bestDist = 6.0;

            for (PetClawd pet : this.pets) {
               double d = pet.distanceTo(eye);
               if (d < bestDist) {
                  bestDist = d;
                  best = pet;
               }
            }
         }

         if (best != null) {
            best.setHeld(true);
         } else {
            this.say("Рядом нет питомца");
         }
      }
   }

   /** Питомец, на которого смотрит игрок, иначе ближайший в пределах range. */
   private PetClawd pick(double range) {
      for (PetClawd pet : this.pets) {
         if (!pet.isHeld() && pet.aimedAt(range)) {
            return pet;
         }
      }

      Vec3d eye = mc.gameRenderer.getCamera().getPos();
      PetClawd best = null;
      double bestDist = range;

      for (PetClawd pet : this.pets) {
         double d = pet.distanceTo(eye);
         if (!pet.isHeld() && d < bestDist) {
            bestDist = d;
            best = pet;
         }
      }

      return best;
   }

   private int freeSlot() {
      for (int slot = 0; slot < MAX_PETS; ++slot) {
         boolean used = false;

         for (PetClawd pet : this.pets) {
            if (pet.slot() == slot) {
               used = true;
               break;
            }
         }

         if (!used) {
            return slot;
         }
      }

      return this.pets.size();
   }

   private void say(String text) {
      tech.pain.Pain.getInstance().getNotifyManager().addNotification("\uf05a", net.minecraft.text.Text.of(text));
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      for (PetClawd pet : this.pets) {
         pet.frame(event);
      }
   }

   /** Присел + ПКМ по питомцу — погладить. true, если клик достался питомцу. */
   public boolean handleMouse(int button, int action) {
      if (!this.isEnabled()) {
         return false;
      }

      for (PetClawd pet : this.pets) {
         if (pet.handleMouse(button, action)) {
            return true;
         }
      }

      return false;
   }
}
