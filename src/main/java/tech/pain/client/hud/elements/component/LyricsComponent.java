package tech.pain.client.hud.elements.component;

import java.util.List;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import tech.pain.Pain;
import tech.pain.base.font.Fonts;
import tech.pain.base.font.MsdfRenderer;
import tech.pain.base.music.LyricsService;
import tech.pain.base.music.SyncedLyrics;
import tech.pain.base.theme.Theme;
import tech.pain.client.hud.HudStyle;
import tech.pain.client.hud.elements.draggable.DraggableHudElement;
import tech.pain.client.modules.impl.misc.MusicLyrics;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.Gradient;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Отдельный элемент HUD «Лирикс»: трек, текущая строка (синхронно с позицией трека)
 * и следующая строка, полоса прогресса. Перетаскивается в чате как остальные элементы.
 */
public class LyricsComponent extends DraggableHudElement {
   private static final float MAX_TEXT = 240.0F;
   private float visible;
   private float boxWidth = 120.0F;
   private String current = "";
   private String previous = "";
   private long changedAt;

   public LyricsComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
      super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
   }

   private static boolean withNextTranslated(MusicLyrics module, String target) {
      return module != null && target != null && module.showNext.isEnabled();
   }

   @Override
   public void render(CustomDrawContext ctx) {
      MusicLyrics module = MusicLyrics.INSTANCE;
      boolean editing = mc.currentScreen instanceof ChatScreen;
      boolean show = module != null && module.shouldShow();
      if (module != null && module.lyricsStyle.is("Island")) {
         // в стиле Island текст живёт в «острове» ватермарки, отдельная плашка не нужна
         this.visible = 0.0F;
         this.width = this.boxWidth;
         this.height = 30.0F;
         return;
      }

      this.visible += ((show || editing ? 1.0F : 0.0F) - this.visible) * 0.12F;
      if (this.visible < 0.02F) {
         this.width = this.boxWidth;
         this.height = 30.0F;
         return;
      }

      MatrixStack m = ctx.getMatrices();
      Theme theme = Pain.getInstance().getThemeManager().getCurrentTheme();
      float a = this.visible;
      String track = module != null && module.showTrack.isEnabled() ? LyricsService.getTrack() : "";
      String line;
      String next = "";
      String badge;
      boolean synced = LyricsService.hasSynced();
      if (!show) {
         line = "Лирикс";
         badge = "HUD";
      } else if (synced) {
         List<SyncedLyrics.Line> lines = LyricsService.getSynced();
         int idx = LyricsService.getSyncedIndex();
         line = idx >= 0 ? lines.get(idx).text() : "";
         if (line.isEmpty()) {
            line = "♪";
         }

         for (int i = Math.max(0, idx + 1); i < lines.size(); ++i) {
            if (!lines.get(i).text().isEmpty()) {
               next = lines.get(i).text();
               break;
            }
         }

         badge = "LRC";
      } else {
         line = LyricsService.getLine();
         if (line.isEmpty()) {
            line = LyricsService.getSyncedState() == 1 ? "ищу текст..." : LyricsService.getStatus();
         }

         badge = "AI";
      }

      line = MusicLyrics.plain(line);
      next = MusicLyrics.plain(next);
      if (!line.equals(this.current)) {
         this.previous = this.current;
         this.current = line;
         this.changedAt = System.currentTimeMillis();
      }

      String target = LyricsService.getTranslateTarget();
      String translated = null;
      boolean realLine = show && !badge.equals("HUD") && (synced || !LyricsService.getLine().isEmpty()) && !this.current.equals("♪");
      if (target != null && realLine) {
         translated = tech.pain.base.music.Translator.get(this.current, target);
         if (translated != null && translated.equalsIgnoreCase(this.current)) {
            translated = null;
         }
      }

      boolean replace = translated != null && module.translateView.is("Вместо");
      String mainLine = replace ? MusicLyrics.plain(translated) : this.current;
      String subLine = translated != null && !replace ? MusicLyrics.plain(translated) : null;
      if (withNextTranslated(module, target) && !next.isEmpty()) {
         String nt = tech.pain.base.music.Translator.get(next, target);
         if (nt != null && module.translateView.is("Вместо")) {
            next = nt;
         }
      }

      boolean withNext = module != null && module.showNext.isEnabled() && !next.isEmpty();
      float lineSize = 8.0F;
      float targetW = Math.max(Fonts.MEDIUM.getWidth(mainLine, lineSize), track.isEmpty() ? 60.0F : Fonts.REGULAR.getWidth(track, 6.0F) + 20.0F);
      if (withNext) {
         targetW = Math.max(targetW, Fonts.REGULAR.getWidth(next, 6.5F));
      }

      if (subLine != null) {
         targetW = Math.max(targetW, Fonts.REGULAR.getWidth(subLine, 7.0F));
      }

      targetW = Math.min(MAX_TEXT, targetW);
      this.boxWidth += (targetW + 24.0F - this.boxWidth) * 0.15F;
      float x = this.getX();
      float y = this.getY();
      float w = this.boxWidth;
      float h = 8.0F + (track.isEmpty() ? 0.0F : 10.0F) + 11.0F + (subLine != null ? 10.0F : 0.0F) + (withNext ? 10.0F : 0.0F) + 7.0F;
      if (HudStyle.isRock()) {
         HudStyle.rockPanel(m, x, y, w, h, a);
      } else {
         HudStyle.panel(m, x, y, w, h, 8.0F, BorderRadius.all(5.0F), new ColorRGBA(80, 80, 80, 255.0F * a));
      }

      float ty = y + 6.0F;
      long now = System.currentTimeMillis();
      boolean active = show && LyricsService.isPlaying();

      for (int i = 0; i < 3; ++i) {
         float bar = active ? 2.0F + 4.0F * (0.5F + 0.5F * (float)Math.sin((double)now / 140.0 + (double)i * 1.9)) : 2.0F;
         DrawUtil.drawRoundedRect(m, x + 7.0F + (float)i * 2.8F, ty + 6.5F - bar, 1.8F, bar, BorderRadius.all(0.9F), theme.getColor().mix(theme.getSecondColor(), (float)i / 2.0F).withAlpha(255.0F * a));
      }

      float bw = Fonts.MEDIUM.getWidth(badge, 5.0F) + 6.0F;
      DrawUtil.drawRoundedRect(m, x + w - 7.0F - bw, ty, bw, 8.0F, BorderRadius.all(2.5F), theme.getColor().withAlpha(60.0F * a));
      ctx.drawText(Fonts.MEDIUM.getFont(5.0F), badge, x + w - 4.0F - bw, ty + 2.25F, new ColorRGBA(255, 255, 255, 220.0F * a));
      if (!track.isEmpty()) {
         MsdfRenderer.renderText(Fonts.REGULAR, track, 6.0F, new ColorRGBA(200, 200, 210, 160.0F * a).getRGB(), m.peek().getPositionMatrix(), x + 18.0F, ty + 1.5F, 0.0F, true, 0.85F, 1.0F, w - 30.0F - bw);
         ty += 10.0F;
      } else {
         ty += 1.0F;
      }

      float t = Math.min(1.0F, (float)(now - this.changedAt) / 220.0F);
      float ease = 1.0F - (1.0F - t) * (1.0F - t);
      float lineY = ty + 2.0F;
      if (t < 1.0F && !this.previous.isEmpty()) {
         MsdfRenderer.renderText(Fonts.MEDIUM, this.previous, lineSize, new ColorRGBA(245, 245, 250, 255.0F * a * (1.0F - ease)).getRGB(), m.peek().getPositionMatrix(), x + 7.0F, lineY - 5.0F * ease, 0.0F, true, 0.9F, 1.0F, w - 14.0F);
      }

      boolean customColors = module != null && module.textColorMode.is("Свой");
      ColorRGBA c1 = (customColors ? module.textColor.getColor() : new ColorRGBA(255, 255, 255)).withAlpha(255.0F * a * ease);
      ColorRGBA c2 = (customColors ? module.sideColor.getColor() : theme.getColor().mix(new ColorRGBA(255, 255, 255), 0.55F)).withAlpha(255.0F * a * ease);
      MsdfRenderer.renderText(Fonts.MEDIUM, mainLine, lineSize, c1.getRGB(), m.peek().getPositionMatrix(), x + 7.0F, lineY + 5.0F * (1.0F - ease), 0.0F, true, 0.9F, 1.0F, w - 14.0F);
      ty += 11.0F;
      if (subLine != null) {
         MsdfRenderer.renderText(Fonts.REGULAR, subLine, 7.0F, c2.getRGB(), m.peek().getPositionMatrix(), x + 7.0F, ty + 1.0F, 0.0F, true, 0.9F, 1.0F, w - 14.0F);
         ty += 10.0F;
      }
      if (withNext) {
         MsdfRenderer.renderText(Fonts.REGULAR, next, 6.5F, new ColorRGBA(200, 200, 210, 120.0F * a).getRGB(), m.peek().getPositionMatrix(), x + 7.0F, ty + 1.5F, 0.0F, true, 0.85F, 1.0F, w - 14.0F);
         ty += 10.0F;
      }

      long len = LyricsService.getLengthMs();
      float progress = len > 0L ? Math.min(1.0F, (float)LyricsService.getPositionMs() / (float)len) : 0.0F;
      float px = x + 7.0F;
      float pw = w - 14.0F;
      float py = y + h - 5.0F;
      DrawUtil.drawRoundedRect(m, px, py, pw, 1.5F, BorderRadius.all(0.75F), new ColorRGBA(255, 255, 255, 25.0F * a));
      if (progress > 0.0F) {
         ColorRGBA g1 = theme.getColor().withAlpha(255.0F * a);
         ColorRGBA g2 = theme.getSecondColor().withAlpha(255.0F * a);
         DrawUtil.drawRoundedRect(m, px, py, Math.max(1.5F, pw * progress), 1.5F, BorderRadius.all(0.75F), Gradient.of(g2, g2, g1, g1));
      }

      this.width = w;
      this.height = h;
   }
}
