package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import tech.pain.Pain;
import tech.pain.base.browser.BrowserCast;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.Panel3D;

/**
 * 3D-экран с окном твоего браузера: показывает уже открытый Zen/Firefox/Chromium/Chrome.
 * Только просмотр — клики в чужое окно Wayland отправить нельзя.
 */
@ModuleAnnotation(
   name = "Browser3D",
   category = Category.RENDER,
   description = "Окно открытого браузера на 3D-экране в мире (niri)"
)
public final class Browser3D extends Module {
   public static final Browser3D INSTANCE = new Browser3D();
   private final ModeSetting browser = new ModeSetting("Браузер", "Zen", "Firefox", "Chromium", "Chrome");
   private final ModeSetting quality = new ModeSetting("Качество", "Среднее", "Высокое", "Низкое");
   private final NumberSetting fps = new NumberSetting("Макс. FPS", 30.0F, 5.0F, 60.0F, 5.0F);
   private final NumberSetting distance = new NumberSetting("Дистанция", 4.0F, 2.0F, 12.0F, 0.25F);
   private final NumberSetting size = new NumberSetting("Ширина экрана", 6.0F, 2.5F, 16.0F, 0.25F);
   private final NumberSetting opacity = new NumberSetting("Непрозрачность", 1.0F, 0.5F, 1.0F, 0.01F);
   private final BooleanSetting glow = new BooleanSetting("Подсветка", true);
   private final Panel3D panel = new Panel3D();
   private boolean registered;
   private int texture = -1;
   private int texW;
   private int texH;
   private long shownFrame = -1L;
   private ByteBuffer buffer;
   private String config = "";

   private Browser3D() {
   }

   private String config() {
      return this.browser.get() + "|" + this.quality.get() + "|" + this.fps.getCurrent();
   }

   private void startCast() {
      this.config = this.config();
      int maxWidth = this.quality.is("Высокое") ? 1920 : (this.quality.is("Низкое") ? 800 : 1280);
      BrowserCast.start(this.browser.get().toLowerCase(), maxWidth, (int)this.fps.getCurrent());
   }

   @Override
   public void onEnable() {
      if (mc.world == null || mc.player == null) {
         this.setEnabled(false);
         return;
      }

      this.panel.place(this.distance.getCurrent());
      this.panel.show();
      this.startCast();
      if (!this.registered) {
         this.registered = true;
         super.onEnable();
      }
   }

   @Override
   public void onDisable() {
      // не отписываемся сразу: экран должен дорастворяться на месте
      this.panel.hide();
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (!this.panel.isVisible()) {
         if (!this.isEnabled() && this.registered) {
            this.registered = false;
            BrowserCast.stop();
            EventManager.unregister(this);
         }

         return;
      }

      if (this.isEnabled()) {
         if (!this.config().equals(this.config)) {
            this.startCast();
         }

         String error = BrowserCast.pollError();
         if (error != null) {
            Pain.getInstance().getNotifyManager().addNotification("", Text.of("Browser3D: " + error));
            this.toggle();
         }
      }

      BrowserCast.Frame frame = BrowserCast.getFrame();
      if (frame != null && frame.id() != this.shownFrame) {
         this.upload(frame);
      }

      if (this.texture == -1 || this.texW == 0) {
         return;
      }

      float pxW = 640.0F;
      float pxH = pxW * (float)this.texH / (float)this.texW;
      this.panel.draw(event, this.texture, 0.0F, 0.0F, 1.0F, 1.0F, pxW, pxH, this.size.getCurrent(), 14.0F, this.opacity.getCurrent(), this.glow.isEnabled());
   }

   /** Кадр — сразу в текстуру, без декодирования. */
   private void upload(BrowserCast.Frame frame) {
      this.shownFrame = frame.id();
      int len = frame.rgba().length;
      if (this.buffer == null || this.buffer.capacity() < len) {
         if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
         }

         this.buffer = MemoryUtil.memAlloc(len);
      }

      this.buffer.clear();
      this.buffer.put(frame.rgba());
      this.buffer.flip();
      if (this.texture == -1) {
         this.texture = GL11.glGenTextures();
      }

      GlStateManager._bindTexture(this.texture);
      GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
      GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
      GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
      if (frame.width() != this.texW || frame.height() != this.texH) {
         this.texW = frame.width();
         this.texH = frame.height();
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, this.texW, this.texH, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.buffer);
      } else {
         GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, this.texW, this.texH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.buffer);
      }
   }
}
