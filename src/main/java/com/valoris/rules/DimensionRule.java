package com.valoris.rules;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Nether and the End are closed (config rules.disableNether / rules.disableEnd): no entity travels there by any
 * means - portals, /tp, other mods - nether portals do not light, end portal frames take no eyes, and a player found
 * in a closed dimension (e.g. logged out there before) is sent to their overworld spawn.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class DimensionRule {
    private DimensionRule() {}

    public static boolean closed(ResourceKey<Level> dim) {
        return (dim == Level.NETHER && Config.DISABLE_NETHER.get()) || (dim == Level.END && Config.DISABLE_END.get());
    }

    private static String name(ResourceKey<Level> dim) {
        return dim == Level.NETHER ? "The Nether" : "The End";
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!closed(event.getDimension())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof Player p) {
            p.displayClientMessage(Component.literal(name(event.getDimension()) + " is closed on this server.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /** Nether portals do not light. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPortal(BlockEvent.PortalSpawnEvent event) {
        if (Config.DISABLE_NETHER.get()) event.setCanceled(true);
    }

    /** No eyes of ender in end portal frames (the portal could never open anyway). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseFrame(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.DISABLE_END.get() || !event.getItemStack().is(Items.ENDER_EYE)) return;
        if (event.getLevel().getBlockState(event.getPos()).is(Blocks.END_PORTAL_FRAME)) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.literal("The End is closed on this server.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    // ------------------------------------------------------------------ players already there

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendBack(p);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 40 == 0) sendBack(p);
    }

    private static void sendBack(ServerPlayer p) {
        ResourceKey<Level> dim = p.level().dimension();
        if (!closed(dim) || p.isDeadOrDying()) return;
        ServerLevel overworld = p.getServer().overworld();
        BlockPos spot = null;
        if (p.getRespawnDimension() == Level.OVERWORLD && p.getRespawnPosition() != null) {
            BlockPos bed = p.getRespawnPosition();
            spot = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bed);
        }
        if (spot == null) {
            BlockPos spawn = overworld.getSharedSpawnPos();
            spot = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        }
        // teleportTo between dimensions goes through the travel event: this move is to the overworld, so it passes
        p.teleportTo(overworld, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.sendSystemMessage(Component.literal(name(dim) + " is closed on this server. You were brought back to the overworld.")
                .withStyle(ChatFormatting.GOLD));
    }
}
