package tech.pain.client.modules.impl.player;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import com.darkmagician6.eventapi.EventTarget;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import tech.pain.Pain;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.base.events.impl.render.EventRender3D;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;
import tech.pain.utility.render.level.Render3DUtil;

/**
 * Бот для сундука Вардена: по клановому хому приходит к сундуку с таймером-голограммой,
 * ждёт открытия, забирает ценный лут, отходит, сдаёт добычу на склад и пополняет запас еды и инвизок.
 * Названия хомов задаются командой ^warden.
 */
@ModuleAnnotation(
   name = "AutoWarden",
   category = Category.PLAYER,
   description = "Сам лутает сундук Вардена по таймеру, сдаёт лут на склад и пополняет припасы"
)
public final class AutoWarden extends Module {
   public static final AutoWarden INSTANCE = new AutoWarden();
   private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
   private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*(?:мин|м\\b|min|m\\b)");
   private static final Pattern SECONDS = Pattern.compile("(\\d+)\\s*(?:сек|с\\b|sec|s\\b)");
   private static final Set<Item> VALUABLE = Set.of(Items.TOTEM_OF_UNDYING, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.ELYTRA, Items.BEACON, Items.DRAGON_HEAD, Items.PLAYER_HEAD, Items.DIAMOND, Items.ENDER_EYE, Items.FIREWORK_ROCKET, Items.PHANTOM_MEMBRANE, Items.TRIPWIRE_HOOK, Items.SNOWBALL, Items.SPLASH_POTION, Items.TNT, Items.VILLAGER_SPAWN_EGG, Items.NAME_TAG, Items.GOAT_HORN, Items.AMETHYST_SHARD, Items.IRON_NUGGET, Items.POPPED_CHORUS_FRUIT, Items.PAPER);
   private final tech.pain.client.modules.api.setting.impl.StringSetting wardenServer = new tech.pain.client.modules.api.setting.impl.StringSetting("Анка вардена", "an113");
   private final tech.pain.client.modules.api.setting.impl.StringSetting lootHome = new tech.pain.client.modules.api.setting.impl.StringSetting("Хом вардена", "warden");
   private final tech.pain.client.modules.api.setting.impl.StringSetting storageServer = new tech.pain.client.modules.api.setting.impl.StringSetting("Анка склада", "an114");
   private boolean homeSent;
   private final BooleanSetting takeAll = new BooleanSetting("Брать всё подряд", false);
   private final BooleanSetting timedOnly = new BooleanSetting("Только сундук с таймером", true);
   private final BooleanSetting deposit = new BooleanSetting("Сдавать лут на склад", true);
   private final BooleanSetting supplies = new BooleanSetting("Брать инвизки на складе", true);
   private final BooleanSetting autoEat = new BooleanSetting("Есть и пить инвизку", true);
   private final NumberSetting openDelay = new NumberSetting("Повтор открытия (мс)", 150.0F, 50.0F, 1500.0F, 10.0F);
   private final NumberSetting retreat = new NumberSetting("Отход после лута (блоков)", 25.0F, 0.0F, 60.0F, 1.0F);
   private final BooleanSetting log = new BooleanSetting("Писать этапы в чат", true);
   private State state = State.IDLE;
   private long stateSince;
   private long lastAction;
   private BlockPos chest;
   private long opensAt = -1L;
   private int emptyChecks;
   private int useTicks;
   private int savedSlot = -1;
   private boolean commandSent;
   private Vec3d retreatFrom;

   private enum State {
      IDLE, TO_FARM, FIND_CHEST, WALK_TO_CHEST, WAIT_TIMER, OPEN_CHEST, LOOT, RETREAT, TO_STORAGE, FIND_STORAGE, WALK_TO_STORAGE, OPEN_STORAGE, DEPOSIT, FIND_SUPPLY, WALK_TO_SUPPLY, OPEN_SUPPLY, TAKE_SUPPLY
   }

   private AutoWarden() {
   }

   // ------------------------------------------------------------- хомы и команда

   /** Ключи: warden — анка вардена, home — хом вардена, storage — анка склада. */
   private tech.pain.client.modules.api.setting.impl.StringSetting field(String key) {
      return switch (key) {
         case "storage" -> this.storageServer;
         case "warden" -> this.wardenServer;
         default -> this.lootHome;
      };
   }

   public void setHome(String key, String name) {
      this.field(key).set(name == null ? "" : name.trim());
   }

   public String getHome(String key) {
      String value = this.field(key).get().trim();
      // команду можно вписать и со слэшем, и без
      return value.startsWith("/") ? value.substring(1) : value;
   }

   // ------------------------------------------------------------------ жизненный цикл

   @Override
   public void onEnable() {
      super.onEnable();
      this.chest = null;
      this.opensAt = -1L;
      this.useTicks = 0;
      this.go(State.TO_FARM);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.stopWalking();
      this.releaseUse();
      this.state = State.IDLE;
   }

   private void go(State next) {
      this.state = next;
      this.stateSince = System.currentTimeMillis();
      this.commandSent = false;
      this.emptyChecks = 0;
      if (this.log.isEnabled() && mc.inGameHud != null) {
         mc.inGameHud.getChatHud().addMessage(Text.literal("§c[Warden] §7" + describe(next)));
      }
   }

   private static String describe(State s) {
      return switch (s) {
         case IDLE -> "остановлен";
         case TO_FARM -> "перехожу на анку вардена";
         case FIND_CHEST -> "ищу сундук";
         case WALK_TO_CHEST -> "иду к сундуку";
         case WAIT_TIMER -> "жду таймер";
         case OPEN_CHEST -> "открываю сундук";
         case LOOT -> "забираю лут";
         case RETREAT -> "отхожу";
         case TO_STORAGE -> "перехожу на анку склада";
         case FIND_STORAGE -> "ищу сундук склада";
         case WALK_TO_STORAGE -> "иду к складу";
         case OPEN_STORAGE -> "открываю склад";
         case DEPOSIT -> "сдаю лут";
         case FIND_SUPPLY -> "ищу сундук с инвизками";
         case WALK_TO_SUPPLY -> "иду за припасами";
         case OPEN_SUPPLY -> "открываю припасы";
         case TAKE_SUPPLY -> "беру инвизки";
      };
   }

   private long inState() {
      return System.currentTimeMillis() - this.stateSince;
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null) {
         return;
      }

      // пока едим или пьём — остальное ждёт
      if (this.useTicks > 0) {
         if (--this.useTicks == 0) {
            this.releaseUse();
         }

         return;
      }

      switch (this.state) {
         case TO_FARM -> this.teleport("loot", State.FIND_CHEST);
         case FIND_CHEST -> this.findChest();
         case WALK_TO_CHEST -> this.walk(this.chest, State.WAIT_TIMER, State.FIND_CHEST);
         case WAIT_TIMER -> this.waitTimer();
         case OPEN_CHEST -> this.open(this.chest, State.LOOT, State.FIND_CHEST);
         case LOOT -> this.loot();
         case RETREAT -> this.retreat();
         case TO_STORAGE -> this.teleport("storage", State.FIND_STORAGE);
         case FIND_STORAGE -> this.findContainer(State.WALK_TO_STORAGE, false);
         case WALK_TO_STORAGE -> this.walk(this.chest, State.OPEN_STORAGE, State.FIND_STORAGE);
         case OPEN_STORAGE -> this.open(this.chest, State.DEPOSIT, State.TO_FARM);
         case DEPOSIT -> this.depositLoot();
         case FIND_SUPPLY -> this.findContainer(State.WALK_TO_SUPPLY, true);
         case WALK_TO_SUPPLY -> this.walk(this.chest, State.OPEN_SUPPLY, State.TO_FARM);
         case OPEN_SUPPLY -> this.open(this.chest, State.TAKE_SUPPLY, State.TO_FARM);
         case TAKE_SUPPLY -> this.takeSupplies();
         default -> {
         }
      }
   }

   // --------------------------------------------------------------------- этапы

   /**
    * Переход между анархиями. К вардену: команда анки, потом /home; на склад: только команда анки —
    * персонаж появляется там, где стоял на той анке, то есть у склада.
    */
   private void teleport(String key, State next) {
      boolean farm = key.equals("loot");
      String server = this.getHome(farm ? "warden" : "storage");
      if (!this.commandSent) {
         if (server.isEmpty()) {
            this.fail("не задана " + (farm ? "анка вардена" : "анка склада") + " — впиши её в настройках модуля");
            return;
         }

         this.stopWalking();
         mc.getNetworkHandler().sendChatCommand(server);
         this.commandSent = true;
         this.homeSent = false;
         this.stateSince = System.currentTimeMillis();
         return;
      }

      // даём серверу время перекинуть на другую анку
      if (this.inState() < 7000L) {
         return;
      }

      if (!farm) {
         this.go(next);
         return;
      }

      if (!this.homeSent) {
         String home = this.getHome("home");
         if (home.isEmpty()) {
            this.fail("не задан хом вардена — впиши его в настройках модуля");
            return;
         }

         mc.getNetworkHandler().sendChatCommand("home " + home);
         this.homeSent = true;
         this.stateSince = System.currentTimeMillis() - 7000L;
      } else if (this.inState() > 7000L + 8500L) {
         this.go(next);
      }
   }

   private void findChest() {
      this.upkeep();
      BlockPos best = null;
      long bestOpens = Long.MAX_VALUE;
      double bestDist = Double.MAX_VALUE;
      Box area = mc.player.getBoundingBox().expand(150.0);

      for (Entity entity : mc.world.getEntitiesByClass(Entity.class, area, e -> e instanceof ArmorStandEntity || e instanceof DisplayEntity.TextDisplayEntity)) {
         long seconds = parseTimer(label(entity));
         if (seconds < 0L) {
            continue;
         }

         BlockPos container = this.containerNear(entity.getBlockPos(), 4);
         if (container == null) {
            continue;
         }

         long opens = System.currentTimeMillis() + seconds * 1000L;
         double dist = mc.player.squaredDistanceTo(Vec3d.ofCenter(container));
         // сначала тот, что откроется раньше; при равном времени — ближайший
         if (opens < bestOpens - 1500L || Math.abs(opens - bestOpens) <= 1500L && dist < bestDist) {
            best = container;
            bestOpens = opens;
            bestDist = dist;
         }
      }

      if (best == null && !this.timedOnly.isEnabled()) {
         best = this.containerNear(mc.player.getBlockPos(), 24);
         bestOpens = System.currentTimeMillis();
      }

      if (best != null) {
         this.chest = best;
         this.opensAt = bestOpens;
         this.go(State.WALK_TO_CHEST);
      } else if (this.inState() > 20000L) {
         this.fail("сундук с таймером не найден рядом с хомом вардена");
      }
   }

   private void walk(BlockPos target, State arrived, State lost) {
      this.upkeep();
      if (target == null) {
         this.go(lost);
         return;
      }

      if (mc.player.squaredDistanceTo(Vec3d.ofCenter(target)) <= 9.0) {
         this.stopWalking();
         this.go(arrived);
         return;
      }

      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      if (!baritone.getPathingBehavior().isPathing() && System.currentTimeMillis() - this.lastAction > 1500L) {
         this.lastAction = System.currentTimeMillis();
         baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(target, 2));
      }

      if (this.inState() > 60000L) {
         this.stopWalking();
         this.go(lost);
      }
   }

   private void waitTimer() {
      this.upkeep();
      // таймер перечитываем каждый тик: сервер может его сдвинуть
      long seconds = this.timerAt(this.chest);
      if (seconds >= 0L) {
         this.opensAt = System.currentTimeMillis() + seconds * 1000L;
      }

      if (mc.player.squaredDistanceTo(Vec3d.ofCenter(this.chest)) > 9.0) {
         this.go(State.WALK_TO_CHEST);
         return;
      }

      if (System.currentTimeMillis() >= this.opensAt) {
         this.go(State.OPEN_CHEST);
      }
   }

   private void open(BlockPos target, State opened, State failed) {
      if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
         this.go(opened);
         return;
      }

      if (target == null || this.inState() > 20000L) {
         this.go(failed);
         return;
      }

      long now = System.currentTimeMillis();
      if ((float)(now - this.lastAction) >= this.openDelay.getCurrent()) {
         this.lastAction = now;
         BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target), Direction.UP, target, false);
         mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
         mc.player.swingHand(Hand.MAIN_HAND);
      }
   }

   private void loot() {
      if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
         this.afterLoot();
         return;
      }

      int size = handler.getRows() * 9;

      for (int i = 0; i < size; ++i) {
         ItemStack stack = handler.getSlot(i).getStack();
         if (!stack.isEmpty() && this.wanted(stack)) {
            // по одному предмету за тик: сервер не успевает за пачкой кликов
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            this.emptyChecks = 0;
            return;
         }
      }

      // лут в сундуке появляется не сразу — убеждаемся несколько тиков подряд, что брать нечего
      if (++this.emptyChecks >= 6 || mc.player.getInventory().getEmptySlot() == -1) {
         mc.player.closeHandledScreen();
         this.afterLoot();
      }
   }

   private void afterLoot() {
      this.retreatFrom = mc.player.getPos();
      this.go(this.retreat.getCurrent() > 0.5F ? State.RETREAT : this.nextAfterFarm());
   }

   private State nextAfterFarm() {
      if (this.deposit.isEnabled() && this.hasLoot()) {
         return State.TO_STORAGE;
      }

      return State.FIND_CHEST;
   }

   private void retreat() {
      float blocks = this.retreat.getCurrent();
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      if (!this.commandSent) {
         this.commandSent = true;
         // уходим от сундука в сторону, противоположную ему
         Vec3d away = this.chest == null ? mc.player.getRotationVec(1.0F).multiply(-1.0) : mc.player.getPos().subtract(Vec3d.ofCenter(this.chest));
         away = new Vec3d(away.x, 0.0, away.z);
         away = away.lengthSquared() < 0.01 ? new Vec3d(1.0, 0.0, 0.0) : away.normalize();
         BlockPos goal = BlockPos.ofFloored(mc.player.getPos().add(away.multiply((double)blocks)));
         baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(goal, 3));
      }

      boolean far = this.retreatFrom != null && mc.player.getPos().distanceTo(this.retreatFrom) >= (double)blocks * 0.8;
      if (far || this.inState() > 6000L) {
         this.stopWalking();
         this.go(this.nextAfterFarm());
      }
   }

   private void findContainer(State next, boolean supply) {
      BlockPos found = supply ? this.supplyChest() : this.containerNear(mc.player.getBlockPos(), 24);
      if (found != null) {
         this.chest = found;
         this.go(next);
      } else if (this.inState() > 5000L) {
         if (supply) {
            this.go(State.TO_FARM);
         } else {
            this.fail("на анке склада рядом нет сундука");
         }
      }
   }

   private void depositLoot() {
      if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
         this.go(this.needSupplies() ? State.FIND_SUPPLY : State.TO_FARM);
         return;
      }

      int containerSize = handler.getRows() * 9;

      for (int i = containerSize; i < handler.slots.size(); ++i) {
         ItemStack stack = handler.getSlot(i).getStack();
         if (!stack.isEmpty() && this.isLoot(stack)) {
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            // предмет не уходит — сундук полон
            if (++this.emptyChecks > 80) {
               mc.player.closeHandledScreen();
               this.fail("склад переполнен");
            }

            return;
         }
      }

      // лут сдан; если инвизка лежит в этом же сундуке — берём её отсюда
      if (this.supplies.isEnabled() && this.count(AutoWarden::isInvisPotion) < 1) {
         for (int i = 0; i < containerSize; ++i) {
            if (isInvisPotion(handler.getSlot(i).getStack())) {
               mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
               return;
            }
         }
      }

      mc.player.closeHandledScreen();
      this.go(this.needSupplies() ? State.FIND_SUPPLY : State.TO_FARM);
   }

   private void takeSupplies() {
      if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
         this.go(State.TO_FARM);
         return;
      }

      int containerSize = handler.getRows() * 9;
      boolean needFood = false;
      boolean needInvis = this.count(AutoWarden::isInvisPotion) < 1;

      for (int i = 0; i < containerSize && (needFood || needInvis); ++i) {
         ItemStack stack = handler.getSlot(i).getStack();
         if (needInvis && isInvisPotion(stack) || needFood && isFood(stack)) {
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            if (++this.emptyChecks < 12) {
               return;
            }

            break;
         }
      }

      mc.player.closeHandledScreen();
      this.go(State.TO_FARM);
   }

   // ------------------------------------------------------------------ помощники

   private void fail(String reason) {
      Pain.getInstance().getNotifyManager().addNotification("", Text.of("AutoWarden: " + reason));
      if (this.isEnabled()) {
         this.toggle();
      }
   }

   private void stopWalking() {
      try {
         BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
      } catch (Throwable ignored) {
      }
   }

   private static String label(Entity entity) {
      Text text = null;
      if (entity instanceof ArmorStandEntity stand) {
         text = stand.getCustomName();
      } else if (entity instanceof DisplayEntity.TextDisplayEntity display) {
         text = display.getText();
      }

      return text == null ? "" : text.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT).trim();
   }

   /** Сколько секунд осталось по надписи голограммы, или -1, если это не таймер. */
   static long parseTimer(String text) {
      if (text == null || text.isBlank()) {
         return -1L;
      }

      Matcher clock = CLOCK.matcher(text);
      if (clock.find()) {
         long a = Long.parseLong(clock.group(1));
         long b = Long.parseLong(clock.group(2));
         return clock.group(3) == null ? a * 60L + b : a * 3600L + b * 60L + Long.parseLong(clock.group(3));
      }

      long total = -1L;
      Matcher min = MINUTES.matcher(text);
      if (min.find()) {
         total = Long.parseLong(min.group(1)) * 60L;
      }

      Matcher sec = SECONDS.matcher(text);
      if (sec.find()) {
         total = Math.max(0L, total) + Long.parseLong(sec.group(1));
      }

      return total;
   }

   private long timerAt(BlockPos pos) {
      if (pos == null) {
         return -1L;
      }

      long best = -1L;

      for (Entity entity : mc.world.getEntitiesByClass(Entity.class, new Box(pos).expand(4.0), e -> e instanceof ArmorStandEntity || e instanceof DisplayEntity.TextDisplayEntity)) {
         long seconds = parseTimer(label(entity));
         if (seconds >= 0L && (best < 0L || seconds < best)) {
            best = seconds;
         }
      }

      return best;
   }

   private static boolean isContainer(Block block) {
      return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL || block == Blocks.ENDER_CHEST;
   }

   private BlockPos containerNear(BlockPos center, int radius) {
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;

      for (BlockPos pos : BlockPos.iterate(center.add(-radius, -3, -radius), center.add(radius, 3, radius))) {
         Block block = mc.world.getBlockState(pos).getBlock();
         if (isContainer(block) && block != Blocks.ENDER_CHEST) {
            double dist = pos.getSquaredDistance(center);
            if (dist < bestDist) {
               bestDist = dist;
               best = pos.toImmutable();
            }
         }
      }

      return best;
   }

   /** Сундук с припасами — тот, рядом с которым стоит табличка со словами «еда» или «инвиз». */
   private BlockPos supplyChest() {
      BlockPos center = mc.player.getBlockPos();

      for (BlockPos pos : BlockPos.iterate(center.add(-24, -3, -24), center.add(24, 3, 24))) {
         BlockEntity entity = mc.world.getBlockEntity(pos);
         if (entity instanceof SignBlockEntity sign) {
            StringBuilder sb = new StringBuilder();

            for (Text line : sign.getFrontText().getMessages(false)) {
               sb.append(line.getString().toLowerCase(Locale.ROOT)).append(' ');
            }

            String text = sb.toString();
            if (text.contains("инвиз") || text.contains("невид") || text.contains("invis")) {
               BlockPos container = this.containerNear(pos, 2);
               if (container != null) {
                  return container;
               }
            }
         }
      }

      return null;
   }

   private boolean wanted(ItemStack stack) {
      return this.takeAll.isEnabled() || this.isLoot(stack);
   }

   private boolean isLoot(ItemStack stack) {
      if (isFood(stack) && !stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) && !stack.isOf(Items.GOLDEN_APPLE) || isInvisPotion(stack)) {
         return false;
      }

      return VALUABLE.contains(stack.getItem()) || stack.getItem().toString().contains("shulker_box");
   }

   private boolean hasLoot() {
      for (int i = 0; i < 36; ++i) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (!stack.isEmpty() && this.isLoot(stack)) {
            return true;
         }
      }

      return false;
   }

   private static boolean isFood(ItemStack stack) {
      return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
   }

   private static boolean isInvisPotion(ItemStack stack) {
      if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
         return false;
      }

      var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
      if (contents == null) {
         return false;
      }

      for (var effect : contents.getEffects()) {
         if (effect.getEffectType() == StatusEffects.INVISIBILITY) {
            return true;
         }
      }

      return false;
   }

   private int count(java.util.function.Predicate<ItemStack> filter) {
      int total = 0;

      for (int i = 0; i < 36; ++i) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (filter.test(stack)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private boolean needSupplies() {
      return this.supplies.isEnabled() && this.count(AutoWarden::isInvisPotion) < 1;
   }

   /** Еда при голоде и инвизка, когда эффект кончился: берём предмет из хотбара и зажимаем использование. */
   private void upkeep() {
      if (!this.autoEat.isEnabled() || mc.currentScreen != null) {
         return;
      }

      boolean hungry = mc.player.getHungerManager().getFoodLevel() <= 14;
      boolean visible = !mc.player.hasStatusEffect(StatusEffects.INVISIBILITY);

      for (int slot = 0; slot < 9; ++slot) {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         boolean eat = hungry && isFood(stack) && !stack.isOf(Items.ENCHANTED_GOLDEN_APPLE);
         boolean drink = visible && isInvisPotion(stack);
         if (eat || drink) {
            this.savedSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slot;
            mc.options.useKey.setPressed(true);
            this.useTicks = 38;
            return;
         }
      }
   }

   private void releaseUse() {
      mc.options.useKey.setPressed(false);
      if (this.savedSlot >= 0 && mc.player != null) {
         mc.player.getInventory().selectedSlot = this.savedSlot;
      }

      this.savedSlot = -1;
      this.useTicks = 0;
   }

   @EventTarget
   public void onRender3D(EventRender3D event) {
      if (this.chest != null && this.state != State.IDLE) {
         int color = Pain.getInstance().getThemeManager().getCurrentTheme().getColor().withAlpha(200).getRGB();
         Render3DUtil.drawBox(new Box(this.chest), color, 2.0F);
      }
   }

   /** Строка состояния для команды ^warden. */
   public String status() {
      long left = this.opensAt < 0L ? -1L : Math.max(0L, (this.opensAt - System.currentTimeMillis()) / 1000L);
      return describe(this.state) + (this.state == State.WAIT_TIMER && left >= 0L ? ", до открытия " + left + " с" : "");
   }
}
