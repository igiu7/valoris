package com.valoris.compat;

import com.valoris.registry.EraDurability;
import com.valoris.valoris2;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Samurai Dynasty is kept for its armor only (made at the Armory Table, armory.json). Everything else is gone:
 * its mobs and projectiles never join a level, its other items (weapons, masks, materials, blocks, spawn eggs) are
 * taken out of inventories and dropped stacks and hidden from the creative tabs. Recipes: armory.json ->
 * removeAllRecipesFrom. Spawns and ores: biome modifiers remove_samurai_dynasty_*. Structures and chest loot: the
 * overrides in data/samurai_dynasty. JEI: the tag c:hidden_from_recipe_viewers.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class SamuraiDynastyRules {
    public static final String MOD = "samurai_dynasty";

    private SamuraiDynastyRules() {}

    /** True for every item of Samurai Dynasty except its armor (and the straw hat / kimono, which are armor items). */
    public static boolean removed(Item item) {
        return !(item instanceof ArmorItem) && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(MOD);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item) {
            if (removed(item.getItem().getItem())) event.setCanceled(true);
        } else if (BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getNamespace().equals(MOD)) {
            event.setCanceled(true);
        }
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

        /** Era of an armor set, as in armory.json (the clothes have none). */
        private static String era(String path) {
            if (path.startsWith("light_samurai_")) return "bronze";
            if (path.startsWith("steel_samurai_") || path.startsWith("steel_ninja_") || path.equals("ninja_leggings")) return "iron";
            if (path.startsWith("netherite_samurai_") || path.startsWith("master_samurai_") || path.startsWith("steel_")
                    || path.startsWith("netherite_ninja_")) return "steel";
            return null;
        }

        /** The durability of the other armor of the same era (registry.EraDurability). */
        @SubscribeEvent
        public static void onComponents(ModifyDefaultComponentsEvent event) {
            event.getAllItems().forEach(item -> {
                if (!(item instanceof ArmorItem armor)) return;
                var id = BuiltInRegistries.ITEM.getKey(item);
                String era = id.getNamespace().equals(MOD) ? era(id.getPath()) : null;
                if (era == null) return;
                int durability = EraDurability.of(era, armor.getType());
                event.modify(item, builder -> builder.set(DataComponents.MAX_DAMAGE, durability));
            });
        }

        @SubscribeEvent
        public static void onTabs(BuildCreativeModeTabContentsEvent event) {
            java.util.List<ItemStack> gone = new java.util.ArrayList<>();
            for (ItemStack stack : event.getParentEntries()) if (removed(stack.getItem())) gone.add(stack);
            for (ItemStack stack : event.getSearchEntries()) if (removed(stack.getItem())) gone.add(stack);
            for (ItemStack stack : gone) event.remove(stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
