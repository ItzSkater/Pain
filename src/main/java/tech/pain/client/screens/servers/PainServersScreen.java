package tech.pain.client.screens.servers;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import tech.pain.Pain;
import tech.pain.base.font.Fonts;
import tech.pain.base.font.MsdfRenderer;
import tech.pain.base.theme.Theme;
import tech.pain.client.hud.HudStyle;
import tech.pain.client.screens.alt.AltScreen;
import tech.pain.client.screens.title.PainTitleScreen;
import tech.pain.utility.interfaces.IMinecraft;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.GuiUtil;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Список серверов в стиле главного меню Pain. Читает тот же servers.dat, что и обычный список,
 * сам опрашивает серверы (онлайн, пинг, описание). Двойной клик — подключиться.
 * Если экран не рисуется, клиент возвращает обычный список серверов.
 */
public class PainServersScreen extends Screen implements IMinecraft {
   private static final float SCALE = 2.0F;
   private static final float ROW = 34.0F;
   private static final ExecutorService PINGS = Executors.newFixedThreadPool(4, runnable -> {
      Thread thread = new Thread(runnable, "Pain-ServerPing");
      thread.setDaemon(true);
      return thread;
   });
   /** true — следующий MultiplayerScreen открыть как есть, без подмены (кнопка «Обычный список»). */
   public static boolean vanillaOnce;
   private final Screen parent;
   private final long openedAt = System.currentTimeMillis();
   private final List<Hit> hits = new ArrayList<>();
   private final java.util.Map<String, Float> hover = new java.util.HashMap<>();
   /** Загруженные иконки серверов: адрес → {текстура, хеш картинки, по которой она сделана}. */
   private final java.util.Map<String, Object[]> icons = new java.util.HashMap<>();
   private ServerList servers;
   private MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
   private int selected = -1;
   private float scroll;
   private float scrollTarget;
   private long lastClick;
   private int lastClicked = -1;
   private long deleteArmedAt;
   private float mouseX;
   private float mouseY;
   private int failures;
   private String message = "";
   private long messageAt;

   public PainServersScreen(Screen parent) {
      super(Text.of("Серверы"));
      this.parent = parent;
   }

   private record Hit(float x, float y, float w, float h, Runnable action) {
      boolean contains(float mx, float my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   @Override
   protected void init() {
      if (this.servers == null) {
         this.servers = new ServerList(mc);
         this.servers.loadFile();
         this.pingAll();
      }
   }

   private void pingAll() {
      this.pinger.cancel();
      this.pinger = new MultiplayerServerListPinger();

      for (int i = 0; i < this.servers.size(); ++i) {
         this.ping(this.servers.get(i));
      }
   }

   private void ping(ServerInfo info) {
      info.setStatus(ServerInfo.Status.PINGING);
      info.label = Text.empty();
      info.playerCountLabel = Text.empty();
      MultiplayerServerListPinger current = this.pinger;
      PINGS.submit(() -> {
         try {
            current.add(info, () -> mc.execute(this.servers::saveFile), () -> {
               // ответ пришёл: ставим «работает», если сервер не помечен как несовместимый
               if (info.getStatus() == ServerInfo.Status.PINGING) {
                  info.setStatus(ServerInfo.Status.SUCCESSFUL);
               }
            });
         } catch (Exception e) {
            info.setStatus(ServerInfo.Status.UNREACHABLE);
            info.label = Text.of(e instanceof java.net.UnknownHostException ? "Адрес не найден" : "Не удалось подключиться");
         }
      });
   }

   @Override
   public void tick() {
      this.pinger.tick();
   }

   @Override
   public void removed() {
      this.pinger.cancel();
      for (Object[] icon : this.icons.values()) {
         if (icon[0] != null) {
            mc.getTextureManager().destroyTexture((net.minecraft.util.Identifier)icon[0]);
         }
      }

      this.icons.clear();
   }

   private static String plain(Text text) {
      return text == null ? "" : text.getString().replaceAll("§.", "").replaceAll("\\s+", " ").trim();
   }

   /**
    * Иконка сервера как текстура. Картинка приходит в ответе на опрос (и хранится в servers.dat с прошлого раза),
    * поэтому сначала показывается сохранённая, а после опроса — свежая. Нет иконки или она битая — null.
    */
   private net.minecraft.util.Identifier iconFor(ServerInfo info) {
      byte[] bytes = info.getFavicon();
      if (bytes == null || bytes.length == 0) {
         return null;
      }

      int hash = java.util.Arrays.hashCode(bytes);
      Object[] cached = this.icons.get(info.address);
      if (cached != null && (Integer)cached[1] == hash) {
         return (net.minecraft.util.Identifier)cached[0];
      }

      net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("pain", "servers/" + Integer.toHexString(info.address.hashCode()) + "_" + Integer.toHexString(hash));
      try {
         net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage.read(bytes);
         if (image.getWidth() != 64 || image.getHeight() != 64) {
            image.close();
            this.icons.put(info.address, new Object[]{null, hash});
            return null;
         }

         if (cached != null && cached[0] != null) {
            mc.getTextureManager().destroyTexture((net.minecraft.util.Identifier)cached[0]);
         }

         mc.getTextureManager().registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(image));
         this.icons.put(info.address, new Object[]{id, hash});
         return id;
      } catch (Exception e) {
         this.icons.put(info.address, new Object[]{null, hash});
         return null;
      }
   }

   private void say(String text) {
      this.message = text;
      this.messageAt = System.currentTimeMillis();
   }

   @Override
   public void render(DrawContext context, int mx, int my, float delta) {
      double savedScale = mc.getWindow().getScaleFactor();
      setScale(SCALE);
      Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
      RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float)mc.getWindow().getScaledWidth(), (float)mc.getWindow().getScaledHeight(), 0.0F, 1000.0F, 21000.0F), ProjectionType.ORTHOGRAPHIC);

      try {
         this.renderServers(new CustomDrawContext(mc.getBufferBuilders().getEntityVertexConsumers()));
      } catch (Throwable e) {
         e.printStackTrace();
         ++this.failures;
      } finally {
         context.draw();
         setScale(savedScale);
         RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
      }

      // экран не рисуется — возвращаем обычный список, чтобы игрок не остался без серверов
      if (this.failures > 3) {
         tech.pain.client.modules.impl.render.CoolInterface.INSTANCE.customServers.setEnabled(false);
         this.openVanilla();
      }
   }

   private void openVanilla() {
      vanillaOnce = true;
      mc.setScreen(new MultiplayerScreen(this.parent));
   }

   private static void setScale(double scale) {
      mc.getWindow().scaleFactor = scale;
      int i = (int)((double)mc.getWindow().framebufferWidth / scale);
      mc.getWindow().scaledWidth = (double)mc.getWindow().framebufferWidth / scale > (double)i ? i + 1 : i;
      int j = (int)((double)mc.getWindow().framebufferHeight / scale);
      mc.getWindow().scaledHeight = (double)mc.getWindow().framebufferHeight / scale > (double)j ? j + 1 : j;
   }

   private void renderServers(CustomDrawContext ctx) {
      MatrixStack m = ctx.getMatrices();
      Vector2f mouse = GuiUtil.getMouse(SCALE);
      this.mouseX = mouse.getX();
      this.mouseY = mouse.getY();
      float sw = (float)mc.getWindow().getScaledWidth();
      float sh = (float)mc.getWindow().getScaledHeight();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      ColorRGBA accent = theme.getColor();
      float appear = Math.min(1.0F, (float)(System.currentTimeMillis() - this.openedAt) / 350.0F);
      appear = 1.0F - (1.0F - appear) * (1.0F - appear) * (1.0F - appear);
      int alpha = (int)(255.0F * appear);
      PainTitleScreen.drawWallpaper(m, sw, sh, this.mouseX, this.mouseY);
      DrawUtil.drawRoundedRect(m, 0.0F, 0.0F, sw, sh, BorderRadius.ZERO, new ColorRGBA(6, 6, 12, 110));
      this.hits.clear();
      float w = Math.min(420.0F, sw - 40.0F);
      float h = Math.min(sh - 40.0F, 380.0F);
      float x = (sw - w) / 2.0F;
      float y = (sh - h) / 2.0F + (1.0F - appear) * 10.0F;
      HudStyle.glass(m, x, y, w, h, BorderRadius.all(12.0F), appear, true);
      ctx.drawText(Fonts.BOLD.getFont(11.0F), "Серверы", x + 12.0F, y + 10.0F, new ColorRGBA(255, 255, 255, alpha));
      String who = "аккаунт: " + mc.getSession().getUsername();
      ctx.drawText(Fonts.REGULAR.getFont(7.0F), who, x + w - Fonts.REGULAR.getWidth(who, 7.0F) - 12.0F, y + 13.0F, new ColorRGBA(210, 210, 225, (int)(190.0F * appear)));
      DrawUtil.drawRoundedRect(m, x + 10.0F, y + 28.0F, w - 20.0F, 0.5F, BorderRadius.ZERO, new ColorRGBA(255, 255, 255, 30.0F * appear));

      // список
      float listY = y + 33.0F;
      float listH = h - 33.0F - 62.0F;
      int count = this.servers.size();
      float max = Math.max(0.0F, (float)count * ROW - listH);
      this.scrollTarget = Math.max(0.0F, Math.min(max, this.scrollTarget));
      this.scroll += (this.scrollTarget - this.scroll) * 0.25F;
      ctx.draw();
      ctx.enableScissor((int)x, (int)listY, (int)(x + w), (int)(listY + listH));
      boolean inside = this.mouseY >= listY && this.mouseY < listY + listH;

      for (int i = 0; i < count; ++i) {
         ServerInfo info = this.servers.get(i);
         float ry = listY + (float)i * ROW - this.scroll;
         if (ry + ROW < listY || ry > listY + listH) {
            continue;
         }

         boolean over = inside && this.mouseX >= x + 8.0F && this.mouseX < x + w - 8.0F && this.mouseY >= ry && this.mouseY < ry + ROW;
         String key = "row" + i;
         float hv = this.hover.getOrDefault(key, 0.0F);
         hv += ((over ? 1.0F : 0.0F) - hv) * 0.25F;
         this.hover.put(key, hv);
         BorderRadius r = BorderRadius.all(7.0F);
         if (i == this.selected) {
            DrawUtil.drawRoundedRect(m, x + 8.0F, ry + 1.5F, w - 16.0F, ROW - 3.0F, r, accent.withAlpha(70.0F * appear));
         }

         if (hv > 0.02F) {
            DrawUtil.drawRoundedRect(m, x + 8.0F, ry + 1.5F, w - 16.0F, ROW - 3.0F, r, new ColorRGBA(255, 255, 255, 20.0F * hv * appear));
         }

         ServerInfo.Status status = info.getStatus();
         boolean online = status == ServerInfo.Status.SUCCESSFUL || status == ServerInfo.Status.INCOMPATIBLE;
         boolean pinging = status == ServerInfo.Status.PINGING || status == ServerInfo.Status.INITIAL;
         ColorRGBA dot = online ? new ColorRGBA(60, 220, 110) : (pinging ? new ColorRGBA(240, 200, 70) : new ColorRGBA(235, 80, 90));
         // кружок с первой буквой названия, цвет каймы — состояние сервера
         float cx = x + 16.0F;
         float cy = ry + ROW / 2.0F - 10.0F;
         net.minecraft.util.Identifier icon = this.iconFor(info);
         if (icon != null) {
            // иконка сервера; состояние показывает точка в углу
            DrawUtil.drawRoundedTexture(m, icon, cx, cy, 20.0F, 20.0F, BorderRadius.all(5.0F), new ColorRGBA(255, 255, 255, alpha));
            DrawUtil.drawRoundedBorder(m, cx, cy, 20.0F, 20.0F, -0.5F, BorderRadius.all(5.0F), new ColorRGBA(255, 255, 255, 50.0F * appear));
            DrawUtil.drawRoundedRect(m, cx + 14.5F, cy + 14.5F, 7.0F, 7.0F, BorderRadius.all(3.5F), new ColorRGBA(12, 12, 18, 255.0F * appear));
            DrawUtil.drawRoundedRect(m, cx + 15.5F, cy + 15.5F, 5.0F, 5.0F, BorderRadius.all(2.5F), dot.withAlpha(255.0F * appear));
         } else {
            DrawUtil.drawRoundedRect(m, cx, cy, 20.0F, 20.0F, BorderRadius.all(10.0F), new ColorRGBA(255, 255, 255, 26.0F * appear));
            DrawUtil.drawRoundedBorder(m, cx, cy, 20.0F, 20.0F, -0.5F, BorderRadius.all(10.0F), dot.withAlpha(220.0F * appear));
            String letter = info.name.isEmpty() ? "?" : info.name.substring(0, 1).toUpperCase();
            ctx.drawText(Fonts.BOLD.getFont(9.0F), letter, cx + 10.0F - Fonts.BOLD.getWidth(letter, 9.0F) / 2.0F, cy + 5.5F, new ColorRGBA(255, 255, 255, alpha));
         }
         float textX = cx + 28.0F;
         float rightW = 70.0F;
         float textW = w - 16.0F - 28.0F - 16.0F - rightW;
         MsdfRenderer.renderText(Fonts.MEDIUM, info.name, 8.5F, new ColorRGBA(250, 250, 255, alpha).getRGB(), m.peek().getPositionMatrix(), textX, ry + 7.0F, 0.0F, true, 0.9F, 1.0F, textW);
         String motd = pinging ? "опрашиваю…" : plain(info.label);
         if (motd.isEmpty()) {
            motd = info.address;
         }

         MsdfRenderer.renderText(Fonts.REGULAR, motd, 6.8F, new ColorRGBA(200, 200, 215, (int)(170.0F * appear)).getRGB(), m.peek().getPositionMatrix(), textX, ry + 19.0F, 0.0F, true, 0.85F, 1.0F, textW);
         // справа: игроки и пинг
         String players = online ? plain(info.playerCountLabel) : (pinging ? "" : "не в сети");
         if (!players.isEmpty()) {
            ctx.drawText(Fonts.MEDIUM.getFont(7.5F), players, x + w - 18.0F - Fonts.MEDIUM.getWidth(players, 7.5F), ry + 8.0F, new ColorRGBA(240, 240, 248, (int)(230.0F * appear)));
         }

         if (online && info.ping >= 0L) {
            String ping = info.ping + " мс";
            ColorRGBA pc = info.ping < 100L ? new ColorRGBA(120, 235, 160) : (info.ping < 250L ? new ColorRGBA(240, 210, 100) : new ColorRGBA(240, 120, 120));
            ctx.drawText(Fonts.REGULAR.getFont(6.5F), ping, x + w - 18.0F - Fonts.REGULAR.getWidth(ping, 6.5F), ry + 19.5F, pc.withAlpha(220.0F * appear));
         }

         if (inside) {
            final int index = i;
            this.hits.add(new Hit(x + 8.0F, ry, w - 16.0F, ROW, () -> this.clickRow(index)));
         }
      }

      ctx.draw();
      ctx.disableScissor();
      if (count == 0) {
         String empty = "Серверов пока нет — нажми «Добавить»";
         ctx.drawText(Fonts.REGULAR.getFont(8.0F), empty, x + (w - Fonts.REGULAR.getWidth(empty, 8.0F)) / 2.0F, listY + listH / 2.0F - 4.0F, new ColorRGBA(210, 210, 225, (int)(170.0F * appear)));
      }

      // кнопки в два ряда
      boolean has = this.selected >= 0 && this.selected < count;
      float by = y + h - 54.0F;
      float gap = 4.0F;
      float bw = (w - 20.0F - gap * 3.0F) / 4.0F;
      this.button(ctx, m, x + 10.0F, by, bw, 20.0F, "Подключиться", appear, accent, has, true, this::connectSelected);
      this.button(ctx, m, x + 10.0F + (bw + gap), by, bw, 20.0F, "По адресу", appear, accent, true, false, this::direct);
      this.button(ctx, m, x + 10.0F + (bw + gap) * 2.0F, by, bw, 20.0F, "Добавить", appear, accent, true, false, this::add);
      this.button(ctx, m, x + 10.0F + (bw + gap) * 3.0F, by, bw, 20.0F, "Обновить", appear, accent, true, false, this::pingAll);
      float by2 = by + 24.0F;
      boolean armed = System.currentTimeMillis() - this.deleteArmedAt < 3000L;
      this.button(ctx, m, x + 10.0F, by2, bw, 20.0F, "Изменить", appear, accent, has, false, this::edit);
      this.button(ctx, m, x + 10.0F + (bw + gap), by2, bw, 20.0F, armed ? "Точно удалить?" : "Удалить", appear, armed ? new ColorRGBA(235, 80, 90) : accent, has, armed, this::delete);
      this.button(ctx, m, x + 10.0F + (bw + gap) * 2.0F, by2, bw, 20.0F, "Аккаунты", appear, accent, true, false, () -> mc.setScreen(new AltScreen(this)));
      this.button(ctx, m, x + 10.0F + (bw + gap) * 3.0F, by2, bw, 20.0F, "Назад", appear, accent, true, false, this::close);
      // запасной выход в обычный список — маленькой ссылкой под окном
      String link = "обычный список серверов";
      float lw = Fonts.REGULAR.getWidth(link, 6.5F);
      float lx = x + (w - lw) / 2.0F;
      float ly = y + h + 6.0F;
      boolean linkOver = this.mouseX >= lx - 4.0F && this.mouseX < lx + lw + 4.0F && this.mouseY >= ly - 3.0F && this.mouseY < ly + 10.0F;
      ctx.drawText(Fonts.REGULAR.getFont(6.5F), link, lx, ly, new ColorRGBA(220, 220, 235, (int)((linkOver ? 230.0F : 130.0F) * appear)));
      this.hits.add(new Hit(lx - 4.0F, ly - 3.0F, lw + 8.0F, 13.0F, this::openVanilla));
      long since = System.currentTimeMillis() - this.messageAt;
      if (!this.message.isEmpty() && since < 3500L) {
         float ma = Math.min(1.0F, (float)(3500L - since) / 500.0F) * appear;
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), this.message, x + (w - Fonts.REGULAR.getWidth(this.message, 7.0F)) / 2.0F, y - 14.0F, new ColorRGBA(235, 235, 245, (int)(230.0F * ma)));
      }

      ctx.draw();
   }

   private void button(CustomDrawContext ctx, MatrixStack m, float x, float y, float w, float h, String text, float a, ColorRGBA accent, boolean enabled, boolean filled, Runnable action) {
      boolean over = enabled && this.mouseX >= x && this.mouseX < x + w && this.mouseY >= y && this.mouseY < y + h;
      float hv = this.hover.getOrDefault("#" + text, 0.0F);
      hv += ((over ? 1.0F : 0.0F) - hv) * 0.25F;
      this.hover.put("#" + text, hv);
      float dim = enabled ? 1.0F : 0.4F;
      BorderRadius r = BorderRadius.all(7.0F);
      DrawUtil.drawRoundedRect(m, x, y, w, h, r, new ColorRGBA(255, 255, 255, (20.0F + 14.0F * hv) * a * dim));
      if (filled && enabled || hv > 0.02F) {
         DrawUtil.drawRoundedRect(m, x, y, w, h, r, accent.withAlpha((filled && enabled ? 95.0F : 0.0F) * a + 80.0F * hv * a));
      }

      DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, r, new ColorRGBA(255, 255, 255, (45.0F + 60.0F * hv) * a * dim));
      float size = Fonts.MEDIUM.getWidth(text, 7.0F) > w - 8.0F ? 6.0F : 7.0F;
      float tw = Fonts.MEDIUM.getWidth(text, size);
      ctx.drawText(Fonts.MEDIUM.getFont(size), text, x + (w - tw) / 2.0F, y + h / 2.0F - size / 2.0F, new ColorRGBA(250, 250, 255, (int)((215.0F + 40.0F * hv) * a * dim)));
      if (enabled) {
         this.hits.add(new Hit(x, y, w, h, action));
      }
   }

   private void clickRow(int index) {
      long now = System.currentTimeMillis();
      this.selected = index;
      this.deleteArmedAt = 0L;
      if (index == this.lastClicked && now - this.lastClick < 350L) {
         this.connectSelected();
      }

      this.lastClicked = index;
      this.lastClick = now;
   }

   private ServerInfo selectedInfo() {
      return this.selected >= 0 && this.selected < this.servers.size() ? this.servers.get(this.selected) : null;
   }

   private void connect(ServerInfo info) {
      ConnectScreen.connect(this, mc, ServerAddress.parse(info.address), info, false, null);
   }

   private void connectSelected() {
      ServerInfo info = this.selectedInfo();
      if (info != null) {
         this.connect(info);
      }
   }

   private void direct() {
      ServerInfo info = new ServerInfo("Minecraft Server", "", ServerInfo.ServerType.OTHER);
      mc.setScreen(new DirectConnectScreen(this, confirmed -> {
         if (confirmed && !info.address.isBlank()) {
            this.connect(info);
         } else {
            mc.setScreen(this);
         }
      }, info));
   }

   private void add() {
      ServerInfo info = new ServerInfo("Minecraft Server", "", ServerInfo.ServerType.OTHER);
      mc.setScreen(new AddServerScreen(this, confirmed -> {
         if (confirmed) {
            this.servers.add(info, false);
            this.servers.saveFile();
            this.selected = this.servers.size() - 1;
            this.ping(info);
         }

         mc.setScreen(this);
      }, info));
   }

   private void edit() {
      ServerInfo info = this.selectedInfo();
      if (info == null) {
         return;
      }

      // правим копию: если нажать «Отмена», в списке ничего не изменится
      ServerInfo copy = new ServerInfo(info.name, info.address, ServerInfo.ServerType.OTHER);
      copy.copyWithSettingsFrom(info);
      mc.setScreen(new AddServerScreen(this, confirmed -> {
         if (confirmed) {
            info.name = copy.name;
            info.address = copy.address;
            info.copyWithSettingsFrom(copy);
            this.servers.saveFile();
            this.ping(info);
         }

         mc.setScreen(this);
      }, copy));
   }

   private void delete() {
      ServerInfo info = this.selectedInfo();
      if (info == null) {
         return;
      }

      // первое нажатие только «взводит» кнопку, удаляет второе в течение трёх секунд
      if (System.currentTimeMillis() - this.deleteArmedAt > 3000L) {
         this.deleteArmedAt = System.currentTimeMillis();
         return;
      }

      this.deleteArmedAt = 0L;
      this.servers.remove(info);
      this.servers.saveFile();
      this.selected = Math.min(this.selected, this.servers.size() - 1);
      this.say("Удалён: " + info.name);
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
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
      this.scrollTarget -= (float)vertical * ROW * 1.5F;
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (key == GLFW.GLFW_KEY_ESCAPE) {
         this.close();
      } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
         this.connectSelected();
      } else if (key == GLFW.GLFW_KEY_DOWN) {
         this.selected = Math.min(this.servers.size() - 1, this.selected + 1);
      } else if (key == GLFW.GLFW_KEY_UP) {
         this.selected = Math.max(0, this.selected - 1);
      } else if (key == GLFW.GLFW_KEY_F5) {
         this.pingAll();
      } else if (key == GLFW.GLFW_KEY_DELETE) {
         this.delete();
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
