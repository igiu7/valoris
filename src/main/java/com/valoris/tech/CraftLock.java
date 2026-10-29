package com.valoris.tech;

import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;

public final class CraftLock {
    private CraftLock() {}

    /** Crafting grids: the result is menu slot 0. */
    public static void filterResult(AbstractContainerMenu menu, ServerPlayer player, ResultContainer result) {
        filterResult(menu, player, result, 0);
    }

    /** Empties the result (menu slot menuSlot) when the player's realm does not know the item's technology. */
    public static void filterResult(AbstractContainerMenu menu, ServerPlayer player, ResultContainer result, int menuSlot) {
        ItemStack stack = result.getItem(0);
        if (stack.isEmpty()) return;
        Optional<String> reason = TechService.blockedReason(player, stack);
        if (reason.isEmpty()) return;

        result.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(menuSlot, ItemStack.EMPTY);
        player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), menuSlot, ItemStack.EMPTY));
        player.displayClientMessage(Component.literal(reason.get()).withStyle(ChatFormatting.RED), true);
    }
}
