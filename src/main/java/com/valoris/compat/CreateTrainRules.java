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
 * No trains and no Create rails: tracks, stations, signals, track observers, train controls, schedules and the
 * controller rail cannot be made (data/create/recipe), are taken out of inventories and dropped stacks, hidden from
 * the creative tabs and from JEI (c:hidden_from_recipe_viewers). Without tracks and stations no train can be built.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class CreateTrainRules {
    private static final Set<String> REMOVED = Set.of("track", "incomplete_track", "track_station", "track_signal",
            "track_observer", "controls", "schedule", "controller_rail");

    private CreateTrainRules() {}

    public static boolean removed(Item item) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("create") && REMOVED.contains(id.getPath());
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
