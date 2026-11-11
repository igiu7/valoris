package com.valoris.farming;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Mining rights by soil tier: ores of tier 2 to 4 (OreZones.oreTier) are mined only by the residents of a city that
 * claims land of that soil tier or higher - one claimed chunk with a bit of tier 3 soil lets its residents mine
 * tier 3, 2 and 1 ores anywhere. Players in no city mine tier 1 ores only. Explosions with no player behind them and
 * Create's drills (mixin compatmixin.CreateDrillOreMixin) go by the city owning the land they break in (none: tier 1).
 * Ore blocks placed by players can always be taken again.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class OreRights extends SavedData {
    private static final String NAME = "valoris_placed_ores";
    /** how long (ticks) a city's best tier is kept before it is worked out again */
    private static final long REFRESH = 6000;

    /** dimension -> positions of ore blocks placed by players */
    private final Map<String, Set<Long>> placed = new HashMap<>();

    private record Best(int tier, long at) {}

    /** city team -> its best soil tier */
    private static final Map<UUID, Best> CITY = new HashMap<>();
    /** overworld chunk -> its best soil tier (only from loaded chunks, whose surface is known) */
    private static final Map<Long, Integer> CHUNK = new HashMap<>();

    private static OreRights get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(OreRights::new, OreRights::load, null), NAME);
    }

    /** Tier 2 to 4 ores; tier 1 is free for all and "none" ores (5) are not ruled here. */
    private static int ruledTier(BlockState state) {
        int t = OreZones.oreTier(state);
        return t >= 2 && t <= 4 ? t : 0;
    }

    // ------------------------------------------------------------------ tiers

    /** The highest soil tier of the city's claimed overworld land (1 if none). */
    public static int cityTier(MinecraftServer server, City city) {
        long now = server.overworld().getGameTime();
        Best best = CITY.get(city.teamId);
        if (best != null && now - best.at() < REFRESH && now >= best.at()) return best.tier();
        ServerLevel level = server.overworld();
        int tier = 1;
        var team = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().getTeamByID(city.teamId);
        if (team.isPresent()) {
            for (ClaimedChunk c : FTBChunksAPI.api().getManager().getOrCreateData(team.get()).getClaimedChunks()) {
                if (!c.getPos().dimension().equals(Level.OVERWORLD)) continue;
                tier = Math.max(tier, chunkTier(level, c.getPos().x(), c.getPos().z()));
                if (tier >= 4) break;
            }
        }
        CITY.put(city.teamId, new Best(tier, now));
        return tier;
    }

    /** The highest soil tier in a chunk: its surface biome on a 4 x 4 grid, as OreZones reads it. */
    private static int chunkTier(ServerLevel level, int cx, int cz) {
        long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
        Integer known = CHUNK.get(key);
        if (known != null) return known;
        boolean loaded = level.hasChunk(cx, cz);
        int tier = 1;
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                int x = (cx << 4) + qx * 4 + 2, z = (cz << 4) + qz * 4 + 2;
                int y = loaded ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) : level.getSeaLevel();
                tier = Math.max(tier, SoilQuality.tier(level, x, z,
                        level.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))));
            }
        }
        if (loaded) CHUNK.put(key, tier);
        return tier;
    }

    /** The highest ore tier a player may mine: his city's best soil tier, or 1 without a city. */
    public static int allowed(ServerPlayer player) {
        return Roles.cityOfMember(player).filter(c -> c.founded).map(c -> cityTier(player.getServer(), c)).orElse(1);
    }

    /** The highest ore tier that may be broken at pos without a player: that of the city owning the land. */
    public static int allowedAt(ServerLevel level, BlockPos pos) {
        Optional<City> city = com.valoris.war.SiegeEvents.cityAt(ValorisData.get(level.getServer()), level, pos);
        return city.filter(c -> c.founded).map(c -> cityTier(level.getServer(), c)).orElse(1);
    }

    private static boolean placedByPlayer(ServerLevel level, BlockPos pos) {
        return get(level.getServer()).placed.getOrDefault(level.dimension().location().toString(), Set.of()).contains(pos.asLong());
    }

    /** For machines (Create's drills): may the ore at pos be broken there. */
    public static boolean machineMayBreak(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel sl)) return true;
        int t = ruledTier(state);
        return t == 0 || t <= allowedAt(sl, pos) || placedByPlayer(sl, pos);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) return;
        int t = ruledTier(event.getState());
        if (t == 0) return;
        if (player.isCreative() || placedByPlayer(level, event.getPos())) return;
        int allowed = allowed(player);
        if (t <= allowed) return;
        event.setCanceled(true);
        boolean inCity = Roles.cityOfMember(player).filter(c -> c.founded).isPresent();
        player.displayClientMessage(Msg.text(inCity
                ? "This is a tier " + t + " ore. Your city's land reaches only tier " + allowed + " soil: claim tier " + t
                        + " land or trade for it."
                : "This is a tier " + t + " ore. Only the residents of a city with tier " + t + " land can mine it.",
                ChatFormatting.RED), true);
    }

    /** Placed ore blocks are remembered; once broken they are forgotten. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer) || ruledTier(event.getPlacedBlock()) == 0) return;
        OreRights data = get(level.getServer());
        data.placed.computeIfAbsent(level.dimension().location().toString(), k -> new HashSet<>()).add(event.getPos().asLong());
        data.setDirty();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        OreRights data = get(level.getServer());
        Set<Long> set = data.placed.get(level.dimension().location().toString());
        if (set != null && set.remove(event.getPos().asLong())) data.setDirty();
    }

    /** No blowing up ores one may not mine: the player behind the blast, else the land's city, decides. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LivingEntity source = event.getExplosion().getIndirectSourceEntity();
        Integer byPlayer = source instanceof ServerPlayer p ? (p.isCreative() ? 4 : allowed(p)) : null;
        event.getAffectedBlocks().removeIf(pos -> {
            int t = ruledTier(level.getBlockState(pos));
            if (t == 0 || placedByPlayer(level, pos)) return false;
            return t > (byPlayer != null ? byPlayer : allowedAt(level, pos));
        });
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        CITY.clear();
        CHUNK.clear();
    }

    // ------------------------------------------------------------------ saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        placed.forEach((dim, set) -> {
            if (set.isEmpty()) return;
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim);
            t.put("pos", new LongArrayTag(set.stream().mapToLong(Long::longValue).toArray()));
            list.add(t);
        });
        tag.put("placed", list);
        return tag;
    }

    public static OreRights load(CompoundTag tag, HolderLookup.Provider registries) {
        OreRights data = new OreRights();
        for (Tag raw : tag.getList("placed", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Set<Long> set = new HashSet<>();
            for (long p : t.getLongArray("pos")) set.add(p);
            data.placed.put(t.getString("dim"), set);
        }
        return data;
    }
}
