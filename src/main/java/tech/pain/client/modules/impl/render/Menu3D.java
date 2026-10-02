package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import tech.pain.Pain;
import tech.pain.base.events.impl.input.EventHotBarScroll;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.screens.menu.rock.RockMenuScreen;
import tech.pain.utility.render.level.Panel3D;

/**
 * «Кинотеатр»: то же меню, но на экране, который стоит в мире.
 * Меню рисуется в отдельный framebuffer и натягивается на прямоугольник в 3D;
 * наводишься прицелом — на экране появляется курсор, клики и колесо уходят в меню.
 */
@ModuleAnnotation(
   name = "Menu3D",
   category = Category.RENDER,
   description = "Меню на 3D-экране в мире («кинотеатр»)"
)
public final class Menu3D extends Module {
   public static final Menu3D INSTANCE = new Menu3D();
   private final NumberSetting distance = new NumberSetting("Дистанция", 3.5F, 2.0F, 8.0F, 0.25F);
   private final NumberSetting size = new NumberSetting("Ширина экрана", 5.0F, 2.5F, 12.0F, 0.25F);
   private final NumberSetting opacity = new NumberSetting("Непрозрачность", 0.97F, 0.5F, 1.0F, 0.01F);
   private final BooleanSetting follow = new BooleanSetting("Переставлять, если ушёл", true);
   private final BooleanSetting glow = new BooleanSetting("Подсветка рамки", true);
   private final tech.pain.client.modules.api.setting.impl.ModeSetting style = new tech.pain.client.modules.api.setting.impl.ModeSetting("Стиль", "Cool", "Liquid Glass");
   private final Panel3D panel = new Panel3D();
   private RockMenuScreen menu;
   private Framebuffer fbo;
   private boolean registered;
   private boolean aiming;
   private boolean pressed;
   private float cursorX;
   private float cursorY;

   private Menu3D() {
      this.setKeyCode(GLFW.GLFW_KEY_G);
   }

   public boolean glass() {
      return this.style.is("Liquid Glass");
   }

   @Override
   public void onEnable() {
      if (mc.world == null || mc.player == null) {
         this.setEnabled(false);
         return;
      }

      if (this.menu == null) {
         this.menu = new RockMenuScreen(() -> {
            if (this.isEnabled()) {
               this.toggle();
            }
         });
      }

      this.panel.place(this.distance.getCurrent());
      this.panel.show();
      if (!this.registered) {
         this.registered = true;
         super.onEnable();
      }
   }

   @Override
   public void onDisable() {
      // не отписываемся сразу: экран остаётся на месте и растворяется, даже если отойти
      this.aiming = false;
      this.pressed = false;
      this.panel.hide();
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (this.menu == null || mc.player == null) {
         return;
      }

      if (!this.panel.isVisible()) {
         if (!this.isEnabled() && this.registered) {
            this.registered = false;
            EventManager.unregister(this);
         }

         return;
      }

      Camera cam = mc.gameRenderer.getCamera();
      if (this.isEnabled() && this.follow.isEnabled() && this.panel.distanceTo(cam.getPos()) > (double)this.distance.getCurrent() * 3.0 + 4.0) {
         this.panel.place(this.distance.getCurrent());
      }

      float fbW = (float)mc.getWindow().getFramebufferWidth();
      float fbH = (float)mc.getWindow().getFramebufferHeight();
      float sw = fbW / 2.0F;
      float sh = fbH / 2.0F;
      // на экран попадает ровно окно меню — скруглённые углы совпадают с его углами
      float rw = this.menu.winW() > 0.0F ? this.menu.winW() : 488.0F;
      float rh = this.menu.winH() > 0.0F ? this.menu.winH() : 318.0F;
      float rx = this.menu.winW() > 0.0F ? this.menu.winX() : (sw - rw) / 2.0F;
      float ry = this.menu.winH() > 0.0F ? this.menu.winY() : (sh - rh) / 2.0F;
      double width = this.size.getCurrent();
      double height = width * (double)rh / (double)rw;
      this.aiming = false;
      if (this.isEnabled() && mc.currentScreen == null) {
         float[] uv = this.panel.aim(width, height);
         if (uv != null) {
            this.aiming = true;
            this.cursorX = rx + uv[0] * rw;
            this.cursorY = ry + uv[1] * rh;
         }
      }

      this.renderToBuffer((int)fbW, (int)fbH, sw, sh);
      this.panel.draw(event, this.fbo.getColorAttachment(), rx / sw, 1.0F - ry / sh, (rx + rw) / sw, 1.0F - (ry + rh) / sh, rw, rh, width, 12.0F, this.glass() ? Math.min(this.opacity.getCurrent(), 0.8F) : this.opacity.getCurrent(), this.glow.isEnabled());
   }

   /** Меню — в свой буфер, с обычной 2D-проекцией; потом возвращаем состояние мира. */
   private void renderToBuffer(int fbW, int fbH, float sw, float sh) {
      if (this.fbo == null) {
         this.fbo = new SimpleFramebuffer(fbW, fbH, true);
      } else if (this.fbo.textureWidth != fbW || this.fbo.textureHeight != fbH) {
         this.fbo.resize(fbW, fbH);
      }

      Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
      Matrix4fStack modelView = RenderSystem.getModelViewStack();
      modelView.pushMatrix();
      modelView.identity();
      RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, sw, sh, 0.0F, -1000.0F, 1000.0F), ProjectionType.ORTHOGRAPHIC);
      if (this.glass()) {
         this.fbo.setClearColor(46.0F / 255.0F, 52.0F / 255.0F, 66.0F / 255.0F, 1.0F);
      } else {
         this.fbo.setClearColor(12.0F / 255.0F, 12.0F / 255.0F, 16.0F / 255.0F, 1.0F);
      }

      this.fbo.clear();
      this.fbo.beginWrite(true);
      RenderSystem.disableDepthTest();

      try {
         this.menu.renderEmbedded(this.cursorX, this.cursorY, this.aiming);
      } finally {
         // альфа буфера после полупрозрачных слоёв «дырявая» — делаем экран сплошным
         GL11.glDisable(GL11.GL_SCISSOR_TEST);
         GL11.glColorMask(false, false, false, true);
         GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
         GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
         GL11.glColorMask(true, true, true, true);
         RenderSystem.enableDepthTest();
         mc.getFramebuffer().beginWrite(true);
         modelView.popMatrix();
         RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.PERSPECTIVE);
      }
   }

   // ------------------------------------------------------------------ ввод

   /** true — клик забрало меню, игре его не отдаём (не бьём и не ставим блоки сквозь экран). */
   public boolean handleMouse(int button, int action) {
      if (!this.isEnabled() || this.menu == null || mc.currentScreen != null) {
         return false;
      }

      if (action == 1) {
         if (!this.aiming) {
            return false;
         }

         this.pressed = true;
         this.menu.cinemaClick(button);
         return true;
      }

      if (this.pressed) {
         this.pressed = false;
         this.menu.cinemaRelease(button);
         return true;
      }

      return false;
   }

   @EventTarget
   public void onScroll(EventHotBarScroll event) {
      if (this.aiming && this.menu != null && mc.currentScreen == null) {
         this.menu.cinemaScroll(event.getVertical());
         event.setCancelled(true);
      }
   }

   /** Клавиши уходят в меню, только когда оно их ждёт: поиск, назначение бинда, колорпикер. */
   public boolean handleKey(int key, int scancode, int action) {
      if (!this.isEnabled() || this.menu == null || mc.currentScreen != null || action == 0) {
         return false;
      }

      if (key == GLFW.GLFW_KEY_ESCAPE) {
         if (this.menu.wantsKeyboard()) {
            this.menu.keyPressed(key, scancode, 0);
         } else {
            this.toggle();
         }

         return true;
      }

      if (!this.menu.wantsKeyboard()) {
         return false;
      }

      this.menu.keyPressed(key, scancode, 0);
      String name = key == GLFW.GLFW_KEY_SPACE ? " " : GLFW.glfwGetKeyName(key, scancode);
      if (name != null && name.length() == 1) {
         this.menu.charTyped(name.charAt(0), 0);
      }

      return true;
   }
}
