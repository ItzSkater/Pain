package tech.pain.client.modules.impl.player;

import com.darkmagician6.eventapi.EventTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import tech.pain.Pain;
import tech.pain.base.events.impl.input.EventKey;
import tech.pain.base.events.impl.player.EventUpdate;
import tech.pain.client.modules.api.Category;
import tech.pain.client.modules.api.Module;
import tech.pain.client.modules.api.ModuleAnnotation;
import tech.pain.client.modules.api.setting.impl.BooleanSetting;
import tech.pain.client.modules.api.setting.impl.KeySetting;
import tech.pain.client.modules.api.setting.impl.ModeSetting;
import tech.pain.client.modules.api.setting.impl.NumberSetting;

/**
 * Сортировка сундука. Работает обычными кликами по слотам, как руками: сначала складывает
 * неполные стаки одного предмета вместе, потом расставляет предметы по порядку от первого слота.
 * Запускается клавишей при открытом сундуке или сама при открытии.
 */
@ModuleAnnotation(
   name = "ChestSorter",
   category = Category.PLAYER,
   description = "Сортирует открытый сундук (или инвентарь): складывает стаки и расставляет предметы по порядку"
)
public final class ChestSorter extends Module {
   public static final ChestSorter INSTANCE = new ChestSorter();
   private final KeySetting sortKey = new KeySetting("Клавиша сортировки");
   private final ModeSetting what = new ModeSetting("Что сортировать", "Сундук", "Инвентарь", "Сундук и инвентарь");
   private final ModeSetting order = new ModeSetting("Порядок", "По названию", "По количеству", "По категории");
   private final BooleanSetting onOpen = new BooleanSetting("Сортировать при открытии", false);
   private final NumberSetting delay = new NumberSetting("Пауза между кликами (мс)", 60.0F, 0.0F, 400.0F, 10.0F);
   private boolean sorting;
   private boolean sortInventoryNext;
   private boolean inventoryPhase;
   private int handlerId = -1;
   private int seenHandler = -1;
   private int steps;
   private long nextClick;
   /** Слот, куда надо вернуть предмет с курсора, чтобы закончить начатый обмен. */
   private final List<Integer> queue = new ArrayList<>();

   private ChestSorter() {
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.stop();
   }

   private void stop() {
      this.sorting = false;
      this.queue.clear();
   }

   private void say(String text) {
      Pain.getInstance().getNotifyManager().addNotification("", Text.of("ChestSorter: " + text));
   }

   @EventTarget
   public void onKey(EventKey event) {
      if (mc.player == null || !event.isKeyDown(this.sortKey.getKeyCode(), mc.currentScreen instanceof HandledScreen)) {
         return;
      }

      if (this.sorting) {
         this.stop();
         this.say("остановлено");
      } else {
         this.begin();
      }
   }

   private void begin() {
      ScreenHandler handler = mc.player.currentScreenHandler;
      boolean chestOpen = handler != mc.player.playerScreenHandler;
      boolean wantChest = !this.what.is("Инвентарь");
      boolean wantInventory = !this.what.is("Сундук");
      if (!chestOpen && !wantInventory) {
         return;
      }

      this.sorting = true;
      this.handlerId = handler.syncId;
      this.inventoryPhase = !chestOpen || !wantChest;
      this.sortInventoryNext = chestOpen && wantChest && wantInventory;
      this.steps = 0;
      this.queue.clear();
      this.nextClick = 0L;
   }

   /** Слоты, которые сортируем сейчас: сундук или основная часть инвентаря (без хотбара и брони). */
   private List<Slot> slots(ScreenHandler handler) {
      List<Slot> result = new ArrayList<>();

      for (Slot slot : handler.slots) {
         boolean own = slot.inventory == mc.player.getInventory();
         if (this.inventoryPhase ? own && slot.getIndex() >= 9 && slot.getIndex() <= 35 : !own) {
            result.add(slot);
         }
      }

      return result;
   }

   private Comparator<ItemStack> comparator() {
      Comparator<ItemStack> byName = Comparator.comparing(s -> s.getName().getString().toLowerCase());
      Comparator<ItemStack> byId = Comparator.comparing(s -> Registries.ITEM.getId(s.getItem()).toString());
      Comparator<ItemStack> main;
      if (this.order.is("По количеству")) {
         main = Comparator.<ItemStack>comparingInt(ItemStack::getCount).reversed().thenComparing(byId);
      } else if (this.order.is("По категории")) {
         // по идентификатору: одинаковые материалы и типы предметов оказываются рядом
         main = byId.thenComparing(byName);
      } else {
         main = byName.thenComparing(byId);
      }

      // пустые слоты — в конец; одинаковые предметы — полные стаки вперёд
      return Comparator.<ItemStack, Boolean>comparing(ItemStack::isEmpty).thenComparing(main).thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
   }

   @EventTarget
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.interactionManager == null) {
         return;
      }

      ScreenHandler handler = mc.player.currentScreenHandler;
      boolean chestOpen = handler != mc.player.playerScreenHandler;
      if (this.onOpen.isEnabled() && chestOpen && handler.syncId != this.seenHandler && !this.sorting) {
         this.seenHandler = handler.syncId;
         this.begin();
      }

      if (!chestOpen) {
         this.seenHandler = -1;
      }

      if (!this.sorting) {
         return;
      }

      // сундук закрыли или открыли другой — бросаем, чтобы не кликать не туда
      if (handler.syncId != this.handlerId) {
         this.stop();
         return;
      }

      long now = System.currentTimeMillis();
      if (now < this.nextClick) {
         return;
      }

      this.nextClick = now + (long)this.delay.getCurrent();
      if (!this.queue.isEmpty()) {
         this.click(handler, this.queue.remove(0));
         return;
      }

      // на курсоре что-то осталось (помешал игрок или сервер) — не продолжаем вслепую
      if (!handler.getCursorStack().isEmpty()) {
         this.stop();
         this.say("на курсоре предмет — положи его и запусти снова");
         return;
      }

      if (++this.steps > 600) {
         this.stop();
         this.say("слишком много шагов, остановлено");
         return;
      }

      List<Slot> slots = this.slots(handler);
      if (this.planMerge(slots) || this.planSwap(slots)) {
         this.click(handler, this.queue.remove(0));
         return;
      }

      if (this.sortInventoryNext) {
         this.sortInventoryNext = false;
         this.inventoryPhase = true;
         return;
      }

      this.stop();
      this.say("готово");
   }

   private void click(ScreenHandler handler, int slotId) {
      mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
   }

   /** Два неполных стака одного предмета: берём дальний, кладём в ближний, остаток возвращаем. */
   private boolean planMerge(List<Slot> slots) {
      for (int i = 0; i < slots.size(); ++i) {
         ItemStack a = slots.get(i).getStack();
         if (a.isEmpty() || a.getCount() >= a.getMaxCount()) {
            continue;
         }

         for (int j = slots.size() - 1; j > i; --j) {
            ItemStack b = slots.get(j).getStack();
            if (!b.isEmpty() && b.getCount() < b.getMaxCount() && ItemStack.areItemsAndComponentsEqual(a, b)) {
               this.queue.add(slots.get(j).id);
               this.queue.add(slots.get(i).id);
               if (a.getCount() + b.getCount() > a.getMaxCount()) {
                  this.queue.add(slots.get(j).id);
               }

               return true;
            }
         }
      }

      return false;
   }

   /** Первый слот, где лежит не то, что должно по порядку: меняем его местами с нужным. */
   private boolean planSwap(List<Slot> slots) {
      List<ItemStack> sorted = new ArrayList<>();

      for (Slot slot : slots) {
         sorted.add(slot.getStack());
      }

      sorted.sort(this.comparator());

      for (int i = 0; i < slots.size(); ++i) {
         ItemStack want = sorted.get(i);
         ItemStack have = slots.get(i).getStack();
         if (ItemStack.areEqual(want, have)) {
            continue;
         }

         if (want.isEmpty()) {
            // дальше по порядку только пустые слоты — всё уже на местах
            return false;
         }

         for (int j = i + 1; j < slots.size(); ++j) {
            if (ItemStack.areEqual(slots.get(j).getStack(), want)) {
               this.queue.add(slots.get(j).id);
               this.queue.add(slots.get(i).id);
               if (!have.isEmpty()) {
                  // в слоте что-то лежало — оно теперь на курсоре, кладём на освободившееся место
                  this.queue.add(slots.get(j).id);
               }

               return true;
            }
         }

         return false;
      }

      return false;
   }
}
