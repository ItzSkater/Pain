package tech.pain.utility.mixin.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ModelTransformationMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tech.pain.client.modules.impl.render.FireChams;

/** После отрисовки модели предмета даём FireChams нарисовать поверх пламя. */
@Mixin(ItemRenderer.class)
public class FireChamsItemRendererMixin {
   @Inject(
      method = "renderItem(Lnet/minecraft/item/ModelTransformationMode;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II[ILnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V",
      at = @At("TAIL")
   )
   private static void pain$fire(ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay, int[] tints, BakedModel model, RenderLayer layer, ItemRenderState.Glint glint, CallbackInfo ci) {
      FireChams.afterItemModel(matrices, consumers, tints, model);
   }
}
