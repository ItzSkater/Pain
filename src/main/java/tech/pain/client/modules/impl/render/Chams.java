package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import tech.pain.Pain;
import tech.pain.base.events.impl.entity.EventEntityColor;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;

/**
 * Chams: модель существа заливается цветом и (по желанию) видна сквозь стены.
 * Заливка — ровный цвет вместо скина; Подкраска — скин остаётся, но окрашен; Перелив — цвета темы.
 * Сквозь стены модель рисуется со сдвигом глубины к камере, поэтому блоки её не перекрывают.
 */
@ModuleAnnotation(
   name = "Chams",
   category = Category.RENDER,
   description = "Заливает модели цветом и показывает их сквозь стены"
)
public final class Chams extends Module {
   public static final Chams INSTANCE = new Chams();
   private static final Identifier WHITE = Identifier.of("pain", "chams_white");
   private static boolean whiteReady;
   private static boolean offsetActive;
   private final ModeSetting mode = new ModeSetting("Режим", "Заливка", "Подкраска", "Перелив");
   private final ColorSetting color = new ColorSetting("Цвет", new ColorRGBA(255, 40, 40, 200), () -> !this.mode.is("Перелив"));
   private final BooleanSetting glow = new BooleanSetting("Светится (без теней)", true, () -> !this.mode.is("Подкраска"));
   private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);
   private final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", true);
   private final BooleanSetting skipSelf = new BooleanSetting("Не трогать себя", true);

   private Chams() {
   }

   /** Подходит ли это существо под настройки модуля. */
   public boolean applies(LivingEntityRenderState state) {
      if (!this.isEnabled() || state == null || mc.player == null) {
         return false;
      }

      if (state instanceof PlayerEntityRenderState player) {
         return !this.skipSelf.isEnabled() || player.id != mc.player.getId();
      }

      return !this.onlyPlayers.isEnabled();
   }

   private int argb() {
      if (this.mode.is("Перелив")) {
         return Pain.getInstance().getThemeManager().getClientColor((int)(System.currentTimeMillis() / 20L % 360L)).withAlpha(210).getRGB();
      }

      return this.color.getColor().getRGB();
   }

   /** Слой для ровной заливки (белая текстура вместо скина); null — рисовать обычным слоем. */
   public RenderLayer layerFor(LivingEntityRenderState state) {
      if (!this.applies(state) || this.mode.is("Подкраска")) {
         return null;
      }

      if (!whiteReady) {
         NativeImage image = new NativeImage(1, 1, false);
         image.setColorArgb(0, 0, -1);
         mc.getTextureManager().registerTexture(WHITE, new NativeImageBackedTexture(image));
         whiteReady = true;
      }

      return this.glow.isEnabled() ? RenderLayer.getEntityTranslucentEmissive(WHITE) : RenderLayer.getEntityTranslucent(WHITE);
   }

   @EventTarget
   public void onEntityColor(EventEntityColor e) {
      if (this.applies(e.getState())) {
         e.setColor(this.argb());
      }
   }

   /** Перед отрисовкой существа: сдвигаем глубину к камере, чтобы стены не перекрывали модель. */
   public void beginThroughWalls(LivingEntityRenderState state, VertexConsumerProvider consumers) {
      if (!this.throughWalls.isEnabled() || !this.applies(state) || !(consumers instanceof VertexConsumerProvider.Immediate immediate)) {
         return;
      }

      // всё накопленное до этого рисуем как обычно, без сдвига
      immediate.draw();
      RenderSystem.enablePolygonOffset();
      RenderSystem.polygonOffset(1.0F, -1100000.0F);
      offsetActive = true;
   }

   public void endThroughWalls(VertexConsumerProvider consumers) {
      if (!offsetActive) {
         return;
      }

      offsetActive = false;
      if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
         immediate.draw();
      }

      RenderSystem.polygonOffset(0.0F, 0.0F);
      RenderSystem.disablePolygonOffset();
   }
}
