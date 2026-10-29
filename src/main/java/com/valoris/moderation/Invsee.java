package com.valoris.moderation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * /vx invsee and /vx ecsee: a staff member looks into an online player's inventory (main inventory, armor and
 * offhand) or ender chest, live. Moderators only look; admins may also take and put items (by hand - shift-clicking
 * and double-click collecting are off so nothing can land in the wrong place).
 */
public final class Invsee {
    private Invsee() {}

    /** The target's whole inventory as a 6-row chest: rows 1-4 main inventory, row 5 armor + offhand, rest unused. */
    private static final class InventoryView implements Container {
        private static final int MAIN = 36, ARMOR = 36, OFFHAND = 40, USED = 41;
        private final ServerPlayer target;
        private final ItemStack filler;

        InventoryView(ServerPlayer target) {
            this.target = target;
            this.filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
            filler.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        }

        private Inventory inv() { return target.getInventory(); }

        @Override public int getContainerSize() { return 54; }

        @Override public boolean isEmpty() { return inv().isEmpty(); }

        @Override
        public ItemStack getItem(int slot) {
            if (slot < MAIN) return inv().items.get(slot);
            if (slot < OFFHAND) return inv().armor.get(3 - (slot - ARMOR)); // helmet first
            if (slot == OFFHAND) return inv().offhand.get(0);
            return filler;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot >= USED) return ItemStack.EMPTY;
            ItemStack stack = getItem(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack taken = stack.split(amount);
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot >= USED) return ItemStack.EMPTY;
            ItemStack stack = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < MAIN) inv().items.set(slot, stack);
            else if (slot < OFFHAND) inv().armor.set(3 - (slot - ARMOR), stack);
            else if (slot == OFFHAND) inv().offhand.set(0, stack);
            setChanged();
        }

        @Override public void setChanged() { inv().setChanged(); }

        @Override public boolean stillValid(Player player) { return target.isAlive() && !target.hasDisconnected(); }

        @Override public void clearContent() { }
    }

    /** A chest menu that may be read-only, never shift-moves, and keeps the unused slots untouchable. */
    private static final class StaffMenu extends ChestMenu {
        private final boolean readOnly;
        private final int usedSlots;

        StaffMenu(MenuType<?> type, int id, Inventory staffInv, Container view, int rows, boolean readOnly, int usedSlots) {
            super(type, id, staffInv, view, rows);
            this.readOnly = readOnly;
            this.usedSlots = usedSlots;
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, Player player) {
            int viewSize = getRowCount() * 9;
            if (type == ClickType.QUICK_MOVE || type == ClickType.PICKUP_ALL) return;
            if (slotId >= usedSlots && slotId < viewSize) return;            // unused slots
            if (readOnly && slotId >= 0 && slotId < viewSize) return;        // looking only
            super.clicked(slotId, button, type, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    public static void inventory(ServerPlayer staff, ServerPlayer target, boolean edit) {
        InventoryView view = new InventoryView(target);
        staff.openMenu(new SimpleMenuProvider((id, inv, p) -> new StaffMenu(MenuType.GENERIC_9x6, id, inv, view, 6,
                !edit, InventoryView.USED), Component.literal(target.getGameProfile().getName()
                + (edit ? "'s inventory" : "'s inventory (view only)"))));
        log(staff, target, "inventory", edit);
    }

    public static void enderChest(ServerPlayer staff, ServerPlayer target, boolean edit) {
        Container chest = target.getEnderChestInventory();
        staff.openMenu(new SimpleMenuProvider((id, inv, p) -> new StaffMenu(MenuType.GENERIC_9x3, id, inv, chest, 3,
                !edit, 27), Component.literal(target.getGameProfile().getName()
                + (edit ? "'s ender chest" : "'s ender chest (view only)"))));
        log(staff, target, "ender chest", edit);
    }

    private static void log(ServerPlayer staff, ServerPlayer target, String what, boolean edit) {
        ModerationService.toStaff(staff.getServer(), Component.literal(staff.getGameProfile().getName()
                + (edit ? " opened " : " looked into ") + "the " + what + " of " + target.getGameProfile().getName() + ".")
                .withStyle(ChatFormatting.GRAY));
    }
}
