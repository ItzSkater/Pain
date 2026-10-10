package tech.pain.utility.render.display;

import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import tech.pain.utility.interfaces.IClient;

public class StencilUtil implements IClient {
   private static int stencilTexture = -1;
   private static int stencilWidth;
   private static int stencilHeight;

   public static void push() {
      Framebuffer framebuffer = mc.getFramebuffer();
      int depth = framebuffer.getDepthAttachment();
      // Маске нужен трафарет, а у основного кадра его нет. Раньше текстуру глубины здесь выбрасывали и ставили
      // вместо неё рендербуфер — после этого её не мог прочитать никто (Iris рисовал чёрный мир).
      // Теперь та же текстура просто пересоздаётся в формате «глубина + трафарет» и остаётся на месте.
      if (depth > -1 && (depth != stencilTexture || stencilWidth != framebuffer.textureWidth || stencilHeight != framebuffer.textureHeight)) {
         framebuffer.beginWrite(false);
         com.mojang.blaze3d.platform.GlStateManager._bindTexture(depth);
         GL11.glTexImage2D(3553, 0, org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8, framebuffer.textureWidth, framebuffer.textureHeight, 0, org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL, org.lwjgl.opengl.GL30.GL_UNSIGNED_INT_24_8, (java.nio.ByteBuffer)null);
         org.lwjgl.opengl.GL30.glFramebufferTexture2D(36160, org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT, 3553, depth, 0);
         stencilTexture = depth;
         stencilWidth = framebuffer.textureWidth;
         stencilHeight = framebuffer.textureHeight;
      }

      GL11.glStencilMask(255);
      GL11.glClear(1024);
      GL11.glEnable(2960);
      GL11.glStencilFunc(519, 1, 1);
      GL11.glStencilOp(7681, 7681, 7681);
      GL11.glDisable(2929);
      GL11.glColorMask(false, false, false, false);
   }

   public static void read(int ref) {
      GL11.glColorMask(true, true, true, true);
      GL11.glStencilFunc(514, ref, 1);
      GL11.glStencilOp(7680, 7680, 7680);
   }

   public static void pop() {
      GL11.glDisable(2960);
      GL11.glEnable(2929);
   }
}
