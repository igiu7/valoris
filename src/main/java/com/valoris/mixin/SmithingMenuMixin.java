package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.tech.CraftLock;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;

/**
 * The smithing table follows the technology tree too: it shows no result for an item the player's realm does not
 * know yet (netherite gear needs Steel Tools / Steel Weapons / Steel Armor, like everything made of steel).
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
    private SmithingMenuMixin(MenuType<?> type, int id, Inventory inv, ContainerLevelAccess access) {
        super(type, id, inv, access);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void valoris$lockResult(CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            CraftLock.filterResult(this, serverPlayer, this.resultSlots, this.getResultSlot());
        }
    }
}
