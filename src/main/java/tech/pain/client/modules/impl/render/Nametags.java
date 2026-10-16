package tech.pain.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.render.EventRender2D;
import tech.pain.base.events.impl.server.EventPacket;
import tech.pain.base.font.Font;
import tech.pain.base.font.Fonts;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.math.MathUtil;
import tech.pain.utility.math.ProjectionUtil;
import tech.pain.utility.render.display.base.BorderRadius;
import tech.pain.utility.render.display.base.CustomDrawContext;
import tech.pain.utility.render.display.base.color.ColorRGBA;
import tech.pain.utility.render.display.shader.DrawUtil;

/**
 * Плашки над игроками: здоровье цветной цифрой и полоской, ник, расстояние, выбитые тотемы,
 * сверху — предметы в руках и броня. Ванильные ники при включённом модуле скрываются.
 */
@ModuleAnnotation(
   name = "Nametags",
   category = Category.RENDER,
   description = "Плашки над игроками: здоровье, ник, расстояние, тотемы, броня и предметы"
)
public final class Nametags extends Module {
   public static final Nametags INSTANCE = new Nametags();
   private final NumberSetting size = new NumberSetting("Размер", 7.0F, 5.0F, 12.0F, 0.5F);
   private final NumberSetting range = new NumberSetting("Дальность показа", 128.0F, 8.0F, 256.0F, 4.0F);
   private final BooleanSetting shrink = new BooleanSetting("Уменьшать вдали", true);
   private final NumberSetting minScale = new NumberSetting("Мин. размер вдали", 0.6F, 0.3F, 1.0F, 0.05F, (java.util.function.Supplier<Boolean>)() -> this.shrink.isEnabled());
   private final BooleanSetting showHp = new BooleanSetting("Здоровье", true);
   private final BooleanSetting hpBar = new BooleanSetting("Полоска здоровья", true);
   private final BooleanSetting showDistance = new BooleanSetting("Расстояние", true);
   private final BooleanSetting showTotems = new BooleanSetting("Попы тотемов", true);
   private final BooleanSetting showItems = new BooleanSetting("Броня и предметы", true);
   private final BooleanSetting background = new BooleanSetting("Подложка", true);
   private final BooleanSetting showPing = new BooleanSetting("Пинг", true);
   private final BooleanSetting showEnchants = new BooleanSetting("Зачарования над предметами", true, () -> this.showItems.isEnabled());
   private final BooleanSetting spread = new BooleanSetting("Раздвигать при наложении", true);
   // сдвиг тега вверх, чтобы не лез на соседний; сглаживается, чтобы теги не дёргались
   private final Map<UUID, Float> lift = new HashMap<>();
   private final Map<UUID, Integer> totemPops = new HashMap<>();

   private Nametags() {
   }

   @EventTarget
   private void onPacket(EventPacket event) {
      Packet<?> packet = event.getPacket();
      if (packet instanceof EntityStatusS2CPacket status && status.getStatus() == 35 && mc.world != null) {
         Entity entity = status.getEntity(mc.world);
         if (entity instanceof PlayerEntity player) {
            this.totemPops.merge(player.getUuid(), 1, Integer::sum);
         }
      }
   }

   @EventTarget
   private void onRender2D(EventRender2D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      CustomDrawContext ctx = event.getContext();
      MatrixStack m = ctx.getMatrices();
      float maxDist = this.range.getCurrent();
      List<PlayerEntity> players = new ArrayList<>();

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player && player.isAlive() && mc.player.distanceTo(player) <= maxDist) {
            players.add(player);
         }
      }

      // дальние рисуем первыми, чтобы ближние плашки лежали поверх
      players.sort((a, b) -> Float.compare(mc.player.distanceTo(b), mc.player.distanceTo(a)));

      // раскладка: где тег стоит и сколько места занимает
      List<Object[]> tags = new ArrayList<>();

      for (PlayerEntity player : players) {
         Vec3d feet = MathUtil.interpolate(player);
         Vec3d screen = ProjectionUtil.worldSpaceToScreenSpace(new Vec3d(feet.x, feet.y + (double)player.getHeight() + 0.45, feet.z));
         if (screen.z <= 0.0 || screen.z >= 1.0) {
            continue;
         }

         float dist = mc.player.distanceTo(player);
         float scale = this.shrink.isEnabled() ? MathHelper.lerp(MathHelper.clamp((dist - 6.0F) / Math.max(1.0F, maxDist * 0.5F - 6.0F), 0.0F, 1.0F), 1.0F, this.minScale.getCurrent()) : 1.0F;
         float[] size = this.measure(player, dist);
         tags.add(new Object[]{player, (float)screen.x, (float)screen.y, scale, size[0] * scale, size[1] * scale, dist});
      }

      // ближние стоят на месте, дальние поднимаются над теми, на кого налезают
      List<float[]> placed = new ArrayList<>();

      for (int i = tags.size() - 1; i >= 0; --i) {
         Object[] t = tags.get(i);
         float tx = (Float)t[1];
         float ty = (Float)t[2];
         float tw = (Float)t[4];
         float th = (Float)t[5];
         float target = 0.0F;
         if (this.spread.isEnabled()) {
            for (int guard = 0; guard < 8; ++guard) {
               boolean moved = false;

               for (float[] r : placed) {
                  float bottom = ty - target;
                  if (tx - tw / 2.0F < r[2] && tx + tw / 2.0F > r[0] && bottom - th < r[3] && bottom > r[1]) {
                     target += bottom - r[1] + 2.0F;
                     moved = true;
                  }
               }

               if (!moved) {
                  break;
               }
            }
         }

         UUID id = ((PlayerEntity)t[0]).getUuid();
         float shown = this.lift.getOrDefault(id, target);
         shown += (target - shown) * 0.3F;
         this.lift.put(id, shown);
         t[2] = ty - shown;
         placed.add(new float[]{tx - tw / 2.0F, ty - target - th, tx + tw / 2.0F, ty - target});
      }

      if (this.lift.size() > 64) {
         this.lift.clear();
      }

      for (Object[] t : tags) {
         m.push();
         m.translate((Float)t[1], (Float)t[2], 0.0F);
         m.scale((Float)t[3], (Float)t[3], 1.0F);
         this.drawTag(ctx, m, (PlayerEntity)t[0], (Float)t[6]);
         m.pop();
      }
   }

   /** Ширина и полная высота тега (с предметами и зачарованиями) в его собственных единицах. */
   private float[] measure(PlayerEntity player, float dist) {
      float fs = this.size.getCurrent();
      Font font = Fonts.MEDIUM.getFont(fs);
      Font small = Fonts.REGULAR.getFont(fs * 0.86F);
      float pad = fs * 0.62F;
      float gap = fs * 0.55F;
      float w = pad * 2.0F + font.width(player.getName().getString()) + font.width("20") + fs * 0.9F + gap;
      if (this.showDistance.isEnabled()) {
         w += small.width(Math.round(dist) + "м") + gap;
      }

      if (this.showPing.isEnabled()) {
         w += small.width("999ms") + gap;
      }

      float h = fs + pad * 1.25F;
      if (this.showItems.isEnabled()) {
         h += 2.0F + 16.0F * (fs / 7.0F * 0.75F) + (this.showEnchants.isEnabled() ? fs * 1.6F : 0.0F);
      }

      return new float[]{w, h};
   }

   /** Плашка рисуется вокруг точки (0, 0): низ плашки стоит на ней, центр по горизонтали. */
   private void drawTag(CustomDrawContext ctx, MatrixStack m, PlayerEntity player, float dist) {
      float fs = this.size.getCurrent();
      Font font = Fonts.MEDIUM.getFont(fs);
      Font small = Fonts.REGULAR.getFont(fs * 0.86F);
      String name = player.getName().getString();
      boolean friend = Pain.getInstance().getFriendManager().isFriend(name);
      float hp = player.getHealth() + player.getAbsorptionAmount();
      float ratio = MathHelper.clamp(player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0.0F, 1.0F);
      ColorRGBA hpColor = healthColor(ratio);
      String hpText = this.showHp.isEnabled() ? String.valueOf(Math.round(hp)) : "";
      String distText = this.showDistance.isEnabled() ? Math.round(dist) + "м" : "";
      int ping = this.ping(player);
      String pingText = this.showPing.isEnabled() && ping >= 0 ? ping + "ms" : "";
      int pops = this.totemPops.getOrDefault(player.getUuid(), 0);
      String popText = this.showTotems.isEnabled() && pops > 0 ? "-" + pops : "";
      float pad = fs * 0.62F;
      float gap = fs * 0.55F;
      float hpW = hpText.isEmpty() ? 0.0F : font.width(hpText) + fs * 0.9F;
      float nameW = font.width(name);
      float distW = distText.isEmpty() ? 0.0F : small.width(distText);
      float popW = popText.isEmpty() ? 0.0F : font.width(popText);
      float pingW = pingText.isEmpty() ? 0.0F : small.width(pingText);
      float w = pad * 2.0F + nameW + (hpW > 0.0F ? hpW + gap : 0.0F) + (distW > 0.0F ? distW + gap : 0.0F) + (pingW > 0.0F ? pingW + gap : 0.0F) + (popW > 0.0F ? popW + gap : 0.0F);
      float h = fs + pad * 1.25F;
      float x = -w / 2.0F;
      float y = -h;
      float radius = h * 0.36F;
      if (this.background.isEnabled()) {
         DrawUtil.drawRoundedRect(m, x, y, w, h, BorderRadius.all(radius), new ColorRGBA(12, 12, 17, 172));
         DrawUtil.drawRoundedBorder(m, x, y, w, h, -0.5F, BorderRadius.all(radius), friend ? new ColorRGBA(110, 235, 150, 150) : new ColorRGBA(255, 255, 255, 38));
      }

      float cx = x + pad;
      float ty = y + (h - fs) / 2.0F - fs * 0.04F;
      if (hpW > 0.0F) {
         // здоровье — цветная «таблетка» слева
         float ph = fs + pad * 0.5F;
         DrawUtil.drawRoundedRect(m, cx - fs * 0.25F, y + (h - ph) / 2.0F, hpW, ph, BorderRadius.all(ph * 0.4F), hpColor.withAlpha(60.0F));
         ctx.drawText(font, hpText, cx - fs * 0.25F + (hpW - font.width(hpText)) / 2.0F, ty, hpColor);
         cx += hpW + gap - fs * 0.25F;
      }

      ctx.drawText(font, name, cx, ty, friend ? new ColorRGBA(130, 240, 165) : new ColorRGBA(248, 248, 252));
      cx += nameW + gap;
      if (distW > 0.0F) {
         ctx.drawText(small, distText, cx, ty + fs * 0.08F, new ColorRGBA(170, 172, 188));
         cx += distW + gap;
      }

      if (pingW > 0.0F) {
         // пинг: зелёный — хороший, жёлтый — так себе, красный — лагает
         ColorRGBA pc = ping < 80 ? new ColorRGBA(120, 230, 140) : ping < 160 ? new ColorRGBA(240, 205, 90) : new ColorRGBA(245, 95, 85);
         ctx.drawText(small, pingText, cx, ty + fs * 0.08F, pc);
         cx += pingW + gap;
      }

      if (popW > 0.0F) {
         ctx.drawText(font, popText, cx, ty, new ColorRGBA(255, 205, 90));
      }

      if (this.hpBar.isEnabled()) {
         float bw = w - radius * 2.0F;
         float by = y + h - 1.6F;
         DrawUtil.drawRoundedRect(m, x + radius, by, bw, 1.2F, BorderRadius.all(0.6F), new ColorRGBA(255, 255, 255, 28));
         DrawUtil.drawRoundedRect(m, x + radius, by, Math.max(1.2F, bw * ratio), 1.2F, BorderRadius.all(0.6F), hpColor);
      }

      if (this.showItems.isEnabled()) {
         this.drawItems(ctx, m, player, y - 2.0F);
      }
   }

   /** Ряд предметов над плашкой: правая рука, броня сверху вниз, левая рука. Пустые слоты пропускаются. */
   private void drawItems(CustomDrawContext ctx, MatrixStack m, PlayerEntity player, float bottom) {
      List<ItemStack> stacks = new ArrayList<>();
      EquipmentSlot[] order = new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

      for (EquipmentSlot slot : order) {
         ItemStack stack = player.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            stacks.add(stack);
         }
      }

      if (stacks.isEmpty()) {
         return;
      }

      float s = this.size.getCurrent() / 7.0F * 0.75F;
      float step = 16.0F * s + 1.0F;
      float x = -step * (float)stacks.size() / 2.0F;

      for (ItemStack stack : stacks) {
         m.push();
         m.translate(x, bottom - 16.0F * s, 0.0F);
         m.scale(s, s, 1.0F);
         ctx.drawItem(stack, 0, 0);
         ctx.drawStackOverlay(mc.textRenderer, stack, 0, 0);
         m.pop();
         if (this.showEnchants.isEnabled()) {
            this.drawEnchants(ctx, stack, x + 8.0F * s, bottom - 16.0F * s - 1.0F);
         }

         x += step;
      }
   }

   /** Пинг из списка игроков сервера или -1, если его нет. */
   private int ping(PlayerEntity player) {
      if (mc.getNetworkHandler() == null) {
         return -1;
      }

      net.minecraft.client.network.PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
      return entry == null ? -1 : entry.getLatency();
   }

   /** До трёх зачарований столбиком над предметом: короткое имя и уровень. */
   private void drawEnchants(CustomDrawContext ctx, ItemStack stack, float cx, float bottom) {
      net.minecraft.component.type.ItemEnchantmentsComponent enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
      if (enchants.isEmpty()) {
         return;
      }

      Font font = Fonts.MEDIUM.getFont(this.size.getCurrent() * 0.62F);
      List<String> lines = new ArrayList<>();

      for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> e : enchants.getEnchantmentEntries()) {
         String id = e.getKey().getIdAsString();
         String path = id.contains(":") ? id.substring(id.indexOf(58) + 1) : id;
         lines.add(shortName(path) + (e.getIntValue() > 1 ? String.valueOf(e.getIntValue()) : ""));
         if (lines.size() >= 3) {
            break;
         }
      }

      float y = bottom;

      for (String line : lines) {
         y -= font.height() + 0.5F;
         ctx.drawText(font, line, cx - font.width(line) / 2.0F, y, new ColorRGBA(200, 160, 255));
      }
   }

   private static String shortName(String path) {
      return switch (path) {
         case "sharpness" -> "Ост";
         case "protection" -> "Защ";
         case "blast_protection" -> "Взр";
         case "fire_protection" -> "Огн";
         case "projectile_protection" -> "Снар";
         case "unbreaking" -> "Проч";
         case "mending" -> "Поч";
         case "fire_aspect" -> "Заг";
         case "knockback" -> "Отд";
         case "efficiency" -> "Эфф";
         case "feather_falling" -> "Пад";
         case "thorns" -> "Шип";
         case "power" -> "Сил";
         case "punch" -> "Отбр";
         case "flame" -> "Плам";
         case "infinity" -> "Беск";
         case "looting" -> "Доб";
         case "sweeping_edge" -> "Разм";
         case "depth_strider" -> "Глуб";
         case "respiration" -> "Дых";
         case "density" -> "Плот";
         case "breach" -> "Проб";
         case "wind_burst" -> "Ветер";
         default -> path.length() > 4 ? path.substring(0, 4) : path;
      };
   }

   /** Зелёный при полном здоровье, жёлтый на половине, красный у нуля. */
   private static ColorRGBA healthColor(float ratio) {
      float r = ratio > 0.5F ? MathHelper.lerp((ratio - 0.5F) * 2.0F, 245.0F, 95.0F) : 245.0F;
      float g = ratio > 0.5F ? MathHelper.lerp((ratio - 0.5F) * 2.0F, 205.0F, 230.0F) : MathHelper.lerp(ratio * 2.0F, 80.0F, 205.0F);
      float b = ratio > 0.5F ? MathHelper.lerp((ratio - 0.5F) * 2.0F, 90.0F, 130.0F) : MathHelper.lerp(ratio * 2.0F, 85.0F, 90.0F);
      return new ColorRGBA((int)r, (int)g, (int)b);
   }
}
