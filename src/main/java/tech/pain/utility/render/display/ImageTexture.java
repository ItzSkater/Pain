package tech.pain.utility.render.display;

import com.mojang.blaze3d.platform.GlStateManager;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Картинка из ресурсов мода (jpg/png) как GL-текстура.
 * Декодируется в фоне, в видеопамять попадает при первом обращении из потока рендера.
 */
public final class ImageTexture {
   private final String resource;
   private volatile ByteBuffer pixels;
   private volatile int width;
   private volatile int height;
   private volatile boolean failed;
   private boolean started;
   private int id = -1;

   private java.nio.file.Path file;

   public ImageTexture(String resource) {
      this.resource = resource;
   }

   /** Картинка с диска (jpg/png). */
   public ImageTexture(java.nio.file.Path file) {
      this.resource = null;
      this.file = file;
   }

   public boolean failed() {
      return this.failed;
   }

   public void dispose() {
      if (this.id != -1) {
         GL11.glDeleteTextures(this.id);
         this.id = -1;
      }
   }

   public int width() {
      return this.width;
   }

   public int height() {
      return this.height;
   }

   /** Идентификатор текстуры или -1, пока картинка не готова. */
   public int id() {
      if (this.id != -1 || this.failed) {
         return this.id;
      }

      if (!this.started) {
         this.started = true;
         Thread t = new Thread(this::decode, "Pain-Image-Decode");
         t.setDaemon(true);
         t.start();
         return -1;
      }

      ByteBuffer data = this.pixels;
      if (data == null) {
         return -1;
      }

      this.id = GL11.glGenTextures();
      GlStateManager._bindTexture(this.id);
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, this.width, this.height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, data);
      GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      STBImage.stbi_image_free(data);
      this.pixels = null;
      return this.id;
   }

   private void decode() {
      ByteBuffer file = null;

      try (MemoryStack stack = MemoryStack.stackPush(); InputStream in = this.file != null ? java.nio.file.Files.newInputStream(this.file) : ImageTexture.class.getResourceAsStream(this.resource)) {
         if (in == null) {
            this.failed = true;
            return;
         }

         byte[] bytes = in.readAllBytes();
         file = MemoryUtil.memAlloc(bytes.length);
         file.put(bytes).flip();
         IntBuffer w = stack.mallocInt(1);
         IntBuffer h = stack.mallocInt(1);
         IntBuffer c = stack.mallocInt(1);
         ByteBuffer decoded = STBImage.stbi_load_from_memory(file, w, h, c, 3);
         if (decoded == null) {
            this.failed = true;
            return;
         }

         this.width = w.get(0);
         this.height = h.get(0);
         this.pixels = decoded;
      } catch (Throwable e) {
         this.failed = true;
      } finally {
         if (file != null) {
            MemoryUtil.memFree(file);
         }
      }
   }
}
