package tech.pain.client.modules.impl.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Эмоции своего персонажа. Бинд модуля открывает колесо эмоций; выбранная эмоция двигает руки, ноги,
 * голову и корпус модели. Видно тебе (в F5 или с камеры спереди) — другим игрокам без Pain не видно.
 */
@ModuleAnnotation(
   name = "Emotes",
   category = Category.RENDER,
   description = "Колесо эмоций по бинду: помахать, танец, дэб, хлопки, честь, победа, Т-поза, вращение"
)
public final class Emotes extends Module {
   public static final Emotes INSTANCE = new Emotes();
   public static final String[] NAMES = {"Помахать", "Танец", "Дэб", "Хлопки", "Честь", "Победа", "Т-поза", "Вращение"};
   private final NumberSetting duration = new NumberSetting("Длительность (сек)", 6.0F, 2.0F, 30.0F, 0.5F);
   private final BooleanSetting stopOnMove = new BooleanSetting("Останавливать при движении", true);
   private final BooleanSetting frontCamera = new BooleanSetting("Камера спереди на время эмоции", true);
   private String current;
   private long startedAt;
   private long stopAt;
   private Perspective savedPerspective;

   private Emotes() {
   }

   @Override
   public void onEnable() {
      super.onEnable();
      // модуль работает как кнопка: открывает колесо и сразу выключается
      if (mc.player != null && mc.currentScreen == null) {
         mc.setScreen(new tech.pain.client.screens.EmoteWheelScreen());
      }

      this.toggle();
   }

   public void play(String name) {
      this.current = name;
      this.startedAt = System.currentTimeMillis();
      this.stopAt = 0L;
      if (this.frontCamera.isEnabled() && this.savedPerspective == null) {
         this.savedPerspective = mc.options.getPerspective();
         mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
      }
   }

   public String current() {
      return this.current;
   }

   private void stop() {
      if (this.current != null && this.stopAt == 0L) {
         this.stopAt = System.currentTimeMillis();
      }
   }

   /** Насколько эмоция «включена» сейчас: плавный вход и выход за 0.25 с. 0 — эмоции нет. */
   private float blend() {
      if (this.current == null || mc.player == null) {
         return 0.0F;
      }

      long now = System.currentTimeMillis();
      float age = (float)(now - this.startedAt) / 1000.0F;
      if (this.stopAt == 0L) {
         boolean moving = mc.player.input != null && (mc.player.input.movementForward != 0.0F || mc.player.input.movementSideways != 0.0F || mc.options.jumpKey.isPressed());
         if (age > this.duration.getCurrent() || this.stopOnMove.isEnabled() && moving && age > 0.3F) {
            this.stop();
         }
      }

      if (this.stopAt != 0L) {
         float out = (float)(now - this.stopAt) / 250.0F;
         if (out >= 1.0F) {
            this.current = null;
            this.stopAt = 0L;
            if (this.savedPerspective != null) {
               mc.options.setPerspective(this.savedPerspective);
               this.savedPerspective = null;
            }

            return 0.0F;
         }

         return Math.min(1.0F, age / 0.25F) * (1.0F - out);
      }

      return Math.min(1.0F, age / 0.25F);
   }

   private static void set(ModelPart part, float pitch, float yaw, float roll, float k) {
      part.pitch = MathHelper.lerp(k, part.pitch, pitch);
      part.yaw = MathHelper.lerp(k, part.yaw, yaw);
      part.roll = MathHelper.lerp(k, part.roll, roll);
   }

   /** Поза модели своего игрока: вызывается после ванильной позы. */
   public void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
      if (mc.player == null || state.id != mc.player.getId()) {
         return;
      }

      float k = this.blend();
      if (k <= 0.0F) {
         return;
      }

      float t = (float)(System.currentTimeMillis() - this.startedAt) / 1000.0F;
      float s = MathHelper.sin(t * 6.0F);
      switch (this.current) {
         case "Помахать" -> {
            set(model.rightArm, -2.75F, -0.15F, 0.35F + 0.4F * MathHelper.sin(t * 11.0F), k);
            set(model.head, 0.0F, -0.25F, 0.08F * MathHelper.sin(t * 5.0F), k);
         }
         case "Танец" -> {
            set(model.body, 0.0F, 0.25F * s, 0.0F, k);
            set(model.rightArm, -1.2F + 0.6F * s, 0.0F, 0.5F + 0.3F * s, k);
            set(model.leftArm, -1.2F - 0.6F * s, 0.0F, -0.5F + 0.3F * s, k);
            set(model.rightLeg, 0.35F * s, 0.0F, 0.08F, k);
            set(model.leftLeg, -0.35F * s, 0.0F, -0.08F, k);
            set(model.head, 0.15F * MathHelper.sin(t * 12.0F), 0.3F * MathHelper.sin(t * 3.0F), 0.0F, k);
         }
         case "Дэб" -> {
            float in = Math.min(1.0F, t / 0.2F) * k;
            set(model.rightArm, -2.4F, 0.0F, -0.9F, in);
            set(model.leftArm, -1.9F, 0.95F, 0.0F, in);
            set(model.head, 0.65F, -0.65F, 0.0F, in);
         }
         case "Хлопки" -> {
            float clap = 0.35F + 0.25F * MathHelper.sin(t * 16.0F);
            set(model.rightArm, -1.45F, -clap, 0.0F, k);
            set(model.leftArm, -1.45F, clap, 0.0F, k);
         }
         case "Честь" -> {
            set(model.rightArm, -2.35F, -0.65F, 0.25F, k);
            set(model.head, -0.05F, 0.0F, 0.0F, k);
         }
         case "Победа" -> {
            float bounce = 0.15F * MathHelper.sin(t * 9.0F);
            set(model.rightArm, -3.0F + bounce, 0.0F, 0.3F, k);
            set(model.leftArm, -3.0F - bounce, 0.0F, -0.3F, k);
            set(model.head, -0.3F, 0.0F, 0.0F, k);
         }
         case "Т-поза", "Вращение" -> {
            set(model.rightArm, 0.0F, 0.0F, 1.5708F, k);
            set(model.leftArm, 0.0F, 0.0F, -1.5708F, k);
            set(model.rightLeg, 0.0F, 0.0F, 0.0F, k);
            set(model.leftLeg, 0.0F, 0.0F, 0.0F, k);
         }
         default -> {
         }
      }
   }

   /** «Вращение» крутит всю модель вокруг себя. */
   public void applySpin(PlayerEntityRenderState state) {
      if (mc.player == null || state.id != mc.player.getId() || !"Вращение".equals(this.current)) {
         return;
      }

      float k = this.blend();
      float t = (float)(System.currentTimeMillis() - this.startedAt) / 1000.0F;
      state.bodyYaw += t * 540.0F % 360.0F * k;
   }
}
