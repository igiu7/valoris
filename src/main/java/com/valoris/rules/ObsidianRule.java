package com.valoris.rules;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Obsidian cannot be collected or built with: broken obsidian drops nothing, obsidian items cannot be picked up,
 * and nobody can place it (players in creative mode can).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class ObsidianRule {
    private ObsidianRule() {}

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!Config.BLOCK_OBSIDIAN.get()) return;
        event.getDrops().removeIf(e -> e.getItem().is(Items.OBSIDIAN));
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!Config.BLOCK_OBSIDIAN.get() || event.getPlayer().isCreative()) return;
        if (event.getItemEntity().getItem().is(Items.OBSIDIAN)) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!Config.BLOCK_OBSIDIAN.get() || !event.getPlacedBlock().is(Blocks.OBSIDIAN)) return;
        if (event.getEntity() instanceof Player p) {
            if (p.isCreative()) return;
            p.displayClientMessage(Component.literal("Obsidian cannot be used for building.").withStyle(ChatFormatting.RED), true);
        }
        event.setCanceled(true);
    }
}
