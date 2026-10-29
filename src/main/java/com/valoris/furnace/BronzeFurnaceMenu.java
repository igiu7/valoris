package com.valoris.furnace;

import com.valoris.skill.Skill;
import com.valoris.skill.SkillService;
import com.valoris.skill.XpRules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Bronze furnace menu: copper slot, tin slot, fuel and the bronze result (vanilla furnace layout + one input). */
public class BronzeFurnaceMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final Player player;

    /** Client side. */
    public BronzeFurnaceMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(4), new SimpleContainerData(4));
    }

    public BronzeFurnaceMenu(int id, Inventory inv, Container container, ContainerData data) {
        super(FurnaceRegistry.BRONZE_FURNACE_MENU.get(), id);
        checkContainerSize(container, 4);
        checkContainerDataCount(data, 4);
        this.container = container;
        this.data = data;
        this.player = inv.player;

        addSlot(new InputSlot(container, BronzeFurnaceBlockEntity.FIRST, 34, 17));
        addSlot(new InputSlot(container, BronzeFurnaceBlockEntity.SECOND, 56, 17));
        addSlot(new Slot(container, BronzeFurnaceBlockEntity.FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BronzeFurnaceBlockEntity.isFuel(stack);
            }
        });
        addSlot(new ResultSlot(container, BronzeFurnaceBlockEntity.RESULT, 116, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    private final class InputSlot extends Slot {
        InputSlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BronzeFurnaceBlockEntity.accepts(player.level(), getContainerSlot(), stack);
        }
    }

    /** Taking the bronze out gives the vanilla experience and Blacksmith XP. */
    private final class ResultSlot extends Slot {
        ResultSlot(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            if (taker instanceof ServerPlayer sp) {
                if (container instanceof BronzeFurnaceBlockEntity be) be.awardExperience(sp);
                double xp = XpRules.smelted(stack) * stack.getCount();
                if (xp > 0) SkillService.addXp(sp, Skill.BLACKSMITH, xp);
            }
            super.onTake(taker, stack);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int invStart = 4, invEnd = 40;
        if (index == BronzeFurnaceBlockEntity.RESULT) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            int moved = copy.getCount() - stack.getCount();
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            if (moved <= 0) return ItemStack.EMPTY;
            slot.onTake(p, copy.copyWithCount(moved)); // experience for what was actually taken
            return copy;
        } else if (index >= invStart) {
            boolean moved = false;
            if (BronzeFurnaceBlockEntity.accepts(p.level(), BronzeFurnaceBlockEntity.FIRST, stack)) {
                moved = moveItemStackTo(stack, 0, 1, false);
            }
            if (!moved && BronzeFurnaceBlockEntity.accepts(p.level(), BronzeFurnaceBlockEntity.SECOND, stack)) {
                moved = moveItemStackTo(stack, 1, 2, false);
            }
            if (!moved && BronzeFurnaceBlockEntity.isFuel(stack)) moved = moveItemStackTo(stack, 2, 3, false);
            if (!moved) {
                if (index < 31) { if (!moveItemStackTo(stack, 31, invEnd, false)) return ItemStack.EMPTY; }
                else if (!moveItemStackTo(stack, invStart, 31, false)) return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, invStart, invEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return container.stillValid(p);
    }

    public boolean isLit() {
        return data.get(0) > 0;
    }

    /** 0..1 of the fuel left. */
    public float litProgress() {
        int duration = data.get(1);
        return duration == 0 ? 0 : Math.min(1F, data.get(0) / (float) duration);
    }

    /** 0..1 of the melt. */
    public float burnProgress() {
        int total = data.get(3);
        return total == 0 ? 0 : Math.min(1F, data.get(2) / (float) total);
    }
}
