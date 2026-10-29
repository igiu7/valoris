package com.valoris.furnace;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/** A cooking recipe used only by the bloomery or the steel furnace. */
public class ValorisCookingRecipe extends AbstractCookingRecipe {
    private final boolean steel;

    public ValorisCookingRecipe(boolean steel, String group, CookingBookCategory category, Ingredient ingredient,
                                ItemStack result, float experience, int cookingTime) {
        super(steel ? FurnaceRegistry.STEELMAKING.get() : FurnaceRegistry.BLOOMERY.get(),
                group, category, ingredient, result, experience, cookingTime);
        this.steel = steel;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return steel ? FurnaceRegistry.STEELMAKING_SERIALIZER.get() : FurnaceRegistry.BLOOMERY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return steel ? FurnaceRegistry.STEELMAKING.get() : FurnaceRegistry.BLOOMERY.get();
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(steel ? FurnaceRegistry.STEEL_FURNACE_ITEM.get() : FurnaceRegistry.BLOOMERY_ITEM.get());
    }
}
