package tech.pain.client.screens.swarm;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import tech.pain.base.swarm.SwarmBot;
import tech.pain.base.swarm.SwarmManager;

/**
 * Меню ботов роя: сервер, ник, количество, запуск/стоп и список ботов.
 */
public class SwarmScreen extends Screen {
   private TextFieldWidget serverField;
   private TextFieldWidget nickField;
   private TextFieldWidget countField;

   public SwarmScreen() {
      super(Text.of("Swarm — Меню ботов"));
   }

   @Override
   protected void init() {
      int cx = this.width / 2;

      this.serverField = new TextFieldWidget(this.textRenderer, cx - 100, 40, 200, 18, Text.of("server"));
      this.serverField.setMaxLength(128);
      this.serverField.setText(SwarmManager.INSTANCE.getHost() + ":" + SwarmManager.INSTANCE.getPort());
      this.addDrawableChild(this.serverField);

      this.nickField = new TextFieldWidget(this.textRenderer, cx - 100, 75, 200, 18, Text.of("nick"));
      this.nickField.setMaxLength(32);
      this.nickField.setText(SwarmManager.INSTANCE.getBaseNick());
      this.addDrawableChild(this.nickField);

      this.countField = new TextFieldWidget(this.textRenderer, cx - 100, 110, 200, 18, Text.of("count"));
      this.countField.setMaxLength(3);
      this.countField.setText("3");
      this.addDrawableChild(this.countField);

      this.addDrawableChild(ButtonWidget.builder(Text.of("Запустить"), b -> this.startBots()).dimensions(cx - 100, 140, 95, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.of("Стоп всех"), b -> SwarmManager.INSTANCE.stopAll()).dimensions(cx + 5, 140, 95, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.of("Закрыть"), b -> this.close()).dimensions(cx - 100, 165, 200, 20).build());
   }

   private void startBots() {
      String raw = this.serverField.getText().trim();
      String host = raw;
      int port = 25565;
      int idx = raw.lastIndexOf(58);
      if (idx > 0) {
         host = raw.substring(0, idx);

         try {
            port = Integer.parseInt(raw.substring(idx + 1));
         } catch (NumberFormatException var7) {
            port = 25565;
         }
      }

      SwarmManager.INSTANCE.setTarget(host, port);
      String nick = this.nickField.getText().trim();
      if (!nick.isEmpty()) {
         SwarmManager.INSTANCE.setBaseNick(nick);
      }

      int count = 3;

      try {
         count = Integer.parseInt(this.countField.getText().trim());
      } catch (NumberFormatException var6) {
         count = 3;
      }

      SwarmManager.INSTANCE.start(Math.max(1, Math.min(count, 50)));
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      int cx = this.width / 2;
      ctx.drawText(this.textRenderer, this.title.getString(), cx - this.textRenderer.getWidth(this.title) / 2, 14, -1, false);
      ctx.drawText(this.textRenderer, "Сервер (ip:port):", cx - 100, 30, -1, false);
      ctx.drawText(this.textRenderer, "Ник ботов:", cx - 100, 65, -1, false);
      ctx.drawText(this.textRenderer, "Кол-во:", cx - 100, 100, -1, false);
      ctx.drawText(this.textRenderer, "Активно ботов: " + SwarmManager.INSTANCE.count(), cx - 100, 192, -16711936, false);

      int y = 205;
      for (SwarmBot bot : SwarmManager.INSTANCE.getBots()) {
         ctx.drawText(this.textRenderer, bot.getName() + " — " + (bot.isAlive() ? "онлайн" : "офф"), cx - 100, y, -5592406, false);
         y += 10;
      }

      if (this.client == null || this.client.world == null) {
         ctx.drawText(this.textRenderer, "Зайди сам на этот сервер, иначе боты не войдут (нет реестров)", cx - 100, y + 6, -43691, false);
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }
}
