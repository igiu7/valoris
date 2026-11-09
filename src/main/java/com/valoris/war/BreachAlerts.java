package com.valoris.war;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.Siege;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * During an active siege the defenders are told in chat which wall of their city is being breached: when a blast
 * (a siege machine's missile, the battering ram...) destroys blocks on the city's land, or a besieger breaks one.
 * The side comes from where the block lies in the city's claim: the edge of the claimed land it is closest to
 * (north, east, south, west, or a corner when two edges are about as close). At most one alert per side every
 * 15 seconds.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class BreachAlerts {
    private BreachAlerts() {}

    private static final long EVERY = 15 * 20;
    /** how far (in chunks) the claim edge is looked for */
    private static final int SCAN = 64;
    /** two edges at most this many blocks apart in distance make a corner (when both are near: up to 24 blocks) */
    private static final int CORNER = 3;
    /** siege id + side -> game time of its last alert */
    private static final Map<String, Long> LAST = new HashMap<>();

    /** Only solid blocks count (no grass, flowers, torches...). Runs last, after every protection. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || !counts(event.getState())) return;
        ValorisData data = ValorisData.get(level.getServer());
        Optional<Siege> siege = SiegeEvents.activeSiegeAt(data, level, event.getPos());
        if (siege.isEmpty() || !SiegeService.isAttacker(player, siege.get())) return;
        alert(data, level, siege.get(), Set.of(side(level, event.getPos(), siege.get().cityTeam)));
    }

    /** After SiegeEvents.onExplosion has taken out what the blast may not destroy. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ValorisData data = ValorisData.get(level.getServer());
        if (data.sieges().isEmpty()) return;
        Map<UUID, Siege> sieges = new HashMap<>();
        Map<UUID, Set<String>> sides = new HashMap<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (!counts(level.getBlockState(pos))) continue;
            Optional<Siege> siege = SiegeEvents.activeSiegeAt(data, level, pos);
            if (siege.isEmpty()) continue;
            sieges.put(siege.get().id, siege.get());
            sides.computeIfAbsent(siege.get().id, k -> new LinkedHashSet<>()).add(side(level, pos, siege.get().cityTeam));
        }
        sides.forEach((id, s) -> alert(data, level, sieges.get(id), s));
    }

    private static boolean counts(BlockState state) {
        return !state.isAir() && state.blocksMotion();
    }

    private static void alert(ValorisData data, ServerLevel level, Siege siege, Set<String> sides) {
        long now = level.getGameTime();
        for (String side : sides) {
            String key = siege.id + side;
            Long last = LAST.get(key);
            if (last != null && now - last < EVERY && now >= last) continue;
            LAST.put(key, now);
            for (UUID d : SiegeService.defendersOf(data, siege)) {
                com.valoris.realm.Realms.message(data, d, Msg.text("The " + side + " wall of " + siege.cityName
                        + " is being breached!", ChatFormatting.RED));
            }
        }
    }

    /**
     * The side of the city's claim the block is on: the claim edge closest to it along the four directions,
     * or the corner of two neighbouring edges about as close.
     */
    static String side(ServerLevel level, BlockPos pos, UUID cityTeam) {
        int x = pos.getX(), z = pos.getZ();
        int cx = x >> 4, cz = z >> 4;
        ChunkDimPos here = new ChunkDimPos(level, pos);
        // distance in blocks from the block to the last claimed block before the edge, per direction
        int north = z - ((cz - claimedRun(here, cityTeam, 0, -1)) << 4);
        int south = ((cz + claimedRun(here, cityTeam, 0, 1)) << 4) + 15 - z;
        int west = x - ((cx - claimedRun(here, cityTeam, -1, 0)) << 4);
        int east = ((cx + claimedRun(here, cityTeam, 1, 0)) << 4) + 15 - x;

        String ns = north <= south ? "north" : "south";
        int nsDist = Math.min(north, south);
        String ew = east <= west ? "east" : "west";
        int ewDist = Math.min(east, west);
        if (Math.abs(nsDist - ewDist) <= CORNER && Math.max(nsDist, ewDist) <= 24) return ns + "-" + ew;
        return nsDist < ewDist ? ns : ew;
    }

    /** How many chunks past this one, in direction (dx, dz), still belong to the city. */
    private static int claimedRun(ChunkDimPos start, UUID cityTeam, int dx, int dz) {
        var manager = FTBChunksAPI.api().getManager();
        int n = 0;
        while (n < SCAN) {
            ClaimedChunk next = manager.getChunk(start.offset(dx * (n + 1), dz * (n + 1)));
            if (next == null || !next.getTeamData().getTeam().getId().equals(cityTeam)) break;
            n++;
        }
        return n;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        LAST.clear();
    }
}
