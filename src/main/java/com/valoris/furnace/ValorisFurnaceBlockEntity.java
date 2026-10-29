package com.valoris.furnace;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Bloomery / steel furnace: a vanilla-style furnace that only runs its own recipe type. */
public class ValorisFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    private final boolean steel;

    public ValorisFurnaceBlockEntity(BlockPos pos, BlockState state, boolean steel) {
        super(steel ? FurnaceRegistry.STEEL_FURNACE_BE.get() : FurnaceRegistry.BLOOMERY_BE.get(), pos, state,
                steel ? FurnaceRegistry.STEELMAKING.get() : FurnaceRegistry.BLOOMERY.get());
        this.steel = steel;
    }

    public static ValorisFurnaceBlockEntity bloomery(BlockPos pos, BlockState state) {
        return new ValorisFurnaceBlockEntity(pos, state, false);
    }

    public static ValorisFurnaceBlockEntity steel(BlockPos pos, BlockState state) {
        return new ValorisFurnaceBlockEntity(pos, state, true);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(steel ? "container.valoris2.steel_furnace" : "container.valoris2.bloomery");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return steel ? new ValorisFurnaceMenu.Steel(containerId, inventory, this, this.dataAccess)
                : new ValorisFurnaceMenu.Bloomery(containerId, inventory, this, this.dataAccess);
    }
}
