package tech.pain.client.screens.menu.rock;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.pain.Pain;
import tech.pain.base.animations.base.Animation;
import tech.pain.base.animations.base.Easing;
import tech.pain.base.font.Fonts;
import tech.pain.base.font.MsdfRenderer;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.setting.Setting;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ButtonSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.ItemSelectSetting;
import tech.pain.client.modules.api.setting.impl.KeySetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.impl.render.Menu;
import tech.pain.client.screens.menu.MenuScreen;
import tech.pain.client.screens.swarm.SwarmScreen;
import tech.pain.utility.game.other.ReplaceUtil;
import tech.pain.utility.game.player.PlayerInventoryComponent;
import tech.pain.utility.render.display.Keyboard;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.Gradient;
import tech.pain.utility.render.display.base.GuiUtil;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * ClickGUI в стиле Rockstar 2.x: окно 488x318 (r=12), три колонки —
 * иконки категорий (33), список модулей (101) и панель настроек/поиска.
 * Настройки модуля рисуются карточками в две колонки.
 */
public class RockMenuScreen extends MenuScreen {
   private static final float SCALE = 2.0F;
   private static final float BASE_W = 488.0F;
   private static final float BASE_H = 318.0F;
   private static final float RAIL = 33.0F;
   private static final float LIST = 101.0F;
   private static final float HEADER = 24.0F;
   private static final float RADIUS = 12.0F;

   private final Animation open = new Animation(260L, Easing.CUBIC_OUT);
   private final Animation categoryY = new Animation(220L, Easing.CUBIC_OUT);
   private final Animation contentFade = new Animation(220L, Easing.CUBIC_OUT);
   private final Map<Object, Float> hovers = new HashMap<>();
   private final List<Hit> hits = new ArrayList<>();

   private Category category = Category.COMBAT;
   private Module selected;
   private Module bindingModule;
   private KeySetting bindingKey;
   private NumberSetting drag;
   private float dragX;
   private float dragW;
   private boolean closing;
   private boolean categoryInit;
   private java.util.function.Supplier<ColorRGBA> pickGet;
   private java.util.function.Consumer<ColorRGBA> pickSet;
   private String pickTitle = "";
   private float pickX;
   private float pickY;
   private float pickHue;
   private float pickSat;
   private float pickVal;
   private float pickAlpha;
   private boolean pickHasAlpha;
   private int pickDrag;
   private static final float PICK_W = 124.0F;
   private long openedAt;

   private String query = "";
   private boolean searchFocus;

   private float listScroll;
   private float listTarget;
   private float listMax;
   private float setScroll;
   private float setTarget;
   private float setMax;
   private Module flash;
   private long flashUntil;

   private float x;
   private float y;
   private float w;
   private float h;
   private float mouseX;
   private float mouseY;
   private float alpha;

   // --- режим «кинотеатр»: меню рисуется в отдельный буфер и показывается на 3D-экране
   private boolean cinema;
   private boolean embedded;
   private float embMouseX;
   private float embMouseY;
   private Runnable cinemaClose;

   public RockMenuScreen() {
   }

   public RockMenuScreen(Runnable cinemaClose) {
      this.cinema = true;
      this.cinemaClose = cinemaClose;
      this.open.setValue(1.0F);
   }

   /** Liquid Glass: для обычного меню берётся из модуля ClickGUI, для 3D-экрана — из Menu3D. */
   private boolean glass() {
      return this.cinema ? tech.pain.client.modules.impl.render.Menu3D.INSTANCE.glass() : Menu.INSTANCE.style.is("Liquid Glass");
   }

   public float winX() {
      return this.x;
   }

   public float winY() {
      return this.y;
   }

   public float winW() {
      return this.w;
   }

   public float winH() {
      return this.h;
   }

   public boolean wantsKeyboard() {
      return this.searchFocus || this.bindingModule != null || this.bindingKey != null || this.pickSet != null;
   }

   /** Рисует меню в текущий framebuffer; курсор — точка, куда смотрит игрок на 3D-экране. */
   public void renderEmbedded(float cursorX, float cursorY, boolean hasCursor) {
      double savedScale = mc.getWindow().getScaleFactor();
      setScale(SCALE);
      this.embedded = true;
      this.embMouseX = hasCursor ? cursorX : -10000.0F;
      this.embMouseY = hasCursor ? cursorY : -10000.0F;
      CustomDrawContext ctx = new CustomDrawContext(mc.getBufferBuilders().getEntityVertexConsumers());

      try {
         this.renderMenu(ctx);
         if (hasCursor) {
            MatrixStack m = ctx.getMatrices();
            DrawUtil.drawRoundedRect(m, cursorX - 3.0F, cursorY - 3.0F, 6.0F, 6.0F, BorderRadius.all(3.0F), new ColorRGBA(0, 0, 0, 150));
            DrawUtil.drawRoundedRect(m, cursorX - 2.0F, cursorY - 2.0F, 4.0F, 4.0F, BorderRadius.all(2.0F), new ColorRGBA(255, 255, 255, 255));
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         ctx.draw();
         this.embedded = false;
         setScale(savedScale);
      }
   }

   public void cinemaClick(int button) {
      this.mouseClicked(0.0, 0.0, button);
   }

   public void cinemaRelease(int button) {
      this.mouseReleased(0.0, 0.0, button);
   }

   public void cinemaScroll(double vertical) {
      this.mouseScrolled(0.0, 0.0, 0.0, vertical);
   }

   @Override
   protected void init() {
      super.init();
      this.closing = false;
      this.needToClose = false;
      this.open.setValue(0.0F);
      this.open.setStartValue(0.0F);
      this.searchFocus = false;
      this.search = false;
      this.openedAt = System.currentTimeMillis();
   }

   // ------------------------------------------------------------------ render

   @Override
   public void render(DrawContext context, int mx, int my, float delta) {
      if (this.needToClose) {
         return;
      }

      double savedScale = mc.getWindow().getScaleFactor();
      setScale(SCALE);
      Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
      RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float)mc.getWindow().getScaledWidth(), (float)mc.getWindow().getScaledHeight(), 0.0F, 1000.0F, 21000.0F), ProjectionType.ORTHOGRAPHIC);

      try {
         this.renderMenu(context);
      } catch (Exception e) {
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

   private void renderMenu(DrawContext context) {
      CustomDrawContext ctx = context instanceof CustomDrawContext custom ? custom : CustomDrawContext.of(context);
      MatrixStack m = ctx.getMatrices();
      Vector2f mouse = GuiUtil.getMouse(SCALE);
      this.mouseX = this.embedded ? this.embMouseX : mouse.getX();
      this.mouseY = this.embedded ? this.embMouseY : mouse.getY();
      this.hits.clear();

      // после закрытия меню ещё рисуется «призраком» и растворяется, как классическое
      boolean ghost = !this.embedded && this.needToClose;
      float time = 1.0F;
      if (!this.embedded) {
         this.openAnimationMetanoise.setEasing(Easing.CIRC_OUT);
         this.openAnimationMetanoise.setDuration(ghost ? 2200L : 1400L);
         this.openAnimationMetanoise.update(!ghost);
         time = this.openAnimationMetanoise.getValue();
         if (ghost) {
            this.mouseX = -10000.0F;
            this.mouseY = -10000.0F;
         } else {
            this.search = this.searchFocus;
            if (!this.searchFocus) {
               PlayerInventoryComponent.updateMoveKeys();
            }
         }
      }

      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      this.w = Math.min(BASE_W, Math.max(360.0F, sw - 12.0F));
      this.h = Math.min(BASE_H, Math.max(235.0F, sh - 12.0F));
      this.x = (float)Math.round((sw - this.w) / 2.0F);
      this.y = (float)Math.round((sh - this.h) / 2.0F);
      this.alpha = this.open.getValue();
      float a = 1.0F;
      float scale = 1.0F;

      m.push();
      m.translate(sw / 2.0F, sh / 2.0F, 0.0F);
      m.scale(scale, scale, 1.0F);
      m.translate(-sw / 2.0F, -sh / 2.0F, 0.0F);
      // пересчёт мыши в координаты масштабированного окна
      this.mouseX = (this.mouseX - sw / 2.0F) / scale + sw / 2.0F;
      this.mouseY = (this.mouseY - sh / 2.0F) / scale + sh / 2.0F;

      Theme theme = theme();
      if (!this.embedded) {
         // на 3D-экране блюр и тень не нужны: там свой непрозрачный фон
         DrawUtil.drawShadow(m, this.x, this.y, this.w, this.h, 24.0F, BorderRadius.all(RADIUS), new ColorRGBA(0, 0, 0, 140.0F * Math.min(1.0F, time)));
         // маска растворения: всё меню рисуется только там, где шум уже «проявился»
         tech.pain.utility.render.display.StencilUtil.push();
         DrawUtil.drawMetanoise(m, this.x, this.y, this.w, this.h, time, RADIUS, new ColorRGBA(0, 0, 0, 255), theme.getColor());
         tech.pain.utility.render.display.StencilUtil.read(1);
         DrawUtil.drawBlur(m, this.x, this.y, this.w, this.h, 18.0F, BorderRadius.all(RADIUS), new ColorRGBA(255, 255, 255, 255.0F * a));
      }

      boolean glass = this.glass();
      if (glass) {
         // стекло: за окном сильный блюр, поверх лёгкое затемнение, белёсый тон и блик сверху
         if (this.embedded) {
            DrawUtil.drawRoundedRect(m, this.x, this.y, this.w, this.h, BorderRadius.all(RADIUS), new ColorRGBA(46, 52, 66, 255));
         } else {
            DrawUtil.drawBlur(m, this.x, this.y, this.w, this.h, 26.0F, BorderRadius.all(RADIUS), new ColorRGBA(255, 255, 255, 255.0F * a));
            DrawUtil.drawRoundedRect(m, this.x, this.y, this.w, this.h, BorderRadius.all(RADIUS), new ColorRGBA(22, 26, 36, 105.0F * a));
         }

         DrawUtil.drawRoundedRect(m, this.x, this.y, this.w, this.h, BorderRadius.all(RADIUS), new ColorRGBA(255, 255, 255, 18.0F * a));
         ColorRGBA hi = new ColorRGBA(255, 255, 255, 34.0F * a);
         ColorRGBA none = new ColorRGBA(255, 255, 255, 0);
         DrawUtil.drawRoundedRect(m, this.x + 1.0F, this.y + 0.5F, this.w - 2.0F, 46.0F, BorderRadius.top(RADIUS, RADIUS), Gradient.of(hi, none, hi, none));
      } else {
         DrawUtil.drawRoundedRect(m, this.x, this.y, this.w, this.h, BorderRadius.all(RADIUS), new ColorRGBA(12, 12, 16, this.embedded ? 255.0F : 228.0F * a));
      }

      DrawUtil.drawRoundedRect(m, this.x, this.y, RAIL, this.h, new BorderRadius(RADIUS, 0.0F, 0.0F, RADIUS), glass ? new ColorRGBA(255, 255, 255, 12.0F * a) : new ColorRGBA(0, 0, 0, 70.0F * a));
      ColorRGBA line = new ColorRGBA(255, 255, 255, (glass ? 34.0F : 16.0F) * a);
      float listX = this.x + RAIL;
      float contentX = listX + LIST;
      DrawUtil.drawRect(m, listX - 0.5F, this.y + 1.0F, 0.5F, this.h - 2.0F, line);
      DrawUtil.drawRect(m, contentX - 0.5F, this.y + 1.0F, 0.5F, this.h - 2.0F, line);
      DrawUtil.drawRect(m, contentX, this.y + HEADER - 0.5F, this.x + this.w - contentX - 1.0F, 0.5F, line);
      DrawUtil.drawRect(m, listX, this.y + HEADER - 0.5F, LIST - 0.5F, 0.5F, line);

      this.renderRail(ctx, m, theme, a);
      this.renderList(ctx, m, theme, a, listX);
      this.renderHeader(ctx, m, theme, a, contentX);
      this.renderContent(ctx, m, theme, a, contentX);

      DrawUtil.drawRoundedBorder(m, this.x, this.y, this.w, this.h, -0.5F, BorderRadius.all(RADIUS), new ColorRGBA(255, 255, 255, (glass ? 110.0F : 26.0F) * a));
      this.renderPicker(ctx, m, a);
      if (!this.embedded) {
         // светящаяся кромка на границе растворения
         DrawUtil.drawMetanoise(m, this.x, this.y, this.w, this.h, time, RADIUS, new ColorRGBA(0, 0, 0, 0), theme.getColor());
         tech.pain.utility.render.display.StencilUtil.pop();
      }

      m.pop();
   }

   private void renderRail(CustomDrawContext ctx, MatrixStack m, Theme theme, float a) {
      float cx = this.x + RAIL / 2.0F;
      ColorRGBA c1 = theme.getColor().withAlpha(255.0F * a);
      ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
      MsdfRenderer.renderText(Fonts.ICONS, "B", 12.0F, Gradient.of(c1, c2, c1, c2), m.peek().getPositionMatrix(), cx - 5.0F, this.y + 9.0F, 0.0F, true, cx - 5.0F, cx + 5.0F);

      float by = this.y + 34.0F;
      float selectedY = by;

      for (Category cat : Category.values()) {
         if (cat == Category.THEMES) {
            continue;
         }

         if (cat == this.category) {
            selectedY = by;
         }

         this.categoryButton(ctx, m, theme, a, cx - 8.5F, by, cat.getIcon(), cat == this.category && this.query.isEmpty(), cat);
         final Category target = cat;
         this.hits.add(new Hit(cx - 8.5F, by, 17.0F, 17.0F, () -> this.selectCategory(target), null, null));
         by += 20.0F;
      }

      if (this.category == Category.THEMES) {
         selectedY = this.y + this.h - 26.0F;
      }

      if (!this.categoryInit) {
         this.categoryY.setValue(selectedY);
         this.categoryY.setStartValue(selectedY);
         this.categoryInit = true;
      } else {
         this.categoryY.update(selectedY);
      }

      if (this.query.isEmpty()) {
         DrawUtil.drawRoundedRect(m, this.x + 1.5F, this.categoryY.getValue() + 4.0F, 2.0F, 9.0F, BorderRadius.all(1.0F), theme.getColor().withAlpha(255.0F * a));
      }

      float gy = this.y + this.h - 26.0F;
      this.categoryButton(ctx, m, theme, a, cx - 8.5F, gy, Category.THEMES.getIcon(), this.category == Category.THEMES && this.query.isEmpty(), "themes");
      this.hits.add(new Hit(cx - 8.5F, gy, 17.0F, 17.0F, () -> this.selectCategory(Category.THEMES), null, null));
   }

   private void categoryButton(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float bx, float by, String icon, boolean active, Object key) {
      float hover = this.hover(key, GuiUtil.isHovered(bx, by, 17.0, 17.0, this.mouseX, this.mouseY));
      float sel = this.hover("sel_" + key, active);
      ColorRGBA bg = new ColorRGBA(255, 255, 255, (10.0F * hover) * a).mix(theme.getColor().withAlpha(46.0F * a), sel);
      DrawUtil.drawRoundedRect(m, bx, by, 17.0F, 17.0F, BorderRadius.all(4.0F), bg);
      ColorRGBA iconColor = new ColorRGBA(150, 150, 160).mix(theme.getColor(), sel).withAlpha((0.62F + 0.38F * Math.max(sel, hover)) * 255.0F * a);
      float iw = Fonts.ICONS.getWidth(icon, 9.0F);
      ctx.drawText(Fonts.ICONS.getFont(9.0F), icon, bx + 8.5F - iw / 2.0F, by + 4.0F, iconColor);
   }

   private void renderList(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float listX) {
      String title = !this.query.isEmpty() ? "Поиск" : this.category.getName();
      ctx.drawText(Fonts.MEDIUM.getFont(9.0F), title, listX + 8.0F, this.y + 8.5F, new ColorRGBA(240, 240, 245, 255.0F * a));
      List<Module> modules = this.visibleModules();
      String count = String.valueOf(modules.size());
      float cw = Fonts.REGULAR.getWidth(count, 6.0F) + 6.0F;
      DrawUtil.drawRoundedRect(m, listX + LIST - 8.0F - cw, this.y + 7.5F, cw, 9.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, 12.0F * a));
      ctx.drawText(Fonts.REGULAR.getFont(6.0F), count, listX + LIST - 5.0F - cw, this.y + 10.0F, new ColorRGBA(200, 200, 210, 200.0F * a));

      float top = this.y + HEADER + 3.0F;
      float bottom = this.y + this.h - 4.0F;
      float viewH = bottom - top;
      float contentH = (float)modules.size() * 17.0F + 2.0F;
      this.listMax = Math.max(0.0F, contentH - viewH);
      this.listTarget = MathHelper.clamp(this.listTarget, -this.listMax, 0.0F);
      this.listScroll = lerp(this.listScroll, this.listTarget, 0.25F);
      ctx.enableScissor((int)listX, (int)top, (int)(listX + LIST), (int)bottom);
      float ry = top + 2.0F + this.listScroll;

      for (Module module : modules) {
         if (ry + 15.0F >= top && ry <= bottom) {
            this.moduleRow(ctx, m, theme, a, module, listX + 5.0F, ry, LIST - 10.0F, top, bottom);
         }

         ry += 17.0F;
      }

      ctx.disableScissor();
   }

   private void moduleRow(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, Module module, float rx, float ry, float rw, float top, float bottom) {
      boolean hovered = this.mouseY >= top && this.mouseY <= bottom && GuiUtil.isHovered(rx, ry, rw, 15.0, this.mouseX, this.mouseY);
      float hover = this.hover(module, hovered);
      float enabled = module.getAnimation().getValue();
      float sel = this.hover("msel_" + module.getName(), this.selected == module);
      ColorRGBA bg = new ColorRGBA(255, 255, 255, (6.0F * hover + 5.0F * sel) * a);
      DrawUtil.drawRoundedRect(m, rx, ry, rw, 15.0F, BorderRadius.all(3.0F), bg);
      if (enabled > 0.01F) {
         ColorRGBA acc1 = theme.getColor().withAlpha(52.0F * enabled * a);
         ColorRGBA acc2 = theme.getSecondColor().withAlpha(14.0F * enabled * a);
         DrawUtil.drawRoundedRect(m, rx, ry, rw, 15.0F, BorderRadius.all(3.0F), Gradient.of(acc1, acc1, acc2, acc2));
      }

      if (this.flash == module && System.currentTimeMillis() < this.flashUntil) {
         float f = Math.min(1.0F, (float)(this.flashUntil - System.currentTimeMillis()) / 400.0F);
         DrawUtil.drawRoundedRect(m, rx, ry, rw, 15.0F, BorderRadius.all(3.0F), theme.getColor().withAlpha(60.0F * f * a));
      }

      String name = module.getName();
      if (this.bindingModule == module) {
         name = "Бинд: ...";
      }

      ColorRGBA text = new ColorRGBA(200, 200, 210).mix(new ColorRGBA(255, 255, 255), enabled).withAlpha((0.6F + 0.3F * enabled + 0.1F * Math.max(sel, hover)) * 255.0F * a);
      MsdfRenderer.renderText(Fonts.REGULAR, name, 7.0F, text.getRGB(), ctx.getMatrices().peek().getPositionMatrix(), rx + 5.0F, ry + 4.5F, 0.0F, true, 0.72F, 1.0F, rw - 16.0F);
      if (module.getKeyCode() != -1 && this.bindingModule != module) {
         String key = shortKey(module.getKeyCode());
         float kw = Fonts.REGULAR.getWidth(key, 5.5F);
         ctx.drawText(Fonts.REGULAR.getFont(5.5F), key, rx + rw - 4.0F - kw, ry + 5.5F, new ColorRGBA(160, 160, 170, 160.0F * a));
      } else {
         float dot = 3.0F;
         DrawUtil.drawRoundedRect(m, rx + rw - 4.0F - dot, ry + 6.0F, dot, dot, BorderRadius.all(1.5F), new ColorRGBA(90, 90, 100).mix(theme.getColor(), enabled).withAlpha(255.0F * a));
      }

      this.hits.add(new Hit(rx, ry, rw, 15.0F, module::toggle, () -> this.select(module), () -> {
         this.bindingModule = this.bindingModule == module ? null : module;
         this.bindingKey = null;
      }).clip(top, bottom));
   }

   private void renderHeader(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float contentX) {
      float sx = contentX + 7.0F;
      float sy = this.y + 6.0F;
      float sw = 110.0F;
      boolean hovered = GuiUtil.isHovered(sx, sy, sw, 12.0, this.mouseX, this.mouseY);
      float hover = this.hover("search", hovered || this.searchFocus);
      DrawUtil.drawRoundedRect(m, sx, sy, sw, 12.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, (9.0F + 7.0F * hover) * a));
      if (this.searchFocus) {
         DrawUtil.drawRoundedBorder(m, sx, sy, sw, 12.0F, -0.5F, BorderRadius.all(3.0F), theme.getColor().withAlpha(120.0F * a));
      }

      ctx.drawText(Fonts.LUPA.getFont(5.0F), "", sx + 4.0F, sy + 3.75F, new ColorRGBA(220, 220, 230, 120.0F * a));
      String shown = this.query.isEmpty() && !this.searchFocus ? "Поиск модулей" : this.query;
      ColorRGBA tc = this.query.isEmpty() ? new ColorRGBA(200, 200, 210, 110.0F * a) : new ColorRGBA(240, 240, 245, 230.0F * a);
      MsdfRenderer.renderText(Fonts.REGULAR, shown, 6.5F, tc.getRGB(), ctx.getMatrices().peek().getPositionMatrix(), sx + 11.5F, sy + 3.5F, 0.0F, true, 0.8F, 1.0F, sw - 15.0F);
      if (this.searchFocus && System.currentTimeMillis() % 1000L < 520L) {
         float cx = sx + 11.5F + Math.min(sw - 16.0F, Fonts.REGULAR.getWidth(this.query, 6.5F)) + 0.5F;
         DrawUtil.drawRect(m, cx, sy + 3.0F, 0.6F, 6.0F, theme.getColor().withAlpha(255.0F * a));
      }

      this.hits.add(new Hit(sx, sy, sw, 12.0F, () -> this.searchFocus = true, () -> {
         this.query = "";
         this.searchFocus = true;
      }, null));
      String user = mc.getSession().getUsername();
      float right = this.x + this.w - 9.0F;
      float av = 13.0F;
      float ax = right - av;
      float ay = this.y + 5.5F;
      ColorRGBA c1 = theme.getColor().withAlpha(255.0F * a);
      ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
      DrawUtil.drawRoundedRect(m, ax, ay, av, av, BorderRadius.all(av / 2.0F), Gradient.of(c1, c2, c1, c2));
      String letter = user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase(Locale.ROOT);
      float lw = Fonts.MEDIUM.getWidth(letter, 7.0F);
      ctx.drawText(Fonts.MEDIUM.getFont(7.0F), letter, ax + av / 2.0F - lw / 2.0F, ay + 3.5F, new ColorRGBA(255, 255, 255, 255.0F * a));
      float uw = Fonts.MEDIUM.getWidth(user, 6.5F);
      ctx.drawText(Fonts.MEDIUM.getFont(6.5F), user, ax - 4.0F - uw, ay + 0.5F, new ColorRGBA(240, 240, 245, 240.0F * a));
      String sub = "Pain v1.2 · 1.21.4";
      float subW = Fonts.REGULAR.getWidth(sub, 5.5F);
      ctx.drawText(Fonts.REGULAR.getFont(5.5F), sub, ax - 4.0F - subW, ay + 7.5F, new ColorRGBA(200, 200, 210, 130.0F * a));
   }

   private void renderContent(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float contentX) {
      float px = contentX + 11.0F;
      float py = this.y + HEADER + 10.0F;
      float pw = this.x + this.w - 11.0F - px;
      float bottom = this.y + this.h - 6.0F;
      this.contentFade.update(1.0F);
      float fa = a * this.contentFade.getValue();
      if (this.category == Category.THEMES && this.query.isEmpty()) {
         this.renderThemes(ctx, m, theme, fa, px, py, pw);
      } else if (this.selected == null) {
         this.renderHome(ctx, m, theme, fa, px, py, pw);
      } else {
         this.renderSettings(ctx, m, theme, fa, px, py, pw, bottom);
      }
   }

   private void renderHome(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float px, float py, float pw) {
      ctx.drawText(Fonts.MEDIUM.getFont(12.0F), "Добро пожаловать", px, py, new ColorRGBA(245, 245, 250, 255.0F * a));
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), "Выбери модуль слева, чтобы открыть его настройки.", px, py + 15.0F, new ColorRGBA(200, 200, 210, 150.0F * a));
      float hy = py + 32.0F;
      String[][] hints = new String[][]{{"ЛКМ", "включить / выключить"}, {"ПКМ", "открыть настройки"}, {"СКМ", "назначить бинд (Del — сбросить)"}, {"Ввод", "поиск по названию, RU/EN раскладка"}};

      for (String[] hint : hints) {
         float kw = Fonts.MEDIUM.getWidth(hint[0], 6.5F) + 8.0F;
         DrawUtil.drawRoundedRect(m, px, hy, kw, 11.0F, BorderRadius.all(3.0F), theme.getColor().withAlpha(50.0F * a));
         ctx.drawText(Fonts.MEDIUM.getFont(6.5F), hint[0], px + 4.0F, hy + 3.0F, new ColorRGBA(255, 255, 255, 235.0F * a));
         ctx.drawText(Fonts.REGULAR.getFont(6.5F), hint[1], px + kw + 5.0F, hy + 3.0F, new ColorRGBA(210, 210, 220, 190.0F * a));
         hy += 14.0F;
      }

      hy += 10.0F;
      ctx.drawText(Fonts.MEDIUM.getFont(9.0F), "Ярлыки", px, hy, new ColorRGBA(240, 240, 245, 230.0F * a));
      hy += 13.0F;
      float tx = px;
      this.shortcut(ctx, m, theme, a, tx, hy, "5", "Боты (Swarm)", () -> this.openScreen(new SwarmScreen()));
      tx += 95.0F;
      this.shortcut(ctx, m, theme, a, tx, hy, "G", "Темы", () -> this.selectCategory(Category.THEMES));
      tx += 95.0F;
      if (tx + 90.0F > px + pw) {
         tx = px;
         hy += 22.0F;
      }

      this.shortcut(ctx, m, theme, a, tx, hy, "3", "Редактор HUD", () -> this.openScreen(new ChatScreen("")));
      int enabled = 0;

      for (Module module : Pain.getInstance().getModuleManager().getModules()) {
         if (module.isEnabled()) {
            ++enabled;
         }
      }

      String stat = "Включено модулей: " + enabled + " / " + Pain.getInstance().getModuleManager().getModules().size();
      ctx.drawText(Fonts.REGULAR.getFont(6.5F), stat, px, this.y + this.h - 18.0F, new ColorRGBA(200, 200, 210, 120.0F * a));
   }

   private void shortcut(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float sx, float sy, String icon, String text, Runnable action) {
      float hover = this.hover("sc_" + text, GuiUtil.isHovered(sx, sy, 90.0, 17.0, this.mouseX, this.mouseY));
      DrawUtil.drawRoundedRect(m, sx, sy, 90.0F, 17.0F, BorderRadius.all(4.0F), new ColorRGBA(255, 255, 255, (8.0F + 8.0F * hover) * a));
      ctx.drawText(Fonts.ICONS.getFont(7.0F), icon, sx + 6.0F, sy + 5.0F, new ColorRGBA(160, 160, 170).mix(theme.getColor(), hover).withAlpha((0.62F + 0.38F * hover) * 255.0F * a));
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), text, sx + 17.0F, sy + 5.5F, new ColorRGBA(235, 235, 240, (0.72F + 0.28F * hover) * 255.0F * a));
      this.hits.add(new Hit(sx, sy, 90.0F, 17.0F, action, null, null));
   }

   private void renderThemes(CustomDrawContext ctx, MatrixStack m, Theme current, float a, float px, float py, float pw) {
      ctx.drawText(Fonts.MEDIUM.getFont(12.0F), "Темы", px, py, new ColorRGBA(245, 245, 250, 255.0F * a));
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), "Цвет акцента меню и HUD", px, py + 15.0F, new ColorRGBA(200, 200, 210, 150.0F * a));
      float colW = (pw - 6.0F) / 2.0F;
      float ty = py + 30.0F;
      int i = 0;

      for (Theme t : Pain.getInstance().getThemeManager().getThemes()) {
         float tx = px + (float)(i % 2) * (colW + 6.0F);
         if (i > 0 && i % 2 == 0) {
            ty += 23.0F;
         }

         boolean active = t == current;
         float hover = this.hover(t, GuiUtil.isHovered(tx, ty, colW, 19.0, this.mouseX, this.mouseY));
         float act = this.hover("theme_" + t.getName(), active);
         DrawUtil.drawRoundedRect(m, tx, ty, colW, 19.0F, BorderRadius.all(5.0F), new ColorRGBA(255, 255, 255, (8.0F + 6.0F * hover) * a));
         if (act > 0.01F) {
            DrawUtil.drawRoundedBorder(m, tx, ty, colW, 19.0F, -0.5F, BorderRadius.all(5.0F), new ColorRGBA(t.getColor1()).withAlpha(200.0F * act * a));
         }

         ColorRGBA a1 = new ColorRGBA(t.getColor1()).withAlpha(255.0F * a);
         ColorRGBA a2 = new ColorRGBA(t.getColor2()).withAlpha(255.0F * a);
         DrawUtil.drawRoundedRect(m, tx + 5.0F, ty + 4.5F, 20.0F, 10.0F, BorderRadius.all(3.0F), Gradient.of(a1, a1, a2, a2));
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), t.getName(), tx + 31.0F, ty + 6.5F, new ColorRGBA(235, 235, 240, (0.7F + 0.3F * Math.max(act, hover)) * 255.0F * a));
         final Theme target = t;
         boolean isCustom = t == Pain.getInstance().getThemeManager().getCustomTheme();
         this.hits.add(new Hit(tx, ty, colW, 19.0F, () -> {
            Theme cur = Pain.getInstance().getThemeManager().getCurrentTheme();
            if (cur != target) {
               target.getAnimation().setValue(0.0F);
               target.startAnimation(cur.getColor1(), cur.getColor2());
               Pain.getInstance().getThemeManager().setCurrentTheme(target);
            }
         }, null, null));
         if (isCustom) {
            // у своей темы два кружка — открывают колорпикер
            for (int k = 0; k < 2; ++k) {
               final int which = k;
               float bx = tx + colW - 30.0F + (float)k * 13.0F;
               ColorRGBA sw = new ColorRGBA(k == 0 ? t.getColor1() : t.getColor2());
               DrawUtil.drawRoundedRect(m, bx, ty + 4.5F, 10.0F, 10.0F, BorderRadius.all(5.0F), sw.withAlpha(255.0F * a));
               DrawUtil.drawRoundedBorder(m, bx, ty + 4.5F, 10.0F, 10.0F, -0.5F, BorderRadius.all(5.0F), new ColorRGBA(255, 255, 255, 120.0F * a));
               final float openX = bx;
               final float openY = ty + 18.0F;
               this.hits.add(new Hit(bx - 1.0F, ty + 3.5F, 12.0F, 12.0F, () -> {
                  tech.pain.base.theme.ThemeManager tm = Pain.getInstance().getThemeManager();
                  tm.setCurrentTheme(target);
                  this.openPicker(which == 0 ? "Основной цвет" : "Второй цвет", openX, openY, () -> new ColorRGBA(which == 0 ? target.getColor1() : target.getColor2()).withAlpha(255), c -> {
                     int rgb = c.withAlpha(255).getRGB();
                     tm.setCustomColors(which == 0 ? rgb : target.getColor1(), which == 0 ? target.getColor2() : rgb);
                  }, false);
               }, null, null));
            }
         }

         ++i;
      }
   }

   private float pickerHeight() {
      return this.pickHasAlpha ? 132.0F : 121.0F;
   }

   private void openPicker(String title, float px, float py, java.util.function.Supplier<ColorRGBA> get, java.util.function.Consumer<ColorRGBA> set, boolean alpha) {
      ColorRGBA c = get.get();
      this.pickGet = get;
      this.pickSet = set;
      this.pickTitle = title;
      this.pickHasAlpha = alpha;
      this.pickHue = c.getHue();
      this.pickSat = c.getSaturation();
      this.pickVal = c.getBrightness();
      this.pickAlpha = (float)c.getAlpha() / 255.0F;
      this.pickX = MathHelper.clamp(px - PICK_W + 20.0F, this.x + 4.0F, this.x + this.w - PICK_W - 4.0F);
      this.pickY = MathHelper.clamp(py, this.y + 4.0F, this.y + this.h - this.pickerHeight() - 4.0F);
      this.pickDrag = 0;
   }

   private void applyPicker() {
      if (this.pickSet != null) {
         ColorRGBA c = ColorRGBA.fromHSB(Math.min(0.999F, this.pickHue), this.pickSat, this.pickVal);
         this.pickSet.accept(new ColorRGBA(c.getRed(), c.getGreen(), c.getBlue(), this.pickHasAlpha ? (int)(this.pickAlpha * 255.0F) : 255));
      }
   }

   /** Колорпикер: квадрат насыщенность/яркость, полоса оттенка, прозрачность, пресеты. */
   private void renderPicker(CustomDrawContext ctx, MatrixStack m, float a) {
      if (this.pickSet == null) {
         return;
      }

      float px = this.pickX;
      float py = this.pickY;
      float ph = this.pickerHeight();
      float sx = px + 7.0F;
      float sy = py + 17.0F;
      float sw = PICK_W - 14.0F;
      float sh = 62.0F;
      float hy = sy + sh + 6.0F;
      float ay = hy + 12.0F;
      if (this.pickDrag == 1) {
         this.pickSat = MathHelper.clamp((this.mouseX - sx) / sw, 0.0F, 1.0F);
         this.pickVal = 1.0F - MathHelper.clamp((this.mouseY - sy) / sh, 0.0F, 1.0F);
         this.applyPicker();
      } else if (this.pickDrag == 2) {
         this.pickHue = MathHelper.clamp((this.mouseX - sx) / sw, 0.0F, 1.0F);
         this.applyPicker();
      } else if (this.pickDrag == 3) {
         this.pickAlpha = MathHelper.clamp((this.mouseX - sx) / sw, 0.0F, 1.0F);
         this.applyPicker();
      }

      ColorRGBA current = this.pickGet.get();
      DrawUtil.drawShadow(m, px, py, PICK_W, ph, 16.0F, BorderRadius.all(8.0F), new ColorRGBA(0, 0, 0, 150.0F * a));
      DrawUtil.drawRoundedRect(m, px, py, PICK_W, ph, BorderRadius.all(8.0F), new ColorRGBA(18, 18, 24, 250.0F * a));
      DrawUtil.drawRoundedBorder(m, px, py, PICK_W, ph, -0.5F, BorderRadius.all(8.0F), new ColorRGBA(255, 255, 255, 30.0F * a));
      this.hits.add(new Hit(px, py, PICK_W, ph, () -> {
      }, () -> {
      }, () -> {
      }));
      MsdfRenderer.renderText(Fonts.MEDIUM, this.pickTitle, 6.5F, new ColorRGBA(240, 240, 245, 240.0F * a).getRGB(), m.peek().getPositionMatrix(), sx, py + 6.0F, 0.0F, true, 0.8F, 1.0F, 62.0F);
      String hex = String.format("#%02X%02X%02X", current.getRed(), current.getGreen(), current.getBlue());
      float hw = Fonts.REGULAR.getWidth(hex, 6.0F);
      ctx.drawText(Fonts.REGULAR.getFont(6.0F), hex, px + PICK_W - 7.0F - hw, py + 6.5F, new ColorRGBA(200, 200, 210, 180.0F * a));
      ColorRGBA hue = ColorRGBA.fromHSB(Math.min(0.999F, this.pickHue), 1.0F, 1.0F).withAlpha(255.0F * a);
      ColorRGBA white = new ColorRGBA(255, 255, 255, 255.0F * a);
      DrawUtil.drawRoundedRect(m, sx, sy, sw, sh, BorderRadius.all(4.0F), Gradient.of(white, white, hue, hue));
      ColorRGBA clear = new ColorRGBA(0, 0, 0, 0);
      ColorRGBA black = new ColorRGBA(0, 0, 0, 255.0F * a);
      DrawUtil.drawRoundedRect(m, sx, sy, sw, sh, BorderRadius.all(4.0F), Gradient.of(clear, black, clear, black));
      float kx = sx + this.pickSat * sw;
      float ky = sy + (1.0F - this.pickVal) * sh;
      DrawUtil.drawRoundedRect(m, kx - 3.5F, ky - 3.5F, 7.0F, 7.0F, BorderRadius.all(3.5F), white);
      DrawUtil.drawRoundedRect(m, kx - 2.5F, ky - 2.5F, 5.0F, 5.0F, BorderRadius.all(2.5F), new ColorRGBA(current.getRed(), current.getGreen(), current.getBlue(), 255.0F * a));
      this.hits.add(new Hit(sx - 2.0F, sy - 2.0F, sw + 4.0F, sh + 4.0F, () -> this.pickDrag = 1, null, null));
      float seg = sw / 6.0F;

      for (int i = 0; i < 6; ++i) {
         ColorRGBA l = ColorRGBA.fromHSB((float)i / 6.0F, 1.0F, 1.0F).withAlpha(255.0F * a);
         ColorRGBA r = ColorRGBA.fromHSB(Math.min(0.999F, (float)(i + 1) / 6.0F), 1.0F, 1.0F).withAlpha(255.0F * a);
         BorderRadius br = i == 0 ? new BorderRadius(3.0F, 0.0F, 0.0F, 3.0F) : (i == 5 ? new BorderRadius(0.0F, 3.0F, 3.0F, 0.0F) : BorderRadius.ZERO);
         DrawUtil.drawRoundedRect(m, sx + (float)i * seg, hy, seg + 0.3F, 6.0F, br, Gradient.of(l, l, r, r));
      }

      DrawUtil.drawRoundedRect(m, sx + this.pickHue * sw - 1.5F, hy - 1.0F, 3.0F, 8.0F, BorderRadius.all(1.5F), white);
      this.hits.add(new Hit(sx - 2.0F, hy - 2.0F, sw + 4.0F, 10.0F, () -> this.pickDrag = 2, null, null));
      float presetY = ay;
      if (this.pickHasAlpha) {
         ColorRGBA solid = new ColorRGBA(current.getRed(), current.getGreen(), current.getBlue(), 255.0F * a);
         DrawUtil.drawRoundedRect(m, sx, ay, sw, 6.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, 18.0F * a));
         DrawUtil.drawRoundedRect(m, sx, ay, sw, 6.0F, BorderRadius.all(3.0F), Gradient.of(solid.withAlpha(0), solid.withAlpha(0), solid, solid));
         DrawUtil.drawRoundedRect(m, sx + this.pickAlpha * sw - 1.5F, ay - 1.0F, 3.0F, 8.0F, BorderRadius.all(1.5F), white);
         this.hits.add(new Hit(sx - 2.0F, ay - 2.0F, sw + 4.0F, 10.0F, () -> this.pickDrag = 3, null, null));
         presetY = ay + 11.0F;
      }

      Theme theme = theme();
      ColorRGBA[] presets = new ColorRGBA[]{theme.getColor(), theme.getSecondColor(), new ColorRGBA(255, 255, 255), new ColorRGBA(255, 70, 70), new ColorRGBA(255, 150, 50), new ColorRGBA(255, 220, 70), new ColorRGBA(80, 230, 120), new ColorRGBA(70, 210, 255), new ColorRGBA(90, 120, 255), new ColorRGBA(180, 90, 255), new ColorRGBA(255, 110, 190)};
      float gap = (sw - 8.0F) / (float)(presets.length - 1);

      for (int i = 0; i < presets.length; ++i) {
         final ColorRGBA preset = presets[i];
         float cx = sx + (float)i * gap;
         float hover = this.hover("preset_" + i, GuiUtil.isHovered(cx - 1.0F, presetY - 1.0F, 10.0, 10.0, this.mouseX, this.mouseY));
         float size = 8.0F + hover * 1.5F;
         DrawUtil.drawRoundedRect(m, cx + 4.0F - size / 2.0F, presetY + 4.0F - size / 2.0F, size, size, BorderRadius.all(size / 2.0F), preset.withAlpha(255.0F * a));
         this.hits.add(new Hit(cx - 1.0F, presetY - 1.0F, 10.0F, 10.0F, () -> {
            this.pickHue = preset.getHue();
            this.pickSat = preset.getSaturation();
            this.pickVal = preset.getBrightness();
            this.applyPicker();
         }, null, null));
      }
   }

   // ---------------------------------------------------------------- settings

   private void renderSettings(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float px, float py, float pw, float bottom) {
      Module module = this.selected;
      ctx.drawText(Fonts.MEDIUM.getFont(12.0F), module.getName(), px, py, new ColorRGBA(245, 245, 250, 255.0F * a));
      float nx = px + Fonts.MEDIUM.getWidth(module.getName(), 12.0F) + 6.0F;
      String bind = this.bindingModule == module ? "Нажмите клавишу..." : (module.getKeyCode() == -1 ? "Без бинда" : "Бинд: " + shortKey(module.getKeyCode()));
      float bw = Fonts.REGULAR.getWidth(bind, 6.0F) + 8.0F;
      float bHover = this.hover("bindchip", GuiUtil.isHovered(nx, py + 0.5F, bw, 10.0, this.mouseX, this.mouseY));
      DrawUtil.drawRoundedRect(m, nx, py + 0.5F, bw, 10.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, (10.0F + 8.0F * bHover) * a));
      ctx.drawText(Fonts.REGULAR.getFont(6.0F), bind, nx + 4.0F, py + 3.5F, new ColorRGBA(220, 220, 230, 210.0F * a));
      this.hits.add(new Hit(nx, py + 0.5F, bw, 10.0F, () -> {
         this.bindingModule = this.bindingModule == module ? null : module;
         this.bindingKey = null;
      }, () -> module.setKeyCode(-1), null));
      float tx = nx + bw + 4.0F;
      float tw = 26.0F;
      float on = module.getAnimation().getValue();
      this.toggle(m, theme, a, tx + 2.0F, py + 1.0F, on);
      this.hits.add(new Hit(tx, py, tw, 11.0F, module::toggle, null, null));
      float cx = px + pw - 12.0F;
      float cHover = this.hover("close", GuiUtil.isHovered(cx - 2.0F, py - 2.0F, 14.0, 14.0, this.mouseX, this.mouseY));
      DrawUtil.drawRoundedRect(m, cx - 2.0F, py - 2.0F, 14.0F, 14.0F, BorderRadius.all(4.0F), new ColorRGBA(255, 255, 255, 12.0F * cHover * a));
      ctx.drawText(Fonts.MEDIUM.getFont(9.0F), "×", cx + 1.5F, py + 0.5F, new ColorRGBA(230, 230, 240, (0.7F + 0.3F * cHover) * 255.0F * a));
      this.hits.add(new Hit(cx - 2.0F, py - 2.0F, 14.0F, 14.0F, () -> this.select(null), null, null));
      String desc = module.getInfo() == null ? "" : module.getInfo().description();
      MsdfRenderer.renderText(Fonts.REGULAR, desc.isEmpty() ? "Без описания" : desc, 7.0F, new ColorRGBA(200, 200, 210, 150.0F * a).getRGB(), ctx.getMatrices().peek().getPositionMatrix(), px, py + 15.0F, 0.0F, true, 0.85F, 1.0F, pw - 4.0F);
      float top = py + 27.0F;
      DrawUtil.drawRect(m, px, top - 3.0F, pw, 0.5F, new ColorRGBA(255, 255, 255, 12.0F * a));
      List<Setting> settings = new ArrayList<>();

      for (Setting s : module.getSettings()) {
         if (s.isVisible()) {
            settings.add(s);
         }
      }

      if (settings.isEmpty()) {
         ctx.drawText(Fonts.REGULAR.getFont(8.0F), "У этого модуля нет настроек", px, top + 8.0F, new ColorRGBA(200, 200, 210, 140.0F * a));
         return;
      }

      float colW = (pw - 5.0F) / 2.0F;
      float[] colH = new float[]{0.0F, 0.0F};
      float viewH = bottom - top;
      this.setTarget = MathHelper.clamp(this.setTarget, -this.setMax, 0.0F);
      this.setScroll = lerp(this.setScroll, this.setTarget, 0.25F);
      ctx.enableScissor((int)px - 2, (int)top, (int)(px + pw + 2.0F), (int)bottom);

      for (Setting s : settings) {
         int col = colH[0] <= colH[1] ? 0 : 1;
         float cardX = px + (float)col * (colW + 5.0F);
         float cardY = top + colH[col] + this.setScroll;
         float ch = this.cardHeight(s, colW);
         if (cardY + ch >= top && cardY <= bottom) {
            this.card(ctx, m, theme, a, s, cardX, cardY, colW, ch, top, bottom);
         }

         colH[col] += ch + 5.0F;
      }

      ctx.disableScissor();
      this.setMax = Math.max(0.0F, Math.max(colH[0], colH[1]) - viewH);
   }

   private float cardHeight(Setting s, float cw) {
      if (s instanceof NumberSetting) {
         return 29.0F;
      } else if (s instanceof ModeSetting mode) {
         List<String> names = new ArrayList<>();
         mode.getValues().forEach(v -> names.add(v.getName()));
         return 20.0F + this.chipsHeight(names, cw - 12.0F) + 6.0F;
      } else if (s instanceof MultiBooleanSetting multi) {
         List<String> names = new ArrayList<>();
         multi.getBooleanSettings().forEach(v -> names.add(v.getName()));
         return 20.0F + this.chipsHeight(names, cw - 12.0F) + 6.0F;
      } else {
         return 21.0F;
      }
   }

   private float chipsHeight(List<String> names, float maxW) {
      float cx = 0.0F;
      int rows = 1;

      for (String n : names) {
         float w = Fonts.REGULAR.getWidth(n, 6.0F) + 9.0F;
         if (cx > 0.0F && cx + w > maxW) {
            cx = 0.0F;
            ++rows;
         }

         cx += w + 3.0F;
      }

      return (float)rows * 14.0F - 3.0F;
   }

   private void card(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, Setting s, float cx, float cy, float cw, float ch, float top, float bottom) {
      boolean inView = this.mouseY >= top && this.mouseY <= bottom;
      float hover = this.hover(s, inView && GuiUtil.isHovered(cx, cy, cw, ch, this.mouseX, this.mouseY));
      DrawUtil.drawRoundedRect(m, cx, cy, cw, ch, BorderRadius.all(7.0F), new ColorRGBA(255, 255, 255, (8.0F + 4.0F * hover) * a));
      ColorRGBA text = new ColorRGBA(235, 235, 240, 235.0F * a);
      ColorRGBA dim = new ColorRGBA(200, 200, 210, 150.0F * a);
      float tx = cx + 7.0F;
      float maxName = cw - 14.0F;
      if (s instanceof BooleanSetting bool) {
         MsdfRenderer.renderText(Fonts.REGULAR, s.getName(), 7.0F, text.getRGB(), m.peek().getPositionMatrix(), tx, cy + 7.5F, 0.0F, true, 0.8F, 1.0F, maxName - 24.0F);
         this.toggle(m, theme, a, cx + cw - 25.0F, cy + 6.0F, bool.getAnimation().getValue());
         this.hits.add(new Hit(cx, cy, cw, ch, () -> bool.setEnabled(!bool.isEnabled()), null, null).clip(top, bottom));
      } else if (s instanceof NumberSetting num) {
         float range = num.getMax() - num.getMin();
         float pct = range <= 0.0F ? 0.0F : (num.getCurrent() - num.getMin()) / range;
         float trackX = cx + 7.0F;
         float trackW = cw - 14.0F;
         if (this.drag == num) {
            this.dragX = trackX;
            this.dragW = trackW;
            this.applyDrag();
            pct = range <= 0.0F ? 0.0F : (num.getCurrent() - num.getMin()) / range;
         }

         String value = formatNumber(num);
         float vw = Fonts.MEDIUM.getWidth(value, 6.5F);
         MsdfRenderer.renderText(Fonts.REGULAR, s.getName(), 7.0F, text.getRGB(), m.peek().getPositionMatrix(), tx, cy + 6.5F, 0.0F, true, 0.8F, 1.0F, maxName - vw - 6.0F);
         ctx.drawText(Fonts.MEDIUM.getFont(6.5F), value, cx + cw - 7.0F - vw, cy + 7.0F, theme.getColor().mix(new ColorRGBA(255, 255, 255), 0.35F).withAlpha(255.0F * a));
         float anim = this.smooth("num_" + System.identityHashCode(num), pct);
         DrawUtil.drawRoundedRect(m, trackX, cy + 19.0F, trackW, 3.0F, BorderRadius.all(1.5F), new ColorRGBA(255, 255, 255, 22.0F * a));
         ColorRGBA c1 = theme.getColor().withAlpha(255.0F * a);
         ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
         DrawUtil.drawRoundedRect(m, trackX, cy + 19.0F, Math.max(3.0F, trackW * anim), 3.0F, BorderRadius.all(1.5F), Gradient.of(c2, c2, c1, c1));
         float kx = trackX + trackW * anim;
         DrawUtil.drawShadow(m, kx - 3.5F, cy + 17.0F, 7.0F, 7.0F, 6.0F, BorderRadius.all(3.5F), theme.getColor().withAlpha(90.0F * a));
         DrawUtil.drawRoundedRect(m, kx - 3.5F, cy + 17.0F, 7.0F, 7.0F, BorderRadius.all(3.5F), new ColorRGBA(255, 255, 255, 255.0F * a));
         this.hits.add(new Hit(cx, cy + 13.0F, cw, 14.0F, () -> {
            this.drag = num;
            this.dragX = trackX;
            this.dragW = trackW;
            this.applyDrag();
         }, null, null).clip(top, bottom));
      } else if (s instanceof ModeSetting mode) {
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), s.getName(), tx, cy + 6.5F, text);
         float chx = tx;
         float chy = cy + 18.0F;

         for (ModeSetting.Value v : mode.getValues()) {
            float w = Fonts.REGULAR.getWidth(v.getName(), 6.0F) + 9.0F;
            if (chx > tx && chx + w > cx + cw - 5.0F) {
               chx = tx;
               chy += 14.0F;
            }

            this.chip(ctx, m, theme, a, chx, chy, w, v.getName(), v.getAnimation().getValue(), top, bottom);
            this.hits.add(new Hit(chx, chy, w, 11.0F, () -> mode.setValue(v), null, null).clip(top, bottom));
            chx += w + 3.0F;
         }
      } else if (s instanceof MultiBooleanSetting multi) {
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), s.getName(), tx, cy + 6.5F, text);
         String count = multi.getSelectedNames().size() + "/" + multi.getBooleanSettings().size();
         float cntW = Fonts.REGULAR.getWidth(count, 6.0F);
         ctx.drawText(Fonts.REGULAR.getFont(6.0F), count, cx + cw - 7.0F - cntW, cy + 7.0F, dim);
         float chx = tx;
         float chy = cy + 18.0F;

         for (MultiBooleanSetting.Value v : multi.getBooleanSettings()) {
            float w = Fonts.REGULAR.getWidth(v.getName(), 6.0F) + 9.0F;
            if (chx > tx && chx + w > cx + cw - 5.0F) {
               chx = tx;
               chy += 14.0F;
            }

            this.chip(ctx, m, theme, a, chx, chy, w, v.getName(), v.getAnimation().getValue(), top, bottom);
            this.hits.add(new Hit(chx, chy, w, 11.0F, () -> v.setEnabled(!v.isEnabled()), null, null).clip(top, bottom));
            chx += w + 3.0F;
         }
      } else if (s instanceof KeySetting key) {
         MsdfRenderer.renderText(Fonts.REGULAR, s.getName(), 7.0F, text.getRGB(), m.peek().getPositionMatrix(), tx, cy + 7.5F, 0.0F, true, 0.8F, 1.0F, maxName - 40.0F);
         String k = this.bindingKey == key ? "..." : (key.getKeyCode() == -1 ? "Нет" : shortKey(key.getKeyCode()));
         float kw = Fonts.REGULAR.getWidth(k, 6.0F) + 8.0F;
         float kx = cx + cw - 7.0F - kw;
         DrawUtil.drawRoundedRect(m, kx, cy + 5.0F, kw, 11.0F, BorderRadius.all(3.0F), this.bindingKey == key ? theme.getColor().withAlpha(120.0F * a) : new ColorRGBA(255, 255, 255, 16.0F * a));
         ctx.drawText(Fonts.REGULAR.getFont(6.0F), k, kx + 4.0F, cy + 8.5F, text);
         this.hits.add(new Hit(cx, cy, cw, ch, () -> {
            this.bindingKey = this.bindingKey == key ? null : key;
            this.bindingModule = null;
         }, () -> key.setKeyCode(-1), null).clip(top, bottom));
      } else if (s instanceof ButtonSetting button) {
         float bw = Fonts.MEDIUM.getWidth(s.getName(), 7.0F);
         DrawUtil.drawRoundedRect(m, cx, cy, cw, ch, BorderRadius.all(7.0F), theme.getColor().withAlpha((30.0F + 40.0F * hover) * a));
         ctx.drawText(Fonts.MEDIUM.getFont(7.0F), s.getName(), cx + cw / 2.0F - bw / 2.0F, cy + 7.5F, new ColorRGBA(255, 255, 255, 240.0F * a));
         this.hits.add(new Hit(cx, cy, cw, ch, () -> {
            if (button.getRunnable() != null) {
               button.getRunnable().run();
            }
         }, null, null).clip(top, bottom));
      } else if (s instanceof ColorSetting color) {
         MsdfRenderer.renderText(Fonts.REGULAR, s.getName(), 7.0F, text.getRGB(), m.peek().getPositionMatrix(), tx, cy + 7.5F, 0.0F, true, 0.8F, 1.0F, maxName - 24.0F);
         ColorRGBA c = color.getColor();
         DrawUtil.drawRoundedRect(m, cx + cw - 25.0F, cy + 5.5F, 18.0F, 10.0F, BorderRadius.all(3.0F), c.withAlpha((float)c.getAlpha() * a));
         DrawUtil.drawRoundedBorder(m, cx + cw - 25.0F, cy + 5.5F, 18.0F, 10.0F, -0.5F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, 40.0F * a));
         final float openX = cx + cw - 25.0F;
         final float openY = cy + 18.0F;
         this.hits.add(new Hit(cx, cy, cw, ch, () -> this.openPicker(s.getName(), openX, openY, color::getColor, color::setColor, true), () -> color.setColor(theme.getColor()), null).clip(top, bottom));
      } else if (s instanceof ItemSelectSetting items) {
         MsdfRenderer.renderText(Fonts.REGULAR, s.getName(), 7.0F, text.getRGB(), m.peek().getPositionMatrix(), tx, cy + 7.5F, 0.0F, true, 0.8F, 1.0F, maxName - 30.0F);
         String cnt = String.valueOf(items.getItemsById().size());
         float cntW = Fonts.REGULAR.getWidth(cnt, 6.5F);
         ctx.drawText(Fonts.REGULAR.getFont(6.5F), cnt, cx + cw - 7.0F - cntW, cy + 8.0F, dim);
      } else {
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), s.getName(), tx, cy + 7.5F, text);
      }
   }

   private void chip(CustomDrawContext ctx, MatrixStack m, Theme theme, float a, float chx, float chy, float w, String name, float sel, float top, float bottom) {
      float hover = this.hover("chip_" + name + "_" + (int)chx + "_" + (int)chy, this.mouseY >= top && this.mouseY <= bottom && GuiUtil.isHovered(chx, chy, w, 11.0, this.mouseX, this.mouseY));
      ColorRGBA bg = new ColorRGBA(255, 255, 255, (12.0F + 8.0F * hover) * a).mix(theme.getColor().withAlpha(190.0F * a), sel);
      DrawUtil.drawRoundedRect(m, chx, chy, w, 11.0F, BorderRadius.all(3.5F), bg);
      ctx.drawText(Fonts.REGULAR.getFont(6.0F), name, chx + 4.5F, chy + 3.25F, new ColorRGBA(210, 210, 220).mix(new ColorRGBA(255, 255, 255), sel).withAlpha((0.7F + 0.3F * Math.max(sel, hover)) * 255.0F * a));
   }

   private void toggle(MatrixStack m, Theme theme, float a, float tx, float ty, float on) {
      ColorRGBA off = new ColorRGBA(255, 255, 255, 30.0F * a);
      ColorRGBA c1 = theme.getColor().withAlpha(255.0F * a);
      ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
      DrawUtil.drawRoundedRect(m, tx, ty, 18.0F, 9.0F, BorderRadius.all(4.5F), off);
      if (on > 0.01F) {
         DrawUtil.drawRoundedRect(m, tx, ty, 18.0F, 9.0F, BorderRadius.all(4.5F), Gradient.of(c1.mulAlpha(on), c1.mulAlpha(on), c2.mulAlpha(on), c2.mulAlpha(on)));
      }

      float kx = tx + 1.5F + 9.0F * on;
      DrawUtil.drawRoundedRect(m, kx, ty + 1.5F, 6.0F, 6.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, (190.0F + 65.0F * on) * a));
   }

   // ------------------------------------------------------------------ helpers

   private List<Module> visibleModules() {
      List<Module> out = new ArrayList<>();
      String q = this.query.toLowerCase(Locale.ROOT);
      String qq = ReplaceUtil.toQwerty(this.query).toLowerCase(Locale.ROOT);

      for (Module module : Pain.getInstance().getModuleManager().getModules()) {
         String name = module.getName().toLowerCase(Locale.ROOT);
         if (q.isEmpty()) {
            if (module.getCategory() == this.category) {
               out.add(module);
            }
         } else if (name.contains(q) || name.contains(qq)) {
            out.add(module);
         }
      }

      if (!q.isEmpty()) {
         out.sort(Comparator.<Module>comparingInt(mod -> mod.getName().toLowerCase(Locale.ROOT).startsWith(q) || mod.getName().toLowerCase(Locale.ROOT).startsWith(qq) ? 0 : 1).thenComparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
      } else {
         out.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
      }

      return out;
   }

   private void selectCategory(Category cat) {
      if (this.category != cat || !this.query.isEmpty()) {
         this.category = cat;
         this.query = "";
         this.searchFocus = false;
         this.selected = null;
         this.listTarget = 0.0F;
         this.listScroll = 0.0F;
         this.resetFade();
      }
   }

   private void select(Module module) {
      if (this.selected != module) {
         this.selected = module;
         this.setTarget = 0.0F;
         this.setScroll = 0.0F;
         this.resetFade();
      }
   }

   private void resetFade() {
      this.contentFade.setValue(0.0F);
      this.contentFade.setStartValue(0.0F);
   }

   private void onQueryChanged() {
      this.listTarget = 0.0F;
      List<Module> found = this.visibleModules();
      if (!this.query.isEmpty() && !found.isEmpty()) {
         Module first = found.get(0);
         if (this.selected != first) {
            this.select(first);
            this.flash = first;
            this.flashUntil = System.currentTimeMillis() + 1600L;
         }
      }
   }

   private void openScreen(Screen screen) {
      if (this.cinema && this.cinemaClose != null) {
         this.cinemaClose.run();
         mc.setScreen(screen);
         return;
      }

      this.closing = false;
      Menu.INSTANCE.setEnabled(false);
      mc.setScreen(screen);
   }

   private void applyDrag() {
      if (this.drag != null && this.dragW > 0.0F) {
         float pct = MathHelper.clamp((this.mouseX - this.dragX) / this.dragW, 0.0F, 1.0F);
         float min = this.drag.getMin();
         float max = this.drag.getMax();
         float inc = this.drag.getIncrement();
         float value = min + pct * (max - min);
         if (inc > 0.0F) {
            value = (float)Math.round(value / inc) * inc;
         }

         this.drag.setCurrent(MathHelper.clamp(value, min, max));
      }
   }

   private float hover(Object key, boolean state) {
      return this.smooth(key, state ? 1.0F : 0.0F);
   }

   private float smooth(Object key, float target) {
      float v = this.hovers.getOrDefault(key, target);
      v = lerp(v, target, 0.22F);
      if (Math.abs(v - target) < 0.002F) {
         v = target;
      }

      this.hovers.put(key, v);
      return v;
   }

   private static float lerp(float from, float to, float t) {
      return from + (to - from) * t;
   }

   private static Theme theme() {
      return Pain.getInstance().getThemeManager().getCurrentTheme();
   }

   private static String shortKey(int keyCode) {
      String name = Keyboard.getKeyName(keyCode);
      return name == null ? "?" : name;
   }

   private static String formatNumber(NumberSetting num) {
      String inc = String.valueOf(num.getIncrement());
      int scale = inc.contains(".") ? inc.split("\\.")[1].length() : 0;
      if (inc.endsWith(".0")) {
         scale = 0;
      }

      return String.format(Locale.US, "%." + scale + "f", num.getCurrent());
   }

   // ------------------------------------------------------------------- input

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (this.closing) {
         return true;
      }

      if (this.bindingModule != null) {
         if (button == 0 || button == 1) {
            this.bindingModule = null;
         } else {
            this.bindingModule.setKeyCode(button);
            this.bindingModule = null;
            return true;
         }
      }

      if (this.bindingKey != null) {
         if (button != 0) {
            this.bindingKey.setKeyCode(button);
            this.bindingKey = null;
            return true;
         }

         this.bindingKey = null;
      }

      if (this.pickSet != null && !GuiUtil.isHovered(this.pickX, this.pickY, PICK_W, this.pickerHeight(), this.mouseX, this.mouseY)) {
         this.pickSet = null;
         this.pickGet = null;
         this.pickDrag = 0;
         return true;
      }

      boolean wasSearch = this.searchFocus;
      this.searchFocus = false;

      for (int i = this.hits.size() - 1; i >= 0; --i) {
         Hit hit = this.hits.get(i);
         if (hit.contains(this.mouseX, this.mouseY)) {
            Runnable action = button == 0 ? hit.left : (button == 1 ? hit.right : (button == 2 ? hit.middle : null));
            if (action != null) {
               action.run();
               return true;
            }
         }
      }

      if (wasSearch && !GuiUtil.isHovered(this.x, this.y, this.w, this.h, this.mouseX, this.mouseY)) {
         this.searchFocus = false;
      }

      return true;
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      this.drag = null;
      this.pickDrag = 0;
      return true;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
      float contentX = this.x + RAIL + LIST;
      if (this.mouseX < contentX) {
         this.listTarget += (float)vertical * 22.0F;
      } else {
         this.setTarget += (float)vertical * 26.0F;
      }

      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.bindingModule != null) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.bindingModule = null;
         } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            this.bindingModule.setKeyCode(-1);
            this.bindingModule = null;
         } else {
            this.bindingModule.setKeyCode(keyCode);
            this.bindingModule = null;
         }

         return true;
      }

      if (this.bindingKey != null) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.bindingKey = null;
         } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            this.bindingKey.setKeyCode(-1);
            this.bindingKey = null;
         } else {
            this.bindingKey.setKeyCode(keyCode);
            this.bindingKey = null;
         }

         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.pickSet != null) {
         this.pickSet = null;
         this.pickGet = null;
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         if (this.searchFocus) {
            this.searchFocus = false;
            return true;
         }

         this.startClosing();
         return true;
      }

      if (this.searchFocus) {
         if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.searchFocus = false;
         } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!this.query.isEmpty()) {
               this.query = Screen.hasControlDown() ? "" : this.query.substring(0, this.query.length() - 1);
               this.onQueryChanged();
            }
         } else if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            String clip = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
            if (clip != null) {
               this.query = (this.query + clip.replace("\n", "")).substring(0, Math.min(32, this.query.length() + clip.length()));
               this.onQueryChanged();
            }
         } else if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_A) {
            this.query = "";
            this.onQueryChanged();
         }

         return true;
      }

      if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
         this.searchFocus = true;
         return true;
      }

      if (!this.cinema && Menu.INSTANCE.getKeyCode() == keyCode && System.currentTimeMillis() - this.openedAt > 400L) {
         this.startClosing();
         return true;
      }

      return false;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (this.searchFocus && chr >= ' ' && this.query.length() < 32) {
         this.query = this.query + chr;
         this.onQueryChanged();
         return true;
      }

      return false;
   }

   private void renderGhost() {
      CustomDrawContext ctx = new CustomDrawContext(mc.getBufferBuilders().getEntityVertexConsumers());

      try {
         this.renderMenu(ctx);
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         ctx.draw();
      }
   }

   public void reopen() {
      this.openedAt = System.currentTimeMillis();
      this.closing = false;
      this.needToClose = false;
   }

   private void startClosing() {
      if (this.cinema) {
         this.searchFocus = false;
         this.drag = null;
         if (this.cinemaClose != null) {
            this.cinemaClose.run();
         }

         return;
      }

      this.searchFocus = false;
      this.search = false;
      this.drag = null;
      this.pickSet = null;
      this.pickGet = null;
      this.bindingModule = null;
      this.bindingKey = null;
      Menu.INSTANCE.setEnabled(false);
      // экран закрывается сразу (управление возвращается игроку), а меню дорастворяется поверх HUD
      this.needToClose = true;
      this.savedRunnable = this::renderGhost;
      mc.setScreen(null);
   }

   @Override
   public void close() {
      this.startClosing();
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   private static final class Hit {
      final float x;
      final float y;
      final float w;
      final float h;
      final Runnable left;
      final Runnable right;
      final Runnable middle;
      float clipTop = -1.0E9F;
      float clipBottom = 1.0E9F;

      Hit(float x, float y, float w, float h, Runnable left, Runnable right, Runnable middle) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.left = left;
         this.right = right;
         this.middle = middle;
      }

      Hit clip(float top, float bottom) {
         this.clipTop = top;
         this.clipBottom = bottom;
         return this;
      }

      boolean contains(float mx, float my) {
         return my >= this.clipTop && my <= this.clipBottom && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
