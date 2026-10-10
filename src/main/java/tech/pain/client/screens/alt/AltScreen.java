package tech.pain.client.screens.alt;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.pain.Pain;
import tech.pain.base.alt.AltManager;
import tech.pain.base.font.Fonts;
import tech.pain.base.theme.Theme;
import tech.pain.client.hud.HudStyle;
import tech.pain.client.screens.title.PainTitleScreen;
import tech.pain.utility.interfaces.IMinecraft;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.GuiUtil;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Менеджер аккаунтов: список офлайн-ников, вход одним кликом без перезапуска игры.
 * Двойной клик по нику — войти; внизу поле для нового ника.
 */
public class AltScreen extends Screen implements IMinecraft {
   private static final float SCALE = 2.0F;
   private static final float ROW = 20.0F;
   private static final int VISIBLE = 8;
   private final Screen parent;
   private final long openedAt = System.currentTimeMillis();
   private final List<Hit> hits = new ArrayList<>();
   private final java.util.Map<String, Float> hover = new java.util.HashMap<>();
   private String input = "";
   private String selected;
   private String message = "";
   private long messageAt;
   private boolean typing = true;
   private float scroll;
   private float scrollTarget;
   private long lastClick;
   private String lastClicked = "";
   private float mouseX;
   private float mouseY;

   public AltScreen(Screen parent) {
      super(Text.of("Аккаунты"));
      this.parent = parent;
      this.selected = AltManager.current();
   }

   private record Hit(float x, float y, float w, float h, Runnable action) {
      boolean contains(float mx, float my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   @Override
   public void render(DrawContext context, int mx, int my, float delta) {
      double savedScale = mc.getWindow().getScaleFactor();
      setScale(SCALE);
      Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
      RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float)mc.getWindow().getScaledWidth(), (float)mc.getWindow().getScaledHeight(), 0.0F, 1000.0F, 21000.0F), ProjectionType.ORTHOGRAPHIC);

      try {
         this.renderAlts(new CustomDrawContext(mc.getBufferBuilders().getEntityVertexConsumers()));
      } catch (Throwable e) {
         e.printStackTrace();
      } finally {
         context.draw();
         setScale(savedScale);
         RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
      }
   }

   private static void setScale(double scale) {
      mc.getWindow().scaleFactor = scale;
      int i = (int)((double)mc.getWindow().framebufferWidth / scale);
      mc.getWindow().scaledWidth = (double)mc.getWindow().framebufferWidth / scale > (double)i ? i + 1 : i;
      int j = (int)((double)mc.getWindow().framebufferHeight / scale);
      mc.getWindow().scaledHeight = (double)mc.getWindow().framebufferHeight / scale > (double)j ? j + 1 : j;
   }

   private void renderAlts(CustomDrawContext ctx) {
      MatrixStack m = ctx.getMatrices();
      Vector2f mouse = GuiUtil.getMouse(SCALE);
      this.mouseX = mouse.getX();
      this.mouseY = mouse.getY();
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      ColorRGBA c1 = theme.getColor();
      float appear = Math.min(1.0F, (float)(System.currentTimeMillis() - this.openedAt) / 350.0F);
      appear = 1.0F - (1.0F - appear) * (1.0F - appear) * (1.0F - appear);
      PainTitleScreen.drawWallpaper(m, sw, sh, this.mouseX, this.mouseY);
      DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, sw, sh, BorderRadius.ZERO, new ColorRGBA(6, 6, 12, 110));
      this.hits.clear();
      List<String> accounts = AltManager.accounts();
      String current = AltManager.current();
      float w = 250.0F;
      float listH = ROW * (float)VISIBLE;
      float h = 34.0F + listH + 78.0F;
      float x = (sw - w) / 2.0F;
      float y = (sh - h) / 2.0F + (1.0F - appear) * 10.0F;
      HudStyle.glass(m, x, y, w, h, BorderRadius.all(12.0F), appear, true);
      ctx.drawText(Fonts.BOLD.getFont(11.0F), "Аккаунты", x + 12.0F, y + 10.0F, new ColorRGBA(255, 255, 255, (int)(255.0F * appear)));
      String now = "сейчас: " + current;
      float nowW = Fonts.REGULAR.getWidth(now, 7.0F);
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), now, x + w - nowW - 12.0F, y + 13.0F, new ColorRGBA(210, 210, 225, (int)(190.0F * appear)));
      DrawUtil.drawRoundedRect(m, x + 10.0F, y + 28.0F, w - 20.0F, 0.5F, BorderRadius.ZERO, new ColorRGBA(255, 255, 255, 30.0F * appear));

      // список
      float listY = y + 34.0F;
      float max = Math.max(0.0F, (float)accounts.size() * ROW - listH);
      this.scrollTarget = Math.max(0.0F, Math.min(max, this.scrollTarget));
      this.scroll += (this.scrollTarget - this.scroll) * 0.25F;
      ctx.draw();
      ctx.enableScissor((int)x, (int)listY, (int)(x + w), (int)(listY + listH));

      for (int i = 0; i < accounts.size(); ++i) {
         String name = accounts.get(i);
         float ry = listY + (float)i * ROW - this.scroll;
         if (ry + ROW < listY || ry > listY + listH) {
            continue;
         }

         boolean inside = this.mouseY >= listY && this.mouseY < listY + listH;
         boolean over = inside && this.mouseX >= x + 8.0F && this.mouseX < x + w - 8.0F && this.mouseY >= ry && this.mouseY < ry + ROW;
         float hv = this.hover.getOrDefault(name, 0.0F);
         hv += ((over ? 1.0F : 0.0F) - hv) * 0.25F;
         this.hover.put(name, hv);
         boolean sel = name.equalsIgnoreCase(this.selected);
         boolean cur = name.equalsIgnoreCase(current);
         BorderRadius r = BorderRadius.all(5.0F);
         if (sel) {
            DrawUtil.drawRoundedRect(m, x + 8.0F, ry + 1.0F, w - 16.0F, ROW - 2.0F, r, c1.withAlpha(70.0F * appear));
         }

         if (hv > 0.02F) {
            DrawUtil.drawRoundedRect(m, x + 8.0F, ry + 1.0F, w - 16.0F, ROW - 2.0F, r, new ColorRGBA(255, 255, 255, 22.0F * hv * appear));
         }

         DrawUtil.drawRoundedRect(m, x + 15.0F, ry + ROW / 2.0F - 2.5F, 5.0F, 5.0F, BorderRadius.all(2.5F), cur ? new ColorRGBA(60, 220, 110, (int)(255.0F * appear)) : new ColorRGBA(255, 255, 255, (int)(60.0F * appear)));
         ctx.drawText(Fonts.MEDIUM.getFont(8.0F), name, x + 26.0F, ry + ROW / 2.0F - 4.0F, new ColorRGBA(245, 245, 250, (int)((cur ? 255.0F : 215.0F) * appear)));
         if (cur) {
            String tag = "в игре";
            float tagW = Fonts.REGULAR.getWidth(tag, 6.5F);
            ctx.drawText(Fonts.REGULAR.getFont(6.5F), tag, x + w - tagW - 16.0F, ry + ROW / 2.0F - 3.0F, new ColorRGBA(120, 235, 160, (int)(220.0F * appear)));
         }

         if (inside) {
            this.hits.add(new Hit(x + 8.0F, ry, w - 16.0F, ROW, () -> this.clickRow(name)));
         }
      }

      ctx.draw();
      ctx.disableScissor();
      if (accounts.isEmpty()) {
         String empty = "Список пуст — добавь ник ниже";
         ctx.drawText(Fonts.REGULAR.getFont(7.5F), empty, x + (w - Fonts.REGULAR.getWidth(empty, 7.5F)) / 2.0F, listY + listH / 2.0F - 4.0F, new ColorRGBA(210, 210, 225, (int)(170.0F * appear)));
      }

      // поле ввода
      float fy = listY + listH + 8.0F;
      float fw = w - 20.0F - 62.0F;
      BorderRadius fr = BorderRadius.all(6.0F);
      DrawUtil.drawRoundedRect(m, x + 10.0F, fy, fw, 18.0F, fr, new ColorRGBA(10, 12, 20, 130.0F * appear));
      DrawUtil.drawRoundedBorder(m, x + 10.0F, fy, fw, 18.0F, -0.5F, fr, (this.typing ? c1 : new ColorRGBA(255, 255, 255)).withAlpha((this.typing ? 200.0F : 50.0F) * appear));
      boolean caret = this.typing && System.currentTimeMillis() / 500L % 2L == 0L;
      String shown = this.input.isEmpty() && !this.typing ? "новый ник" : this.input + (caret ? "|" : "");
      ctx.drawText(Fonts.REGULAR.getFont(8.0F), shown, x + 16.0F, fy + 5.0F, new ColorRGBA(245, 245, 250, (int)((this.input.isEmpty() && !this.typing ? 120.0F : 240.0F) * appear)));
      this.hits.add(new Hit(x + 10.0F, fy, fw, 18.0F, () -> this.typing = true));
      this.button(ctx, m, x + 10.0F + fw + 4.0F, fy, 58.0F, 18.0F, "Добавить", appear, c1, this::addInput);

      // кнопки
      float by = fy + 24.0F;
      float bw = (w - 20.0F - 12.0F) / 4.0F;
      this.button(ctx, m, x + 10.0F, by, bw, 18.0F, "Войти", appear, c1, () -> this.login(this.selected));
      this.button(ctx, m, x + 10.0F + (bw + 4.0F), by, bw, 18.0F, "Случайный", appear, c1, () -> {
         String name = AltManager.randomName();
         AltManager.add(name);
         this.login(name);
      });
      this.button(ctx, m, x + 10.0F + (bw + 4.0F) * 2.0F, by, bw, 18.0F, "Удалить", appear, c1, this::removeSelected);
      this.button(ctx, m, x + 10.0F + (bw + 4.0F) * 3.0F, by, bw, 18.0F, "Назад", appear, c1, this::close);
      long since = System.currentTimeMillis() - this.messageAt;
      if (!this.message.isEmpty() && since < 3500L) {
         float ma = Math.min(1.0F, (float)(3500L - since) / 500.0F) * appear;
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), this.message, x + (w - Fonts.REGULAR.getWidth(this.message, 7.0F)) / 2.0F, by + 25.0F, new ColorRGBA(225, 225, 240, (int)(220.0F * ma)));
      }

      ctx.draw();
   }

   private void button(CustomDrawContext ctx, MatrixStack m, float x, float y, float w, float h, String text, float a, ColorRGBA accent, Runnable action) {
      boolean over = this.mouseX >= x && this.mouseX < x + w && this.mouseY >= y && this.mouseY < y + h;
      float hv = this.hover.getOrDefault("#" + text, 0.0F);
      hv += ((over ? 1.0F : 0.0F) - hv) * 0.25F;
      this.hover.put("#" + text, hv);
      BorderRadius r = BorderRadius.all(6.0F);
      DrawUtil.drawRoundedRect(m, x, y, w, h, r, new ColorRGBA(255, 255, 255, (20.0F + 14.0F * hv) * a));
      if (hv > 0.02F) {
         DrawUtil.drawRoundedRect(m, x, y, w, h, r, accent.withAlpha(80.0F * hv * a));
      }

      DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, r, new ColorRGBA(255, 255, 255, (45.0F + 60.0F * hv) * a));
      float tw = Fonts.MEDIUM.getWidth(text, 7.0F);
      ctx.drawText(Fonts.MEDIUM.getFont(7.0F), text, x + (w - tw) / 2.0F, y + h / 2.0F - 3.5F, new ColorRGBA(250, 250, 255, (int)((215.0F + 40.0F * hv) * a)));
      this.hits.add(new Hit(x, y, w, h, action));
   }

   private void say(String text) {
      this.message = text;
      this.messageAt = System.currentTimeMillis();
   }

   private void clickRow(String name) {
      long now = System.currentTimeMillis();
      if (name.equals(this.lastClicked) && now - this.lastClick < 350L) {
         this.login(name);
      }

      this.selected = name;
      this.lastClicked = name;
      this.lastClick = now;
   }

   private void login(String name) {
      if (name == null || !AltManager.valid(name)) {
         this.say("Выбери аккаунт в списке");
         return;
      }

      AltManager.login(name);
      this.selected = name;
      this.say(mc.world != null ? "Ник " + name + " — заработает после перезахода на сервер" : "Вошёл как " + name);
   }

   private void addInput() {
      String name = this.input.trim();
      if (!AltManager.valid(name)) {
         this.say("Ник: 3–16 символов, латиница, цифры и _");
         return;
      }

      if (!AltManager.add(name)) {
         this.say("Такой ник уже есть");
      }

      this.input = "";
      this.login(name);
   }

   private void removeSelected() {
      if (this.selected == null) {
         return;
      }

      if (this.selected.equalsIgnoreCase(AltManager.current())) {
         this.say("Нельзя удалить аккаунт, под которым ты сейчас");
         return;
      }

      AltManager.remove(this.selected);
      this.say("Удалён " + this.selected);
      this.selected = AltManager.current();
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         this.typing = false;

         for (int i = this.hits.size() - 1; i >= 0; --i) {
            Hit hit = this.hits.get(i);
            if (hit.contains(this.mouseX, this.mouseY)) {
               hit.action().run();
               break;
            }
         }
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
      this.scrollTarget -= (float)vertical * ROW * 2.0F;
      return true;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.typing && this.input.length() < 16 && (Character.isLetterOrDigit(chr) && chr < 128 || chr == '_')) {
         this.input += chr;
      }

      return true;
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (key == GLFW.GLFW_KEY_ESCAPE) {
         this.close();
         return true;
      }

      if (this.typing) {
         if (key == GLFW.GLFW_KEY_BACKSPACE && !this.input.isEmpty()) {
            this.input = this.input.substring(0, this.input.length() - 1);
         } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            this.addInput();
         } else if (key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clip = mc.keyboard.getClipboard().replaceAll("[^A-Za-z0-9_]", "");
            this.input = (this.input + clip).substring(0, Math.min(16, this.input.length() + clip.length()));
         }
      }

      return true;
   }

   @Override
   public void close() {
      mc.setScreen(this.parent);
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }
}
