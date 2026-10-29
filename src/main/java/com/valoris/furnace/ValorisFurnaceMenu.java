package com.valoris.furnace;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;

/** Furnace menus for the two furnaces (vanilla furnace layout). */
public final class ValorisFurnaceMenu {
    private ValorisFurnaceMenu() {}

    public static class Bloomery extends AbstractFurnaceMenu {
        public Bloomery(int id, Inventory inv) {
            super(FurnaceRegistry.BLOOMERY_MENU.get(), FurnaceRegistry.BLOOMERY.get(), RecipeBookType.FURNACE, id, inv);
        }

        public Bloomery(int id, Inventory inv, Container container, ContainerData data) {
            super(FurnaceRegistry.BLOOMERY_MENU.get(), FurnaceRegistry.BLOOMERY.get(), RecipeBookType.FURNACE,
                    id, inv, container, data);
        }
    }

    public static class Steel extends AbstractFurnaceMenu {
        public Steel(int id, Inventory inv) {
            super(FurnaceRegistry.STEEL_FURNACE_MENU.get(), FurnaceRegistry.STEELMAKING.get(), RecipeBookType.BLAST_FURNACE, id, inv);
        }

        public Steel(int id, Inventory inv, Container container, ContainerData data) {
            super(FurnaceRegistry.STEEL_FURNACE_MENU.get(), FurnaceRegistry.STEELMAKING.get(), RecipeBookType.BLAST_FURNACE,
                    id, inv, container, data);
        }
    }
}
