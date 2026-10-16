package tech.pain.client.screens.menu.tabs;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.pain.Pain;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.base.theme.Theme;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.setting.Setting;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.ButtonSetting;
import tech.pain.client.modules.api.setting.impl.ColorSetting;
import tech.pain.client.modules.api.setting.impl.KeySetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.MultiBooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.client.modules.api.setting.impl.StringSetting;
import tech.pain.client.modules.impl.render.Menu;
import tech.pain.client.screens.menu.MenuScreen;
import tech.pain.utility.game.player.PlayerInventoryComponent;
import tech.pain.utility.render.display.Keyboard;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.Gradient;
import tech.pain.utility.render.display.base.GuiUtil;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Клик-гуи «Tabs» и «Liquid Tabs» в духе Meteor: каждая категория — отдельное окно, окна можно таскать
 * за заголовок и сворачивать правым кликом. Модуль: левый клик — вкл/выкл, правый — раскрыть настройки
 * прямо под ним, колесо — бинд. Сверху поиск по всем модулям, снизу подсказка с описанием.
 * Liquid Tabs — окна из жидкого стекла, Tabs — глубокий чёрный.
 */
public class TabsMenuScreen extends MenuScreen {
   private static final float SCALE = 2.0F;
   private static final float PANEL_W = 116.0F;
   private static final float HEAD_H = 21.0F;
   private static final float ROW_H = 15.0F;
   // окна запоминают своё место между открытиями меню
   private static final Map<Category, float[]> POS = new HashMap<>();
   private static final Set<Category> COLLAPSED = new HashSet<>();
   private final Set<Module> expanded = new HashSet<>();
   private final Map<Object, Float> anim = new HashMap<>();
   private final Map<Category, Float> scroll = new HashMap<>();
   private final List<Hit> hits = new ArrayList<>();
   private float mouseX;
   private float mouseY;
   private long openedAt = System.currentTimeMillis();
   private Category dragPanel;
   private float dragDX;
   private float dragDY;
   private NumberSetting slider;
   private float sliderX;
   private float sliderW;
   private ColorSetting hueDrag;
   private float hueX;
   private float hueW;
   private final Set<ColorSetting> openColors = new HashSet<>();
   private Module bindingModule;
   private KeySetting bindingKey;
   private StringSetting editing;
   private boolean searchFocus;
   private String query = "";
   private Module hovered;

   public TabsMenuScreen() {
   }

   private boolean liquid() {
      return Menu.INSTANCE.style.is("Liquid Tabs");
   }

   public void reopen() {
      this.openedAt = System.currentTimeMillis();
      this.needToClose = false;
   }

   @Override
   protected void init() {
      this.openedAt = System.currentTimeMillis();
   }

   private static void setScale(double scale) {
      net.minecraft.client.util.Window w = mc.getWindow();
      w.scaleFactor = scale;
      int i = (int)((double)w.framebufferWidth / scale);
      w.scaledWidth = (double)w.framebufferWidth / scale > (double)i ? i + 1 : i;
      int j = (int)((double)w.framebufferHeight / scale);
      w.scaledHeight = (double)w.framebufferHeight / scale > (double)j ? j + 1 : j;
   }

   @Override
   public void render(DrawContext context, int mx, int my, float delta) {
      double saved = mc.getWindow().getScaleFactor();
      setScale(SCALE);
      Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
      RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float)mc.getWindow().getScaledWidth(), (float)mc.getWindow().getScaledHeight(), 0.0F, 1000.0F, 21000.0F), ProjectionType.ORTHOGRAPHIC);

      try {
         this.draw(context instanceof CustomDrawContext c ? c : CustomDrawContext.of(context));
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         context.draw();
         setScale(saved);
         RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
      }
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   private float smooth(Object key, float target, float speed) {
      float v = this.anim.getOrDefault(key, target);
      v += (target - v) * speed;
      if (Math.abs(target - v) < 0.002F) {
         v = target;
      }

      this.anim.put(key, v);
      return v;
   }

   private void draw(CustomDrawContext ctx) {
      MatrixStack m = ctx.getMatrices();
      Vector2f mouse = GuiUtil.getMouse(SCALE);
      this.mouseX = mouse.getX();
      this.mouseY = mouse.getY();
      this.hits.clear();
      this.hovered = null;
      if (!this.searchFocus && this.editing == null) {
         PlayerInventoryComponent.updateMoveKeys();
      }

      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      float open = MathHelper.clamp((float)(System.currentTimeMillis() - this.openedAt) / 260.0F, 0.0F, 1.0F);
      float ease = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      // фон: мир чуть размыт и притемнён, чтобы окна читались
      if (this.liquid()) {
         DrawUtil.drawBlur(m, 0.0F, 0.0F, sw, sh, 6.0F * ease, BorderRadius.ZERO, new ColorRGBA(255, 255, 255, 255.0F * ease));
      }

      DrawUtil.drawRect(m, 0.0F, 0.0F, sw, sh, new ColorRGBA(0, 0, 0, 70.0F * ease));
      this.drawSearch(ctx, m, theme, sw, ease);
      float x = 14.0F;
      int index = 0;

      for (Category cat : Category.values()) {
         if (cat == Category.THEMES) {
            continue;
         }

         float[] pos = POS.computeIfAbsent(cat, c -> new float[]{0.0F, 0.0F, 0.0F});
         if (pos[2] == 0.0F) {
            pos[0] = x;
            pos[1] = 38.0F;
            pos[2] = 1.0F;
         }

         x += PANEL_W + 8.0F;
         // окна выпадают по очереди
         float local = MathHelper.clamp(open * 1.6F - (float)index * 0.12F, 0.0F, 1.0F);
         float drop = 1.0F - (1.0F - local) * (1.0F - local);
         if (this.dragPanel == cat) {
            pos[0] = MathHelper.clamp(this.mouseX - this.dragDX, 0.0F, sw - PANEL_W);
            pos[1] = MathHelper.clamp(this.mouseY - this.dragDY, 0.0F, sh - HEAD_H);
         }

         this.drawPanel(ctx, m, theme, cat, pos[0], pos[1] - (1.0F - drop) * 14.0F, drop, sh);
         ++index;
      }

      this.drawTooltip(ctx, m, theme, sw, sh);
   }

   private void drawSearch(CustomDrawContext ctx, MatrixStack m, Theme theme, float sw, float a) {
      float w = 150.0F;
      float h = 15.0F;
      float x = (sw - w) / 2.0F;
      float y = 12.0F;
      this.material(m, x, y, w, h, 4.0F, a, theme);
      if (this.searchFocus) {
         DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.6F, BorderRadius.all(4.0F), theme.getColor().withAlpha(180.0F * a));
      }

      Font font = Fonts.REGULAR.getFont(6.5F);
      String shown = this.query.isEmpty() && !this.searchFocus ? "Поиск модулей..." : this.query + (this.searchFocus && System.currentTimeMillis() % 1000L < 520L ? "_" : "");
      ctx.drawText(Fonts.LUPA.getFont(6.0F), "", x + 7.0F, y + 4.5F, new ColorRGBA(170, 170, 185, 220.0F * a));
      ctx.drawText(font, shown, x + 17.0F, y + 5.0F, new ColorRGBA(this.query.isEmpty() ? 150 : 235, this.query.isEmpty() ? 150 : 235, this.query.isEmpty() ? 162 : 242, 255.0F * a));
      this.hits.add(new Hit(x, y, w, h, () -> {
         this.searchFocus = true;
         this.editing = null;
      }, () -> this.query = "", null));
   }

   /** Материал окна: стекло для Liquid Tabs, глубокий чёрный для Tabs. */
   private void material(MatrixStack m, float x, float y, float w, float h, float r, float a, Theme theme) {
      BorderRadius radius = BorderRadius.all(r);
      ColorRGBA clear = new ColorRGBA(255, 255, 255, 0);
      if (this.liquid()) {
         DrawUtil.drawShadow(m, x, y + 1.0F, w, h, 14.0F, radius, new ColorRGBA(0, 0, 0, 70.0F * a));
         DrawUtil.drawBlur(m, x, y, w, h, 14.0F, radius, new ColorRGBA(255, 255, 255, 255.0F * a));
         DrawUtil.drawRoundedRect(m, x, y, w, h, radius, new ColorRGBA(18, 22, 32, 110.0F * a));
         ColorRGBA hi = new ColorRGBA(255, 255, 255, 44.0F * a);
         DrawUtil.drawRoundedRect(m, x + 1.0F, y + 0.75F, w - 2.0F, Math.min(h * 0.4F, 12.0F), BorderRadius.top(r, r), Gradient.of(hi, clear, hi, clear));
         DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, radius, new ColorRGBA(255, 255, 255, 80.0F * a));
      } else {
         DrawUtil.drawShadow(m, x, y + 1.0F, w, h, 12.0F, radius, new ColorRGBA(0, 0, 0, 140.0F * a));
         DrawUtil.drawRoundedRect(m, x, y, w, h, radius, new ColorRGBA(8, 8, 11, 238.0F * a));
         DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, radius, new ColorRGBA(255, 255, 255, 18.0F * a));
      }
   }

   private List<Module> modules(Category cat) {
      List<Module> out = new ArrayList<>();
      String q = this.query.toLowerCase(Locale.ROOT);

      for (Module module : Pain.getInstance().getModuleManager().getModules()) {
         if (module.getCategory() == cat && (q.isEmpty() || module.getName().toLowerCase(Locale.ROOT).contains(q))) {
            out.add(module);
         }
      }

      out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      return out;
   }

   private void drawPanel(CustomDrawContext ctx, MatrixStack m, Theme theme, Category cat, float x, float y, float a, float sh) {
      List<Module> list = this.modules(cat);
      if (!this.query.isEmpty() && list.isEmpty()) {
         return;
      }

      boolean collapsed = COLLAPSED.contains(cat) && this.query.isEmpty();
      // высота содержимого: строки модулей плюс раскрытые настройки
      float content = 0.0F;
      if (!collapsed) {
         for (Module module : list) {
            content += ROW_H;
            float open = this.smooth("exp_" + module.getName(), this.expanded.contains(module) ? 1.0F : 0.0F, 0.25F);
            if (open > 0.0F) {
               content += this.settingsHeight(module) * open;
            }
         }

         content += 4.0F;
      }

      float maxH = Math.max(60.0F, sh - y - HEAD_H - 10.0F);
      float bodyH = Math.min(content, maxH);
      float shownH = this.smooth("body_" + cat, bodyH, 0.3F);
      float total = HEAD_H + shownH;
      this.material(m, x, y, PANEL_W, total, 4.0F, a, theme);
      ColorRGBA c1 = theme.getColor().withAlpha(255.0F * a);
      ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
      // заголовок: иконка, название, число модулей, полоса цвета темы
      ctx.drawText(Fonts.ICONS.getFont(8.0F), cat.getIcon(), x + 8.0F, y + 7.0F, c1);
      ctx.drawText(Fonts.MEDIUM.getFont(8.0F), cat.getName(), x + 21.0F, y + 7.0F, new ColorRGBA(245, 245, 250, 255.0F * a));
      String count = String.valueOf(list.size());
      Font small = Fonts.REGULAR.getFont(6.0F);
      float cw = small.width(count) + 7.0F;
      DrawUtil.drawRoundedRect(m, x + PANEL_W - 8.0F - cw, y + 6.0F, cw, 9.0F, BorderRadius.all(4.5F), new ColorRGBA(255, 255, 255, 14.0F * a));
      ctx.drawText(small, count, x + PANEL_W - 8.0F - cw + 3.5F, y + 8.5F, new ColorRGBA(210, 210, 220, 220.0F * a));
      DrawUtil.drawRoundedRect(m, x + 7.0F, y + HEAD_H - 1.5F, PANEL_W - 14.0F, 1.0F, BorderRadius.all(0.5F), Gradient.of(c1, c1, c2, c2));
      this.hits.add(new Hit(x, y, PANEL_W, HEAD_H, () -> {
         this.dragPanel = cat;
         float[] pos = POS.get(cat);
         this.dragDX = this.mouseX - pos[0];
         this.dragDY = this.mouseY - pos[1];
      }, () -> {
         if (!COLLAPSED.remove(cat)) {
            COLLAPSED.add(cat);
         }
      }, null));
      if (shownH < 1.0F) {
         return;
      }

      float top = y + HEAD_H;
      float bottom = top + shownH;
      float max = Math.max(0.0F, content - bodyH);
      float sc = MathHelper.clamp(this.scroll.getOrDefault(cat, 0.0F), -max, 0.0F);
      this.scroll.put(cat, sc);
      float shownScroll = this.smooth("scroll_" + cat, sc, 0.3F);
      ctx.enableScissor((int)x, (int)top, (int)(x + PANEL_W), (int)bottom);
      float ry = top + 2.0F + shownScroll;

      for (Module module : list) {
         if (ry + ROW_H >= top && ry <= bottom) {
            this.drawModule(ctx, m, theme, module, x, ry, a, top, bottom);
         }

         ry += ROW_H;
         float open = this.anim.getOrDefault("exp_" + module.getName(), 0.0F);
         if (open > 0.01F) {
            float sh2 = this.settingsHeight(module);
            float visible = sh2 * open;
            if (ry + visible >= top && ry <= bottom) {
               ctx.enableScissor((int)x, (int)Math.max(top, ry), (int)(x + PANEL_W), (int)Math.min(bottom, ry + visible));
               this.drawSettings(ctx, m, theme, module, x, ry, a, Math.max(top, ry), Math.min(bottom, ry + visible));
               ctx.disableScissor();
            }

            ry += visible;
         }
      }

      ctx.disableScissor();
      this.hits.add(new Hit(x, top, PANEL_W, shownH, null, null, null).scroll(cat));
   }

   private void drawModule(CustomDrawContext ctx, MatrixStack m, Theme theme, Module module, float x, float ry, float a, float top, float bottom) {
      boolean hover = this.mouseY >= top && this.mouseY <= bottom && GuiUtil.isHovered(x + 3.0F, ry, PANEL_W - 6.0F, ROW_H - 1.0F, this.mouseX, this.mouseY);
      if (hover) {
         this.hovered = module;
      }

      float h = this.smooth("hov_" + module.getName(), hover ? 1.0F : 0.0F, 0.25F);
      float on = module.getAnimation().getValue();
      ColorRGBA accent = theme.getColor();
      if (on > 0.01F) {
         ColorRGBA from = accent.withAlpha(60.0F * on * a);
         ColorRGBA to = theme.getSecondColor().withAlpha(8.0F * on * a);
         DrawUtil.drawRoundedRect(m, x + 3.0F, ry, PANEL_W - 6.0F, ROW_H - 1.0F, BorderRadius.all(2.5F), Gradient.of(from, from, to, to));
         DrawUtil.drawRoundedRect(m, x + 3.0F, ry + 3.0F, 1.5F, ROW_H - 7.0F, BorderRadius.all(0.75F), accent.withAlpha(255.0F * on * a));
      }

      if (h > 0.01F) {
         DrawUtil.drawRoundedRect(m, x + 3.0F, ry, PANEL_W - 6.0F, ROW_H - 1.0F, BorderRadius.all(2.5F), new ColorRGBA(255, 255, 255, 12.0F * h * a));
      }

      Font font = Fonts.MEDIUM.getFont(7.0F);
      ColorRGBA text = new ColorRGBA(175, 175, 188).mix(new ColorRGBA(255, 255, 255), Math.max(on, h * 0.6F)).withAlpha(255.0F * a);
      String label = this.bindingModule == module ? "Нажми клавишу..." : module.getName();
      ctx.drawText(font, label, x + 9.0F, ry + 4.5F, this.bindingModule == module ? accent.withAlpha(255.0F * a) : text);
      if (module.getKeyCode() > 0 && this.bindingModule != module) {
         String key = Keyboard.getKeyName(module.getKeyCode());
         if (key != null) {
            Font tiny = Fonts.REGULAR.getFont(5.5F);
            ctx.drawText(tiny, key, x + PANEL_W - 20.0F - tiny.width(key), ry + 5.0F, new ColorRGBA(140, 140, 155, 200.0F * a));
         }
      }

      if (!module.getSettings().isEmpty()) {
         // три точки: есть настройки; раскрыто — цвет темы
         float open = this.anim.getOrDefault("exp_" + module.getName(), 0.0F);
         ColorRGBA dots = new ColorRGBA(120, 120, 135).mix(accent, open).withAlpha(230.0F * a);

         for (int d = 0; d < 3; ++d) {
            DrawUtil.drawRoundedRect(m, x + PANEL_W - 15.0F + (float)d * 3.0F, ry + ROW_H / 2.0F - 1.1F, 1.6F, 1.6F, BorderRadius.all(0.8F), dots);
         }
      }

      this.hits.add(new Hit(x + 3.0F, ry, PANEL_W - 6.0F, ROW_H - 1.0F, module::toggle, () -> {
         if (!module.getSettings().isEmpty() && !this.expanded.remove(module)) {
            this.expanded.add(module);
         }
      }, () -> {
         this.bindingModule = module;
         this.bindingKey = null;
      }).clip(top, bottom));
   }

   private List<Setting> visibleSettings(Module module) {
      List<Setting> out = new ArrayList<>();

      for (Setting s : module.getSettings()) {
         if (s.isVisible()) {
            out.add(s);
         }
      }

      return out;
   }

   private float settingHeight(Setting s) {
      if (s instanceof NumberSetting) {
         return 20.0F;
      } else if (s instanceof MultiBooleanSetting multi) {
         return 12.0F + 10.0F * (float)multi.getBooleanSettings().size();
      } else if (s instanceof ColorSetting color && this.openColors.contains(color)) {
         return 24.0F;
      }

      return 13.0F;
   }

   private float settingsHeight(Module module) {
      float h = 3.0F;

      for (Setting s : this.visibleSettings(module)) {
         h += this.settingHeight(s);
      }

      return h + 2.0F;
   }

   private void drawSettings(CustomDrawContext ctx, MatrixStack m, Theme theme, Module module, float x, float y, float a, float top, float bottom) {
      DrawUtil.drawRoundedRect(m, x + 5.0F, y + 1.0F, PANEL_W - 10.0F, this.settingsHeight(module) - 2.0F, BorderRadius.all(4.0F), new ColorRGBA(0, 0, 0, 60.0F * a));
      float sy = y + 3.0F;

      for (Setting s : this.visibleSettings(module)) {
         float h = this.settingHeight(s);
         if (sy + h >= top && sy <= bottom) {
            this.drawSetting(ctx, m, theme, s, x + 9.0F, sy, PANEL_W - 18.0F, a, top, bottom);
         }

         sy += h;
      }
   }

   private void drawSetting(CustomDrawContext ctx, MatrixStack m, Theme theme, Setting s, float x, float y, float w, float a, float top, float bottom) {
      Font font = Fonts.REGULAR.getFont(6.0F);
      ColorRGBA text = new ColorRGBA(205, 205, 215, 255.0F * a);
      ColorRGBA dim = new ColorRGBA(150, 150, 162, 230.0F * a);
      ColorRGBA accent = theme.getColor();
      String name = fit(font, s.getName(), w - 34.0F);
      if (s instanceof BooleanSetting bool) {
         ctx.drawText(font, name, x, y + 3.5F, text);
         float on = this.smooth(bool, bool.isEnabled() ? 1.0F : 0.0F, 0.3F);
         float tx = x + w - 14.0F;
         DrawUtil.drawRoundedRect(m, tx, y + 2.5F, 14.0F, 7.0F, BorderRadius.all(3.5F), new ColorRGBA(255, 255, 255, 25.0F * a).mix(accent.withAlpha(220.0F * a), on));
         DrawUtil.drawRoundedRect(m, tx + 1.0F + 7.0F * on, y + 3.5F, 5.0F, 5.0F, BorderRadius.all(2.5F), new ColorRGBA(255, 255, 255, 255.0F * a));
         this.hits.add(new Hit(x, y, w, 12.0F, () -> bool.setEnabled(!bool.isEnabled()), null, null).clip(top, bottom));
      } else if (s instanceof NumberSetting num) {
         ctx.drawText(font, name, x, y + 2.0F, text);
         String value = format(num);
         ctx.drawText(font, value, x + w - font.width(value), y + 2.0F, accent.mix(new ColorRGBA(255, 255, 255), 0.35F).withAlpha(255.0F * a));
         float range = num.getMax() - num.getMin();
         if (this.slider == num) {
            this.sliderX = x;
            this.sliderW = w;
            this.applySlider();
         }

         float pct = range <= 0.0F ? 0.0F : (num.getCurrent() - num.getMin()) / range;
         float shown = this.smooth(num, pct, 0.35F);
         DrawUtil.drawRoundedRect(m, x, y + 12.0F, w, 3.0F, BorderRadius.all(1.5F), new ColorRGBA(255, 255, 255, 22.0F * a));
         ColorRGBA c1 = accent.withAlpha(255.0F * a);
         ColorRGBA c2 = theme.getSecondColor().withAlpha(255.0F * a);
         DrawUtil.drawRoundedRect(m, x, y + 12.0F, Math.max(3.0F, w * shown), 3.0F, BorderRadius.all(1.5F), Gradient.of(c2, c2, c1, c1));
         DrawUtil.drawShadow(m, x + w * shown - 3.0F, y + 10.5F, 6.0F, 6.0F, 5.0F, BorderRadius.all(3.0F), accent.withAlpha(110.0F * a));
         DrawUtil.drawRoundedRect(m, x + w * shown - 3.0F, y + 10.5F, 6.0F, 6.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, 255.0F * a));
         final float sx = x;
         final float swd = w;
         this.hits.add(new Hit(x - 2.0F, y + 8.0F, w + 4.0F, 11.0F, () -> {
            this.slider = num;
            this.sliderX = sx;
            this.sliderW = swd;
            this.applySlider();
         }, null, null).clip(top, bottom));
      } else if (s instanceof ModeSetting mode) {
         ctx.drawText(font, name, x, y + 3.5F, text);
         String value = "< " + mode.get() + " >";
         float vw = font.width(value);
         ctx.drawText(font, value, x + w - vw, y + 3.5F, accent.mix(new ColorRGBA(255, 255, 255), 0.3F).withAlpha(255.0F * a));
         this.hits.add(new Hit(x, y, w, 12.0F, () -> cycle(mode, 1), () -> cycle(mode, -1), null).clip(top, bottom));
      } else if (s instanceof MultiBooleanSetting multi) {
         ctx.drawText(font, name, x, y + 2.0F, text);
         float ry = y + 11.0F;

         for (MultiBooleanSetting.Value v : multi.getBooleanSettings()) {
            float on = this.smooth(v, v.isEnabled() ? 1.0F : 0.0F, 0.3F);
            DrawUtil.drawRoundedRect(m, x + 2.0F, ry + 1.5F, 6.0F, 6.0F, BorderRadius.all(2.0F), new ColorRGBA(255, 255, 255, 25.0F * a).mix(accent.withAlpha(230.0F * a), on));
            ctx.drawText(font, fit(font, v.getName(), w - 12.0F), x + 11.0F, ry + 2.0F, dim.mix(text, on));
            this.hits.add(new Hit(x, ry, w, 9.5F, v::toggle, null, null).clip(top, bottom));
            ry += 10.0F;
         }
      } else if (s instanceof ColorSetting color) {
         ctx.drawText(font, name, x, y + 3.5F, text);
         DrawUtil.drawRoundedRect(m, x + w - 14.0F, y + 2.5F, 14.0F, 7.0F, BorderRadius.all(2.5F), color.getColor().withAlpha(255.0F * a));
         DrawUtil.drawRoundedBorder(m, x + w - 14.0F, y + 2.5F, 14.0F, 7.0F, -0.5F, BorderRadius.all(2.5F), new ColorRGBA(255, 255, 255, 60.0F * a));
         this.hits.add(new Hit(x, y, w, 12.0F, () -> {
            if (!this.openColors.remove(color)) {
               this.openColors.add(color);
            }
         }, () -> color.setColor(theme.getColor()), null).clip(top, bottom));
         if (this.openColors.contains(color)) {
            // полоса оттенков: тянешь — меняется цвет, насыщенность и яркость сохраняются
            float hy = y + 14.0F;
            int steps = 12;

            for (int i = 0; i < steps; ++i) {
               ColorRGBA h0 = ColorRGBA.fromHSB((float)i / (float)steps, 0.85F, 1.0F).withAlpha(255.0F * a);
               ColorRGBA h1 = ColorRGBA.fromHSB((float)(i + 1) / (float)steps, 0.85F, 1.0F).withAlpha(255.0F * a);
               DrawUtil.drawRect(m, x + w * (float)i / (float)steps, hy, w / (float)steps + 0.3F, 5.0F, h0.mix(h1, 0.5F));
            }

            float hue = color.getColor().getHue();
            DrawUtil.drawRoundedRect(m, x + w * hue - 1.0F, hy - 1.0F, 2.0F, 7.0F, BorderRadius.all(1.0F), new ColorRGBA(255, 255, 255, 255.0F * a));
            if (this.hueDrag == color) {
               this.hueX = x;
               this.hueW = w;
               this.applyHue();
            }

            final float hx = x;
            final float hw = w;
            this.hits.add(new Hit(x, hy - 2.0F, w, 9.0F, () -> {
               this.hueDrag = color;
               this.hueX = hx;
               this.hueW = hw;
               this.applyHue();
            }, null, null).clip(top, bottom));
         }
      } else if (s instanceof KeySetting key) {
         ctx.drawText(font, name, x, y + 3.5F, text);
         String label = this.bindingKey == key ? "..." : key.getKeyCode() <= 0 ? "нет" : String.valueOf(Keyboard.getKeyName(key.getKeyCode()));
         float lw = font.width(label) + 8.0F;
         DrawUtil.drawRoundedRect(m, x + w - lw, y + 1.5F, lw, 9.0F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, 18.0F * a));
         ctx.drawText(font, label, x + w - lw + 4.0F, y + 3.5F, text);
         this.hits.add(new Hit(x, y, w, 12.0F, () -> {
            this.bindingKey = key;
            this.bindingModule = null;
         }, () -> key.setKeyCode(-1), null).clip(top, bottom));
      } else if (s instanceof StringSetting str) {
         boolean active = this.editing == str;
         String value = str.get() + (active && System.currentTimeMillis() % 1000L < 520L ? "_" : "");
         DrawUtil.drawRoundedRect(m, x, y + 1.0F, w, 10.5F, BorderRadius.all(3.0F), new ColorRGBA(255, 255, 255, (active ? 24.0F : 12.0F) * a));
         ctx.drawText(font, fit(font, value.isEmpty() ? s.getName() : value, w - 6.0F), x + 3.0F, y + 3.5F, value.isEmpty() ? dim : text);
         this.hits.add(new Hit(x, y, w, 12.0F, () -> {
            this.editing = str;
            this.searchFocus = false;
         }, () -> str.set(""), null).clip(top, bottom));
      } else if (s instanceof ButtonSetting button) {
         DrawUtil.drawRoundedRect(m, x, y + 1.0F, w, 10.5F, BorderRadius.all(3.0F), accent.withAlpha(50.0F * a));
         ctx.drawText(font, name, x + (w - font.width(name)) / 2.0F, y + 3.5F, text);
         this.hits.add(new Hit(x, y, w, 12.0F, () -> {
            if (button.getRunnable() != null) {
               button.getRunnable().run();
            }
         }, null, null).clip(top, bottom));
      } else {
         ctx.drawText(font, name, x, y + 3.5F, dim);
      }
   }

   private void drawTooltip(CustomDrawContext ctx, MatrixStack m, Theme theme, float sw, float sh) {
      Module module = this.hovered;
      float show = this.smooth("tooltip", module != null ? 1.0F : 0.0F, 0.2F);
      if (module != null) {
         this.anim.put("tooltip_text", 0.0F);
         this.lastTooltip = module;
      }

      if (show < 0.02F || this.lastTooltip == null) {
         return;
      }

      String text = this.lastTooltip.getInfo().description();
      Font font = Fonts.REGULAR.getFont(6.5F);
      text = fit(font, text, sw * 0.6F);
      float w = font.width(text) + 16.0F;
      float h = 14.0F;
      float x = (sw - w) / 2.0F;
      float y = sh - 26.0F + (1.0F - show) * 6.0F;
      this.material(m, x, y, w, h, 4.0F, show, theme);
      ctx.drawText(font, text, x + 8.0F, y + 4.5F, new ColorRGBA(225, 225, 235, 255.0F * show));
   }

   private Module lastTooltip;

   private void applySlider() {
      NumberSetting num = this.slider;
      float pct = MathHelper.clamp((this.mouseX - this.sliderX) / Math.max(1.0F, this.sliderW), 0.0F, 1.0F);
      float value = num.getMin() + (num.getMax() - num.getMin()) * pct;
      float inc = num.getIncrement();
      if (inc > 0.0F) {
         value = (float)Math.round(value / inc) * inc;
      }

      num.setCurrent(MathHelper.clamp(value, num.getMin(), num.getMax()));
   }

   private void applyHue() {
      ColorSetting color = this.hueDrag;
      float hue = MathHelper.clamp((this.mouseX - this.hueX) / Math.max(1.0F, this.hueW), 0.0F, 0.999F);
      ColorRGBA old = color.getColor();
      float sat = Math.max(0.35F, old.getSaturation());
      float bri = Math.max(0.5F, old.getBrightness());
      color.setColor(ColorRGBA.fromHSB(hue, sat, bri).withAlpha(old.getAlpha()));
   }

   private static void cycle(ModeSetting mode, int dir) {
      List<ModeSetting.Value> values = mode.getValues();
      int index = 0;

      for (int i = 0; i < values.size(); ++i) {
         if (values.get(i).getName().equals(mode.get())) {
            index = i;
         }
      }

      mode.set(values.get(Math.floorMod(index + dir, values.size())).getName());
   }

   private static String format(NumberSetting num) {
      String inc = String.valueOf(num.getIncrement());
      int scale = inc.contains(".") && !inc.endsWith(".0") ? inc.split("\\.")[1].length() : 0;
      return String.format(Locale.US, "%." + Math.min(scale, 3) + "f", num.getCurrent());
   }

   private static String fit(Font font, String text, float max) {
      if (font.width(text) <= max) {
         return text;
      }

      String t = text;

      while (t.length() > 1 && font.width(t + "...") > max) {
         t = t.substring(0, t.length() - 1);
      }

      return t + "...";
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      this.searchFocus = false;
      this.editing = null;
      if (this.bindingModule != null || this.bindingKey != null) {
         // клик мышью отменяет ожидание клавиши
         this.bindingModule = null;
         this.bindingKey = null;
         return true;
      }

      for (int i = this.hits.size() - 1; i >= 0; --i) {
         Hit hit = this.hits.get(i);
         if (hit.contains(this.mouseX, this.mouseY)) {
            Runnable action = button == 0 ? hit.left : button == 1 ? hit.right : button == 2 ? hit.middle : null;
            if (action != null) {
               action.run();
               return true;
            }

            if (hit.scrollOf == null) {
               return true;
            }
         }
      }

      return true;
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      this.dragPanel = null;
      this.slider = null;
      this.hueDrag = null;
      return true;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
      for (int i = this.hits.size() - 1; i >= 0; --i) {
         Hit hit = this.hits.get(i);
         if (hit.scrollOf != null && hit.contains(this.mouseX, this.mouseY)) {
            this.scroll.put(hit.scrollOf, this.scroll.getOrDefault(hit.scrollOf, 0.0F) + (float)vertical * 24.0F);
            return true;
         }
      }

      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.bindingModule != null || this.bindingKey != null) {
         int code = keyCode == GLFW.GLFW_KEY_ESCAPE ? this.currentBind() : keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : keyCode;
         if (this.bindingModule != null) {
            this.bindingModule.setKeyCode(code);
         } else {
            this.bindingKey.setKeyCode(code);
         }

         this.bindingModule = null;
         this.bindingKey = null;
         return true;
      }

      if (this.editing != null) {
         String value = this.editing.get();
         if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.editing = null;
         } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
            this.editing.set(value.substring(0, value.length() - 1));
         }

         return true;
      }

      if (this.searchFocus) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            this.searchFocus = false;
         } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !this.query.isEmpty()) {
            this.query = this.query.substring(0, this.query.length() - 1);
         }

         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == Menu.INSTANCE.getKeyCode() && System.currentTimeMillis() - this.openedAt > 300L) {
         this.close();
         return true;
      }

      return false;
   }

   private int currentBind() {
      return this.bindingModule != null ? this.bindingModule.getKeyCode() : this.bindingKey.getKeyCode();
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (chr < ' ' || chr == '§') {
         return true;
      }

      if (this.editing != null) {
         this.editing.set(this.editing.get() + chr);
      } else if (this.searchFocus) {
         this.query = this.query + chr;
      } else if (Character.isLetterOrDigit(chr) && this.bindingModule == null && this.bindingKey == null) {
         // начал печатать — сразу поиск
         this.searchFocus = true;
         this.query = this.query + chr;
      }

      return true;
   }

   @Override
   public void close() {
      this.searchFocus = false;
      this.editing = null;
      this.bindingModule = null;
      this.bindingKey = null;
      this.dragPanel = null;
      this.slider = null;
      Menu.INSTANCE.setEnabled(false);
      mc.setScreen(null);
   }

   @Override
   public boolean shouldPause() {
      return false;
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
      Category scrollOf;

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

      Hit scroll(Category cat) {
         this.scrollOf = cat;
         return this;
      }

      boolean contains(float mx, float my) {
         return my >= this.clipTop && my <= this.clipBottom && mx >= this.x && mx <= this.x + this.w && my >= this.y && my <= this.y + this.h;
      }
   }
}
