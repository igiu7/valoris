package com.valoris.compat;

import java.util.Set;

import com.valoris.valoris2;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Cuffed is kept for its handcuffs and their key only. Every other item and block of it (shackles, tape, keys, locks,
 * prison blocks, guillotine...) is taken out of inventories and dropped stacks and hidden from the creative tabs; its
 * recipes are switched off and the handcuffs are made of bronze and string (data/cuffed/recipe), JEI hides the rest
 * (c:hidden_from_recipe_viewers). Who can be cuffed: compatmixin.CuffedRestrainMixin.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class CuffedRules {
    public static final String MOD = "cuffed";
    private static final Set<String> KEPT = Set.of("handcuffs", "handcuffs_key");

    private CuffedRules() {}

    public static boolean removed(Item item) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals(MOD) && !KEPT.contains(id.getPath());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && removed(item.getItem().getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && removed(stack.getItem())) inv.setItem(i, ItemStack.EMPTY);
        }
        if (removed(player.containerMenu.getCarried().getItem())) player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    @EventBusSubscriber(modid = valoris2.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onTabs(BuildCreativeModeTabContentsEvent event) {
            java.util.List<ItemStack> gone = new java.util.ArrayList<>();
            for (ItemStack stack : event.getParentEntries()) if (removed(stack.getItem())) gone.add(stack);
            for (ItemStack stack : event.getSearchEntries()) if (removed(stack.getItem())) gone.add(stack);
            for (ItemStack stack : gone) event.remove(stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
