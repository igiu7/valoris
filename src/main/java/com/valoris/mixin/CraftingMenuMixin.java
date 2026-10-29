package com.valoris.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.tech.CraftLock;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * Runs after the crafting table / 2x2 inventory grid computes its result (server side) and empties the
 * result slot when the player's city has not researched the technology the item needs.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void valoris$lockResult(AbstractContainerMenu menu, Level level, Player player,
                                           CraftingContainer craftSlots, ResultContainer resultSlots,
                                           @Nullable RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            CraftLock.filterResult(menu, serverPlayer, resultSlots);
        }
    }
}
