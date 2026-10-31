package com.valoris.rules;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.valoris.valoris2;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Bottles o' enchanting are gone with the experience (see NoExperienceOrbs): no loot gives them (chests, vaults,
 * fishing, mobs: the global loot modifier valoris2:no_experience_bottles), no villager sells them, and one that
 * is still around cannot be thrown, by hand or from a dispenser.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class NoExperienceBottles {
    private NoExperienceBottles() {}

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, valoris2.MODID);
    public static final Supplier<MapCodec<RemoveBottles>> REMOVE_BOTTLES =
            LOOT_MODIFIERS.register("no_experience_bottles", () -> RemoveBottles.CODEC);

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }

    /** Takes every bottle o' enchanting out of the generated loot. */
    public static final class RemoveBottles extends LootModifier {
        public static final MapCodec<RemoveBottles> CODEC =
                RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, RemoveBottles::new));

        public RemoveBottles(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(stack -> stack.is(Items.EXPERIENCE_BOTTLE));
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        for (List<VillagerTrades.ItemListing> level : event.getTrades().values()) level.replaceAll(NoExperienceBottles::withoutBottles);
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().replaceAll(NoExperienceBottles::withoutBottles);
        event.getRareTrades().replaceAll(NoExperienceBottles::withoutBottles);
    }

    /** A trade that is skipped (no offer) when it would sell bottles o' enchanting. */
    private static VillagerTrades.ItemListing withoutBottles(VillagerTrades.ItemListing listing) {
        return (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            return offer != null && offer.getResult().is(Items.EXPERIENCE_BOTTLE) ? null : offer;
        };
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().is(Items.EXPERIENCE_BOTTLE)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ThrownExperienceBottle) event.setCanceled(true);
    }
}
