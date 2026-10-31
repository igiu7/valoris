package com.valoris.compat;

import java.util.Set;

import com.valoris.valoris2;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * DrinkBeer Refill without Haar's Icey Pale Lager (Drunk Frost Walker): no recipe (data/drinkbeer override), the mug
 * and its recipe board are taken out of inventories and dropped stacks (gifts, broken mugs) and hidden from the
 * creative tabs and JEI (c:hidden_from_recipe_viewers), and the effect never applies. The blaze stouts are brewed
 * with a magma block instead of blaze powder (the Nether is closed), the Night Howl Kvass with raw wolf meat instead
 * of a bone (no hostile mobs). Only the beer is left: every other recipe of the mod (bartending table, lights, bells,
 * stars, trade box, recipe boards) is switched off by the same overrides, and no gifts are given (compatmixin.
 * DrinkBeerGiftMixin).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class DrinkBeerRules {
    private DrinkBeerRules() {}

    private static final Set<ResourceLocation> REMOVED = Set.of(
            ResourceLocation.fromNamespaceAndPath("drinkbeer", "beer_mug_haars_icey_pale_lager"),
            ResourceLocation.fromNamespaceAndPath("drinkbeer", "recipe_board_beer_mug_haars_icey_pale_lager"));
    private static final ResourceLocation FROST_WALKER = ResourceLocation.fromNamespaceAndPath("drinkbeer", "drunk_frost_walker");

    public static boolean removed(Item item) {
        return REMOVED.contains(BuiltInRegistries.ITEM.getKey(item));
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().is(FROST_WALKER)) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && removed(item.getItem().getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        // one drunk before this rule
        for (var effect : java.util.List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().is(FROST_WALKER)) player.removeEffect(effect.getEffect());
        }
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
