package tech.pain.client.hud.elements.component;

import tech.pain.client.hud.HudStyle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.network.PlayerListEntry;
import tech.pain.Pain;
import tech.pain.base.font.Fonts;
import tech.pain.base.font.MsdfFont;
import tech.pain.base.theme.Theme;
import tech.pain.client.hud.elements.draggable.DraggableHudElement;
import tech.pain.client.modules.impl.misc.NameProtect;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

public class WatermarkComponent extends DraggableHudElement {
   public WatermarkComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
      super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
   }

   public void render(CustomDrawContext ctx) {
      tech.pain.client.modules.impl.misc.MusicLyrics lyricsModule = tech.pain.client.modules.impl.misc.MusicLyrics.INSTANCE;
      if (tech.pain.client.modules.impl.render.Interface.INSTANCE.watermarkStyle.is("Dynamic Island") || lyricsModule.isEnabled() && lyricsModule.lyricsStyle.is("Island")) {
         this.renderIsland(ctx);
         return;
      }

      if (HudStyle.isRock()) {
         this.renderRock(ctx);
         return;
      }

      float x = this.getX();
      float y = this.getY();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      String name = "Pain";
      String fps = mc.getCurrentFps() + "fps";
      PlayerListEntry list = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
      float width = 92.5F + Fonts.REGULAR.getWidth(NameProtect.INSTANCE.isEnabled() ? NameProtect.getCustomName() : mc.player.getNameForScoreboard(), 7.75F) + Fonts.REGULAR.getWidth(list != null ? list.getLatency() + "ms" : "0ms", 7.25F) + Fonts.REGULAR.getWidth(fps, 7.25F);
      HudStyle.panel(ctx.getMatrices(), x - 0.5F, y - 1.5F, width, 14.25F, 5.0F, BorderRadius.all(3.0F), new ColorRGBA(80, 80, 80, 255));
      ctx.drawText(Fonts.ICONS.getFont(6.5F), "B", x + 3.5F, y + 3.0F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), name, x + 12.75F, y + 3.25F, HudStyle.title(new ColorRGBA(255, 255, 255, 255)));
      x += 34.0F;
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x + 4.0F, y + 5.25F, 2.0F, 2.0F, BorderRadius.all(0.5F), theme.getColor());
      ctx.drawText(Fonts.ICONS2.getFont(6.0F), "\uf007", x + 10.0F, y + 3.75F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), NameProtect.INSTANCE.isEnabled() ? NameProtect.getCustomName() : mc.player.getNameForScoreboard(), x + 18.0F, y + 3.25F, new ColorRGBA(255, 255, 255, 255));
      x += Fonts.REGULAR.getWidth(NameProtect.INSTANCE.isEnabled() ? NameProtect.getCustomName() : mc.player.getNameForScoreboard(), 7.75F) + 14.0F;
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x + 6.0F, y + 5.25F, 2.0F, 2.0F, BorderRadius.all(0.5F), theme.getColor());
      ctx.drawText(Fonts.ICONS2.getFont(6.0F), "\uf1eb", x + 12.0F, y + 3.65F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), list != null ? list.getLatency() + "ms" : "0ms", x + 21.5F, y + 3.25F, new ColorRGBA(255, 255, 255, 255));
      x += Fonts.REGULAR.getWidth(list != null ? list.getLatency() + "ms" : "0ms", 7.25F) + 21.0F;
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x + 4.5F, y + 5.25F, 2.0F, 2.0F, BorderRadius.all(0.5F), theme.getColor());
      ctx.drawText(Fonts.ICONS2.getFont(6.0F), "\uf624", x + 11.0F, y + 3.95F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), fps, x + 19.0F, y + 3.25F, new ColorRGBA(255, 255, 255, 255));
      float x2 = this.getX();
      float y2 = this.getY() + 15.0F;
      String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
      float var10000 = 54.0F + Fonts.REGULAR.getWidth(mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null ? mc.getCurrentServerEntry().address : "Неизвестно", 7.25F) + Fonts.REGULAR.getWidth(time, 7.25F);
      MsdfFont var10001 = Fonts.REGULAR;
      String var10002 = String.format("%.1f", Pain.getInstance().getServerHandler().getTPS());
      float width2 = var10000 + var10001.getWidth(var10002.replace(",", ".") + "tps", 7.25F);
      HudStyle.panel(ctx.getMatrices(), x2 - 0.5F, y2 - 1.5F, width2, 14.25F, 5.0F, BorderRadius.all(3.0F), new ColorRGBA(80, 80, 80, 255));
      ctx.drawText(Fonts.ICONS2.getFont(6.0F), "\uf0ac", x2 + 3.0F, y2 + 4.0F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null ? mc.getCurrentServerEntry().address : "Неизвестно", x2 + 11.5F, y2 + 3.5F, new ColorRGBA(255, 255, 255, 255));
      x2 += Fonts.REGULAR.getWidth(mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null ? mc.getCurrentServerEntry().address : "Неизвестно", 7.25F) + 13.0F;
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x2 + 4.0F, y2 + 5.25F, 2.0F, 2.0F, BorderRadius.all(0.5F), theme.getColor());
      ctx.drawText(Fonts.ICONS2.getFont(6.0F), "\uf017", x2 + 10.0F, y2 + 3.85F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), time, x2 + 18.5F, y2 + 3.25F, new ColorRGBA(255, 255, 255, 255));
      x2 += Fonts.REGULAR.getWidth(time, 7.25F) + 17.0F;
      DrawUtil.drawRoundedRect(ctx.getMatrices(), x2 + 5.0F, y2 + 5.25F, 2.0F, 2.0F, BorderRadius.all(0.5F), theme.getColor());
      ctx.drawText(Fonts.ICONS2.getFont(6.0F), "\uf68f", x2 + 11.5F, y2 + 3.5F, theme.getColor());
      ctx.drawText(Fonts.REGULAR.getFont(7.25F), String.format("%.1f", Pain.getInstance().getServerHandler().getTPS()).replace(",", ".") + "tps", x2 + 19.0F, y2 + 3.25F, new ColorRGBA(255, 255, 255, 255));
      this.width = width;
      this.height = 29.0F;
   }


   private void renderRock(CustomDrawContext ctx) {
      net.minecraft.client.util.math.MatrixStack m = ctx.getMatrices();
      float x = this.getX();
      float y = this.getY();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      String user = NameProtect.INSTANCE.isEnabled() ? NameProtect.getCustomName() : mc.player.getNameForScoreboard();
      PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
      String ping = (entry != null ? entry.getLatency() : 0) + "ms";
      String fps = mc.getCurrentFps() + "fps";
      String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
      String[] icons = {"\uf007", "\uf1eb", "\uf624", "\uf017"};
      String[] parts = {user, ping, fps, time};
      float h = 17.0F;
      float w = 8.0F + 9.0F + 3.0F + Fonts.MEDIUM.getWidth("Pain", 7.5F) + 6.0F;

      for (String part : parts) {
         w += 7.0F + 9.0F + Fonts.REGULAR.getWidth(part, 7.0F) + 2.0F;
      }

      w += 4.0F;
      HudStyle.rockPanel(m, x, y, w, h, 1.0F);
      ColorRGBA c1 = theme.getColor();
      ColorRGBA c2 = theme.getSecondColor();
      ctx.drawText(Fonts.ICONS.getFont(8.0F), "B", x + 7.0F, y + 4.5F, tech.pain.utility.render.display.base.Gradient.of(c1, c2, c1, c2));
      float cx = x + 19.0F;
      ctx.drawText(Fonts.MEDIUM.getFont(7.5F), "Pain", cx, y + 5.0F, new ColorRGBA(255, 255, 255, 255));
      cx += Fonts.MEDIUM.getWidth("Pain", 7.5F) + 6.0F;

      for (int i = 0; i < parts.length; ++i) {
         DrawUtil.drawRoundedRect(m, cx, y + 4.5F, 0.5F, 8.0F, BorderRadius.ZERO, new ColorRGBA(255, 255, 255, 30));
         cx += 5.0F;
         ctx.drawText(Fonts.ICONS2.getFont(6.0F), icons[i], cx, y + 5.75F, theme.getColor());
         cx += 9.0F;
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), parts[i], cx, y + 5.25F, new ColorRGBA(235, 235, 240, 235));
         cx += Fonts.REGULAR.getWidth(parts[i], 7.0F) + 4.0F;
      }

      float totalH = h;
      this.width = w;
      this.height = totalH;
   }

   // --- Dynamic Island
   private float islandW = 80.0F;
   private float islandH = 17.0F;
   private float islandVW;
   private float islandVH;
   private float islandMusic;
   private String islandLine = "";
   private String islandPrev = "";
   private long islandChanged;
   private long islandFrame;

   /**
    * Чёрный «остров» как на айфоне: в покое — лого и FPS, а если включён стиль лирикса Island
    * и играет музыка, остров растягивается и показывает только строку песни.
    */
   private void renderIsland(CustomDrawContext ctx) {
      net.minecraft.client.util.math.MatrixStack m = ctx.getMatrices();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      long now = System.currentTimeMillis();
      float dt = this.islandFrame == 0L ? 0.016F : Math.min(0.05F, (float)(now - this.islandFrame) / 1000.0F);
      this.islandFrame = now;
      tech.pain.client.modules.impl.misc.MusicLyrics lyrics = tech.pain.client.modules.impl.misc.MusicLyrics.INSTANCE;
      boolean lyricMode = lyrics.isEnabled() && lyrics.lyricsStyle.is("Island") && lyrics.shouldShow() && !lyrics.isSettingUp();
      String[] lines = lyricMode ? lyrics.displayLines() : null;
      String line = lines != null ? tech.pain.client.modules.impl.misc.MusicLyrics.plain(lines[0]) : "";
      String sub = lines != null && lines[1] != null ? tech.pain.client.modules.impl.misc.MusicLyrics.plain(lines[1]) : null;
      if (lyricMode && !line.equals(this.islandLine)) {
         this.islandPrev = this.islandLine;
         this.islandLine = line;
         this.islandChanged = now;
      }

      this.islandMusic += ((lyricMode ? 1.0F : 0.0F) - this.islandMusic) * Math.min(1.0F, dt * 9.0F);
      String fps = mc.getCurrentFps() + " fps";
      float nameW = Fonts.MEDIUM.getWidth("Pain", 7.5F);
      float fpsW = Fonts.REGULAR.getWidth(fps, 7.0F);
      float idleW = 9.0F + 9.0F + 3.0F + nameW + 7.0F + 3.0F + 4.0F + fpsW + 10.0F;
      float idleH = 17.0F;
      float maxText = 250.0F;
      float lineW = Math.min(maxText, Fonts.MEDIUM.getWidth(this.islandLine.isEmpty() ? "♪" : this.islandLine, 7.5F));
      float subW = sub == null ? 0.0F : Math.min(maxText, Fonts.REGULAR.getWidth(sub, 6.5F));
      float targetW = lyricMode ? Math.max(70.0F, 10.0F + 12.0F + 5.0F + Math.max(lineW, subW) + 12.0F) : idleW;
      float targetH = lyricMode ? (sub != null ? 29.0F : 20.0F) : idleH;
      // пружинка: остров слегка «перелетает» нужный размер и возвращается
      this.islandVW += ((targetW - this.islandW) * 170.0F - this.islandVW * 19.0F) * dt;
      this.islandVH += ((targetH - this.islandH) * 170.0F - this.islandVH * 19.0F) * dt;
      this.islandW += this.islandVW * dt;
      this.islandH += this.islandVH * dt;
      float w = Math.max(30.0F, this.islandW);
      float h = Math.max(12.0F, this.islandH);
      // остров растёт в стороны от центра элемента, а сам элемент остаётся размером с «покой»
      float cx = this.getX() + idleW / 2.0F;
      float x = cx - w / 2.0F;
      float y = this.getY();
      BorderRadius radius = BorderRadius.all(Math.min(h / 2.0F, 10.0F));
      DrawUtil.drawShadow(m, x, y, w, h, 12.0F, radius, new ColorRGBA(0, 0, 0, 120));
      DrawUtil.drawRoundedRect(m, x, y, w, h, radius, new ColorRGBA(0, 0, 0, 245));
      DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, radius, new ColorRGBA(255, 255, 255, 22));
      float idleA = 1.0F - this.islandMusic;
      float musicA = this.islandMusic;
      ColorRGBA c1 = theme.getColor();
      ColorRGBA c2 = theme.getSecondColor();
      if (idleA > 0.03F) {
         float ix = cx - idleW / 2.0F + 9.0F;
         float ty = y + h / 2.0F;
         ctx.drawText(Fonts.ICONS.getFont(8.0F), "B", ix, ty - 4.0F, tech.pain.utility.render.display.base.Gradient.of(c1.withAlpha(255.0F * idleA), c2.withAlpha(255.0F * idleA), c1.withAlpha(255.0F * idleA), c2.withAlpha(255.0F * idleA)));
         ix += 12.0F;
         ctx.drawText(Fonts.MEDIUM.getFont(7.5F), "Pain", ix, ty - 3.5F, new ColorRGBA(255, 255, 255, 255.0F * idleA));
         ix += nameW + 7.0F;
         // зелёная точка-индикатор, как у активности на айфоне
         DrawUtil.drawRoundedRect(m, ix, ty - 1.5F, 3.0F, 3.0F, BorderRadius.all(1.5F), new ColorRGBA(60, 220, 110, 255.0F * idleA));
         ix += 7.0F;
         ctx.drawText(Fonts.REGULAR.getFont(7.0F), fps, ix, ty - 3.25F, new ColorRGBA(215, 215, 222, 235.0F * idleA));
      }

      if (musicA > 0.03F) {
         float left = x + 10.0F;

         for (int i = 0; i < 3; ++i) {
            float bar = 2.0F + 5.0F * (0.5F + 0.5F * (float)Math.sin((double)now / 140.0 + (double)i * 1.9));
            DrawUtil.drawRoundedRect(m, left + (float)i * 3.2F, y + h / 2.0F + 3.5F - bar, 2.0F, bar, BorderRadius.all(1.0F), c1.mix(c2, (float)i / 2.0F).withAlpha(255.0F * musicA));
         }

         float tx = left + 16.0F;
         float tw = w - 16.0F - 22.0F;
         float t = Math.min(1.0F, (float)(now - this.islandChanged) / 220.0F);
         float ease = 1.0F - (1.0F - t) * (1.0F - t);
         float baseY = sub != null ? y + 6.0F : y + h / 2.0F - 3.5F;
         String shown = this.islandLine.isEmpty() ? "♪" : this.islandLine;
         if (t < 1.0F && !this.islandPrev.isEmpty()) {
            tech.pain.base.font.MsdfRenderer.renderText(Fonts.MEDIUM, this.islandPrev, 7.5F, new ColorRGBA(255, 255, 255, 255.0F * musicA * (1.0F - ease)).getRGB(), m.peek().getPositionMatrix(), tx, baseY - 4.0F * ease, 0.0F, true, 0.9F, 1.0F, tw);
         }

         tech.pain.base.font.MsdfRenderer.renderText(Fonts.MEDIUM, shown, 7.5F, new ColorRGBA(255, 255, 255, 255.0F * musicA * ease).getRGB(), m.peek().getPositionMatrix(), tx, baseY + 4.0F * (1.0F - ease), 0.0F, true, 0.9F, 1.0F, tw);
         if (sub != null) {
            tech.pain.base.font.MsdfRenderer.renderText(Fonts.REGULAR, sub, 6.5F, c1.mix(new ColorRGBA(255, 255, 255), 0.5F).withAlpha(230.0F * musicA * ease).getRGB(), m.peek().getPositionMatrix(), tx, baseY + 10.5F, 0.0F, true, 0.9F, 1.0F, tw);
         }
      }

      this.width = idleW;
      this.height = idleH;
   }
}
