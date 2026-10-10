package tech.pain.client.modules.impl.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import tech.pain.Pain;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.display.base.color.ColorRGBA;

/**
 * Fire Chams: предмет в руке (от первого лица) горит. Поверх модели предмета второй раз рисуется
 * бегущая вверх текстура пламени: либо по всей поверхности, либо только каймой по краям
 * (чуть увеличенная копия, от которой видна только выступающая за предмет часть).
 */
@ModuleAnnotation(
   name = "FireChams",
   category = Category.RENDER,
   description = "Предмет в руке горит: весь целиком или только по краям, с выбором цвета пламени"
)
public final class FireChams extends Module {
   public static final FireChams INSTANCE = new FireChams();
   private static final Identifier TEXTURE = Identifier.of("pain", "fire_chams");
   private static final int SIZE = 64;
   /** Предмет, который сейчас рисуется в руке от первого лица; null — рисуется что-то другое. */
   private static ItemStack current;
   private static boolean drawing;
   private static boolean mirrored;
   private static RenderLayer wholeLayer;
   private static RenderLayer edgeLayer;
   private static int textureColor = Integer.MIN_VALUE;
   private final ModeSetting area = new ModeSetting("Горит", "Весь предмет", "Грани", "Всё вместе");
   private final ModeSetting items = new ModeSetting("Предметы", "Любые", "Только оружие и инструменты");
   private final ModeSetting flame = new ModeSetting("Пламя", "Огонь", "Синее", "Тема", "Свой цвет");
   private final ColorSetting custom = new ColorSetting("Цвет пламени", new ColorRGBA(160, 60, 255), () -> this.flame.is("Свой цвет"));
   private final NumberSetting speed = new NumberSetting("Скорость", 1.0F, 0.2F, 4.0F, 0.1F);
   private final NumberSetting scale = new NumberSetting("Размер языков", 1.0F, 0.3F, 3.0F, 0.1F);
   private final NumberSetting edge = new NumberSetting("Толщина граней", 1.0F, 0.4F, 3.0F, 0.1F, (java.util.function.Supplier<Boolean>)() -> !this.area.is("Весь предмет"));
   private final BooleanSetting offHand = new BooleanSetting("И во второй руке", true);

   private FireChams() {
   }

   /** Вызывается перед отрисовкой предмета в руке от первого лица и после неё (stack = null). */
   public static void setCurrent(ItemStack stack, boolean mainHand) {
      FireChams module = INSTANCE;
      if (stack == null || stack.isEmpty() || !module.isEnabled() || !mainHand && !module.offHand.isEnabled()) {
         current = null;
         return;
      }

      if (module.items.is("Только оружие и инструменты") && !(stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.PICKAXES)
         || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES))) {
         current = null;
         return;
      }

      current = stack;
   }

   private int flameColor() {
      if (this.flame.is("Синее")) {
         return 0x2A7BFF;
      }

      if (this.flame.is("Тема")) {
         return Pain.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB() & 0xFFFFFF;
      }

      if (this.flame.is("Свой цвет")) {
         return this.custom.getColor().getRGB() & 0xFFFFFF;
      }

      return 0xFF6A12;
   }

   /** Текстура пламени: бесшовный шум с вертикальными языками; тёмное → цвет пламени → почти белое. */
   private void ensureTexture() {
      int rgb = this.flameColor();
      if (rgb == textureColor) {
         return;
      }

      textureColor = rgb;
      float cr = (float)(rgb >> 16 & 255) / 255.0F;
      float cg = (float)(rgb >> 8 & 255) / 255.0F;
      float cb = (float)(rgb & 255) / 255.0F;
      NativeImage image = new NativeImage(SIZE, SIZE, false);

      for (int y = 0; y < SIZE; ++y) {
         for (int x = 0; x < SIZE; ++x) {
            double u = (double)x / (double)SIZE * Math.PI * 2.0;
            double v = (double)y / (double)SIZE * Math.PI * 2.0;
            // целые частоты — текстура стыкуется сама с собой без шва
            double n = Math.sin(u * 3.0 + Math.sin(v * 2.0) * 1.4) * 0.5 + Math.sin(u * 5.0 - v * 1.0 + 1.7) * 0.3 + Math.sin(u * 2.0 + v * 3.0 + 4.1) * 0.25
               + Math.sin(u * 7.0 + Math.sin(v * 1.0 + 2.0) * 2.0) * 0.18;
            float i = MathHelper.clamp((float)(n * 0.62 + 0.5), 0.0F, 1.0F);
            i = i * i * (3.0F - 2.0F * i);
            // горячая середина языка уходит в жёлто-белый
            float hot = MathHelper.clamp((i - 0.6F) / 0.4F, 0.0F, 1.0F);
            float r = MathHelper.clamp(cr * i * 1.25F + hot * 0.7F, 0.0F, 1.0F);
            float g = MathHelper.clamp(cg * i * 1.25F + hot * 0.6F, 0.0F, 1.0F);
            float b = MathHelper.clamp(cb * i * 1.25F + hot * 0.35F, 0.0F, 1.0F);
            image.setColorArgb(x, y, 0xFF000000 | (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F));
         }
      }

      mc.getTextureManager().registerTexture(TEXTURE, new NativeImageBackedTexture(image));
   }

   private static RenderLayer layer(boolean edges) {
      RenderPhase.Texturing texturing = new RenderPhase.Texturing(edges ? "pain_fire_edges_texturing" : "pain_fire_texturing", () -> {
         FireChams m = INSTANCE;
         float t = (float)(System.currentTimeMillis() % 100000L) / 1000.0F;
         float s = 70.0F / m.scale.getCurrent();
         // текстура едет так, что языки поднимаются вверх и слегка гуляют в стороны
         com.mojang.blaze3d.systems.RenderSystem.setTextureMatrix(new Matrix4f().translation((float)Math.sin((double)t * 1.3) * 0.08F, t * 0.9F * m.speed.getCurrent(), 0.0F).scale(s, s, 1.0F));
         if (edges) {
            // у увеличенной копии оставляем только дальние от камеры стенки — их видно лишь за краем предмета
            GL11.glCullFace(mirrored ? GL11.GL_BACK : GL11.GL_FRONT);
         }
      }, () -> {
         com.mojang.blaze3d.systems.RenderSystem.resetTextureMatrix();
         if (edges) {
            GL11.glCullFace(GL11.GL_BACK);
         }
      });
      return RenderLayer.of(edges ? "pain_fire_edges" : "pain_fire", VertexFormats.POSITION_TEXTURE, DrawMode.QUADS, 1536,
         RenderLayer.MultiPhaseParameters.builder()
            .program(RenderPhase.GLINT_PROGRAM)
            .texture(new RenderPhase.Texture(TEXTURE, TriState.TRUE, false))
            .writeMaskState(RenderPhase.COLOR_MASK)
            .cull(edges ? RenderPhase.ENABLE_CULLING : RenderPhase.DISABLE_CULLING)
            .depthTest(edges ? RenderPhase.LEQUAL_DEPTH_TEST : RenderPhase.EQUAL_DEPTH_TEST)
            .transparency(RenderPhase.GLINT_TRANSPARENCY)
            .texturing(texturing)
            .build(false));
   }

   /** Второй проход по модели предмета — пламя. Вызывается сразу после обычной отрисовки модели. */
   public static void afterItemModel(MatrixStack matrices, VertexConsumerProvider consumers, int[] tints, BakedModel model) {
      if (current == null || drawing || !(consumers instanceof VertexConsumerProvider.Immediate immediate)) {
         return;
      }

      FireChams module = INSTANCE;
      drawing = true;

      try {
         module.ensureTexture();
         if (wholeLayer == null) {
            wholeLayer = layer(false);
            edgeLayer = layer(true);
         }

         // сам предмет должен уже лежать в кадре, иначе пламени не к чему «прилипнуть»
         immediate.draw();
         if (!module.area.is("Грани")) {
            VertexConsumer consumer = immediate.getBuffer(wholeLayer);
            ItemRenderer.renderBakedItemModel(model, tints, 15728880, OverlayTexture.DEFAULT_UV, matrices, consumer);
            immediate.draw(wholeLayer);
         }

         if (!module.area.is("Весь предмет")) {
            float grow = 1.0F + 0.06F * module.edge.getCurrent();
            matrices.push();
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.scale(grow, grow, 1.0F + 0.25F * module.edge.getCurrent());
            matrices.translate(-0.5F, -0.5F, -0.5F);
            // в левой руке модель зеркальная, «лицо» и «изнанка» меняются местами
            mirrored = matrices.peek().getPositionMatrix().determinant() < 0.0F;
            VertexConsumer consumer = immediate.getBuffer(edgeLayer);
            ItemRenderer.renderBakedItemModel(model, tints, 15728880, OverlayTexture.DEFAULT_UV, matrices, consumer);
            immediate.draw(edgeLayer);
            matrices.pop();
         }
      } finally {
         drawing = false;
      }
   }
}
