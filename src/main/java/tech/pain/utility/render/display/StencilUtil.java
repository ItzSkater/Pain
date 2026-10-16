package tech.pain.utility.render.display;

import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import tech.pain.utility.interfaces.IClient;

public class StencilUtil implements IClient {
   // свой буфер «глубина + трафарет»: подключается к кадру только на время маски
   private static int stencilBuffer = -1;
   private static int stencilWidth;
   private static int stencilHeight;
   private static int swappedDepth = -1;
   private static int swappedFbo = -1;

   /**
    * Включает маску. Раньше здесь навсегда меняли формат текстуры глубины кадра на «глубина + трафарет» —
    * после этого Iris каждый кадр не мог скопировать глубину в свои текстуры (сотни ошибок OpenGL в секунду).
    * Теперь текстура глубины не трогается: на время маски вместо неё подключается свой буфер, потом она возвращается.
    */
   public static void push() {
      Framebuffer framebuffer = mc.getFramebuffer();
      int depth = framebuffer.getDepthAttachment();
      int w = framebuffer.textureWidth;
      int h = framebuffer.textureHeight;
      if (stencilBuffer == -1) {
         stencilBuffer = org.lwjgl.opengl.GL30.glGenRenderbuffers();
         stencilWidth = -1;
      }

      if (stencilWidth != w || stencilHeight != h) {
         org.lwjgl.opengl.GL30.glBindRenderbuffer(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, stencilBuffer);
         org.lwjgl.opengl.GL30.glRenderbufferStorage(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8, w, h);
         org.lwjgl.opengl.GL30.glBindRenderbuffer(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, 0);
         stencilWidth = w;
         stencilHeight = h;
      }

      framebuffer.beginWrite(false);
      if (swappedFbo == -1) {
         swappedFbo = framebuffer.fbo;
         swappedDepth = depth;
         org.lwjgl.opengl.GL30.glFramebufferTexture2D(36160, org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT, 3553, 0, 0);
         org.lwjgl.opengl.GL30.glFramebufferRenderbuffer(36160, org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT, org.lwjgl.opengl.GL30.GL_RENDERBUFFER, stencilBuffer);
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
      if (swappedFbo != -1) {
         // возвращаем кадру его родную текстуру глубины
         int previous = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
         org.lwjgl.opengl.GL30.glBindFramebuffer(36160, swappedFbo);
         org.lwjgl.opengl.GL30.glFramebufferRenderbuffer(36160, org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT, org.lwjgl.opengl.GL30.GL_RENDERBUFFER, 0);
         org.lwjgl.opengl.GL30.glFramebufferTexture2D(36160, org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT, 3553, swappedDepth, 0);
         org.lwjgl.opengl.GL30.glBindFramebuffer(36160, previous);
         swappedFbo = -1;
         swappedDepth = -1;
      }
   }
}
