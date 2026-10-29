package com.valoris.city;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.tech.TechService;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbchunks.net.SendGeneralDataPacket;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Valoris rules on top of FTB Chunks:
 * - a city claims at most {@link TechService#claimLimit} chunks (that is also the limit FTB shows in the claim manager),
 * - only the city ruler may claim or unclaim, players outside a city cannot claim at all,
 * - a new chunk must touch the city's land (side by side); the chunk holding the city banner cannot be unclaimed,
 *   nor a chunk whose removal would cut the land in two,
 * - force-loading is disabled everywhere (0/0), so no chunks stay loaded and lag the server,
 * - waypoint sharing is off (waypoints are disabled on the client).
 */
@EventBusSubscriber(modid = com.valoris.valoris2.MODID)
public final class ClaimLimitHook {
    public static final String LIMIT_KEY = "valoris2.claim.city_limit";
    public static final String RULER_KEY = "valoris2.claim.ruler_only";
    public static final String FORCE_LOAD_KEY = "valoris2.claim.no_force_load";
    public static final String ADJACENT_KEY = "valoris2.claim.not_adjacent";
    public static final String BANNER_KEY = "valoris2.claim.banner_chunk";
    public static final String SPLIT_KEY = "valoris2.claim.splits_land";
    public static final String SIEGE_KEY = "valoris2.claim.siege";
    public static final String CONQUEST_KEY = "valoris2.claim.conquest";

    private ClaimLimitHook() {}

    public static void init() {
        ClaimedChunkEvent.BEFORE_CLAIM.register((source, chunk) -> {
            var teamData = chunk.getTeamData();
            var server = teamData.getTeamManager().getServer();
            var city = ValorisData.get(server).byTeam(teamData.getTeam().getId());
            if (!mayChangeClaims(source, city.orElse(null))) {
                return CompoundEventResult.interruptFalse(ClaimResult.customProblem(RULER_KEY));
            }
            String frozen = frozenKey(source, server, city.orElse(null));
            if (frozen != null) return CompoundEventResult.interruptFalse(ClaimResult.customProblem(frozen));
            if (city.isPresent() && teamData.getClaimedChunks().size() >= TechService.claimLimit(city.get())) {
                return CompoundEventResult.interruptFalse(ClaimResult.customProblem(LIMIT_KEY));
            }
            if (isPlayer(source) && !touchesOwnLand(teamData, chunk.getPos())) {
                return CompoundEventResult.interruptFalse(ClaimResult.customProblem(ADJACENT_KEY));
            }
            return CompoundEventResult.pass();
        });
        ClaimedChunkEvent.BEFORE_UNCLAIM.register((source, chunk) -> {
            var teamData = chunk.getTeamData();
            var server = teamData.getTeamManager().getServer();
            var city = ValorisData.get(server).byTeam(teamData.getTeam().getId());
            if (!mayChangeClaims(source, city.orElse(null))) {
                return CompoundEventResult.interruptFalse(ClaimResult.customProblem(RULER_KEY));
            }
            String frozen = frozenKey(source, server, city.orElse(null));
            if (frozen != null) return CompoundEventResult.interruptFalse(ClaimResult.customProblem(frozen));
            if (isPlayer(source) && city.isPresent() && holdsBanner(teamData.getTeamManager().getServer(), city.get(), chunk.getPos())) {
                return CompoundEventResult.interruptFalse(ClaimResult.customProblem(BANNER_KEY));
            }
            if (isPlayer(source) && splitsLand(teamData, chunk.getPos())) {
                return CompoundEventResult.interruptFalse(ClaimResult.customProblem(SPLIT_KEY));
            }
            return CompoundEventResult.pass();
        });
        ClaimedChunkEvent.BEFORE_LOAD.register((source, chunk) ->
                CompoundEventResult.interruptFalse(ClaimResult.customProblem(FORCE_LOAD_KEY)));
    }

    /** Server code and the console may always change claims; a player only as the ruler of the owning city. */
    private static boolean mayChangeClaims(CommandSourceStack source, City city) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        return city != null && city.ruler.equals(player.getUUID());
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer;
    }

    /**
     * The land of a city is fixed while a siege is coming or running (no claiming over the besiegers' camp, no
     * unclaiming the battlefield) and while the fallen city awaits its conqueror. Null when claims may change.
     */
    private static String frozenKey(CommandSourceStack source, MinecraftServer server, City city) {
        if (!isPlayer(source) || city == null) return null;
        ValorisData data = ValorisData.get(server);
        if (data.siegeOfCity(city.teamId).isPresent()) return SIEGE_KEY;
        if (data.conquestOfCity(city.teamId).isPresent()) return CONQUEST_KEY;
        return null;
    }

    /** The first claim may go anywhere; every further one must share a side with a chunk the team already owns. */
    private static boolean touchesOwnLand(ChunkTeamData teamData, ChunkDimPos pos) {
        if (teamData.getClaimedChunks().isEmpty()) return true;
        var manager = FTBChunksAPI.api().getManager();
        for (int[] d : SIDES) {
            ClaimedChunk n = manager.getChunk(pos.offset(d[0], d[1]));
            if (n != null && n.getTeamData() == teamData) return true;
        }
        return false;
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * True if unclaiming this chunk would cut the team's land in two: its own neighbours would no longer reach each
     * other without it. Only the piece this chunk belongs to is checked, so land that was already split before
     * does not block every unclaim.
     */
    private static boolean splitsLand(ChunkTeamData teamData, ChunkDimPos removed) {
        Set<ChunkDimPos> owned = new HashSet<>();
        for (ClaimedChunk c : teamData.getClaimedChunks()) owned.add(c.getPos());
        owned.remove(removed);

        List<ChunkDimPos> neighbours = new ArrayList<>();
        for (int[] d : SIDES) {
            ChunkDimPos n = removed.offset(d[0], d[1]);
            if (owned.contains(n)) neighbours.add(n);
        }
        if (neighbours.size() < 2) return false; // an edge or a lone chunk

        // walk the land from one neighbour (without the removed chunk) until every other neighbour is found
        Set<ChunkDimPos> toFind = new HashSet<>(neighbours.subList(1, neighbours.size()));
        Set<ChunkDimPos> seen = new HashSet<>();
        ArrayDeque<ChunkDimPos> queue = new ArrayDeque<>();
        queue.add(neighbours.get(0));
        seen.add(neighbours.get(0));
        while (!queue.isEmpty()) {
            ChunkDimPos p = queue.poll();
            if (toFind.remove(p) && toFind.isEmpty()) return false;
            for (int[] d : SIDES) {
                ChunkDimPos n = p.offset(d[0], d[1]);
                if (owned.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        return true;
    }

    /**
     * True if the city banner stands in this chunk. Follows the banner when it is moved or replaced (the city keeps
     * its current position); a banner that was destroyed and not replaced yet protects nothing.
     */
    private static boolean holdsBanner(MinecraftServer server, City city, ChunkDimPos pos) {
        if (!city.hasBanner() || !pos.dimension().location().toString().equals(city.bannerDim)) return false;
        BlockPos banner = BlockPos.of(city.bannerPos);
        if (pos.x() != (banner.getX() >> 4) || pos.z() != (banner.getZ() >> 4)) return false;
        return CityService.bannerPresent(server, city);
    }

    // ------------------------------------------------------------ limits (used by ChunkTeamDataImplMixin)

    /** Claim limit FTB should use for this team: the city's limit, 0 for non-city teams, empty for server teams. */
    public static OptionalInt maxClaims(ChunkTeamData teamData) {
        if (teamData.getTeam().isServerTeam()) return OptionalInt.empty();
        MinecraftServer server = teamData.getTeamManager().getServer();
        if (server == null || server.overworld() == null) return OptionalInt.empty();
        return OptionalInt.of(ValorisData.get(server).byTeam(teamData.getTeam().getId())
                .map(TechService::claimLimit).orElse(0));
    }

    // ------------------------------------------------------------ server start / updates

    /** Turns off waypoint sharing and releases every force-loaded chunk left from before. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        FTBChunksWorldConfig.WAYPOINT_SHARING_SERVER.set(false);
        FTBChunksWorldConfig.WAYPOINT_SHARING_PARTY.set(false);
        FTBChunksWorldConfig.WAYPOINT_SHARING_PLAYERS.set(false);

        CommandSourceStack src = server.createCommandSourceStack();
        int released = 0;
        for (ClaimedChunk chunk : List.copyOf(FTBChunksAPI.api().getManager().getAllClaimedChunks())) {
            if (chunk.isForceLoaded()
                    && chunk.getTeamData().unForceLoad(src, chunk.getPos(), false, true).isSuccess()) {
                released++;
            }
        }
        if (released > 0) com.valoris.valoris2.LOGGER.info("Released {} force-loaded chunks", released);
    }

    /** Re-sends the claimed/limit numbers to the city's online members (e.g. after a new claim limit). */
    public static void syncLimits(MinecraftServer server, City city) {
        FTBTeamsAPI.api().getManager().getTeamByID(city.teamId).ifPresent(team -> {
            ChunkTeamData td = FTBChunksAPI.api().getManager().getOrCreateData(team);
            SendGeneralDataPacket.send(td, team.getOnlineMembers());
        });
    }
}
