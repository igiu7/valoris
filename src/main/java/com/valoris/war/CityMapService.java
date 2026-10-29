package com.valoris.war;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.Config;
import com.valoris.city.Roles;
import com.valoris.data.Relation;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.net.CityMapPayload;
import com.valoris.util.Errors;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Surveys a city's claimed land for the siege map.
 *
 * Every block column is scanned from the top down to the natural ground. Blocks whose id marks them as
 * player-made (bricks, planks, polished stone, glass... see Config siegeMap.builtKeywords; logs too unless they
 * stand under leaves) are "built". A column with built blocks above an air gap of 2+ blocks has a roof over a
 * room, so it is the inside of a building. A solid built stack of 2+ blocks without a roof is a tall structure.
 * Tall structures touching a building are its outer walls (up to 3 thick); the remaining tall structures are city
 * walls (tiny bits such as lamp posts are dropped, tiny roofed bits inside a wall are gates). This works for walls
 * and buildings of any thickness and shape.
 */
public final class CityMapService {
    private static final int MAX_CHUNKS_SIDE = 16;
    private static final int SCAN_DEPTH = 64;
    private static final long CACHE_MS = 30_000L;

    // raw column kinds during the scan
    private static final byte R_GROUND = 1, R_WATER = 4, R_PAVED = 5, R_TALL = 10, R_COVERED = 11;
    // block kinds
    private static final int AIR = 0, SKIP = 1, LEAVES = 2, LOG = 3, BUILT = 4, FLUID = 5, NATURAL = 6;

    private record Cached(long time, CityMapPayload map) {}

    private static final Map<UUID, Cached> CACHE = new HashMap<>();
    private static final Map<Block, Integer> KIND = new HashMap<>();

    private CityMapService() {}

    public static void request(ServerPlayer player, String cityName) {
        CityMapPayload map;
        try {
            map = forPlayer(player, cityName);
        } catch (CommandSyntaxException e) {
            map = CityMapPayload.error(cityName, e.getRawMessage().getString());
        } catch (RuntimeException e) {
            com.valoris.valoris2.LOGGER.error("Could not survey city {}", cityName, e);
            map = CityMapPayload.error(cityName, "The survey failed: " + e.getClass().getSimpleName());
        }
        PacketDistributor.sendToPlayer(player, map);
    }

    private static CityMapPayload forPlayer(ServerPlayer player, String cityName) throws CommandSyntaxException {
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        City city = data.cityByName(cityName).filter(c -> c.founded).orElseThrow(() -> Errors.of("No such city."));

        Optional<City> own = Roles.cityOfMember(player);
        Optional<Realm> mine = Realms.of(player);
        Optional<Realm> theirs = Realms.ofCity(data, city);
        boolean allowed = player.hasPermissions(2)
                || own.map(c -> c.teamId.equals(city.teamId)).orElse(false)
                || (mine.isPresent() && theirs.isPresent() && (mine.get().id().equals(theirs.get().id())
                    || data.relation(mine.get().id(), theirs.get().id())
                        .filter(r -> r == Relation.WAR || r == Relation.ALLIANCE).isPresent()));
        if (!allowed) throw Errors.of("Your scouts can only survey your own cities and those of your allies and enemies.");

        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(city.teamId);
        if (cached != null && now - cached.time() < CACHE_MS) return cached.map();
        CityMapPayload map = build(server, city, theirs.map(Realm::title).orElse(""));
        CACHE.put(city.teamId, new Cached(now, map));
        return map;
    }

    // ------------------------------------------------------------------ survey

    private static CityMapPayload build(MinecraftServer server, City city, String kingdomName) throws CommandSyntaxException {
        var team = FTBTeamsAPI.api().getManager().getTeamByID(city.teamId)
                .orElseThrow(() -> Errors.of("That city has no team."));
        var claims = FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks();

        String dimId = city.hasBanner() ? city.bannerDim : "";
        if (dimId.isEmpty()) {
            Map<String, Integer> count = new HashMap<>();
            for (ClaimedChunk c : claims) count.merge(c.getPos().dimension().location().toString(), 1, Integer::sum);
            dimId = count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        }
        if (dimId.isEmpty()) throw Errors.of("That city has not claimed any land.");
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimId));
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) throw Errors.of("The city's dimension is not loaded.");

        List<ChunkPos> chunks = new ArrayList<>();
        for (ClaimedChunk c : claims) if (c.getPos().dimension().equals(dimKey)) chunks.add(c.getPos().chunkPos());
        if (chunks.isEmpty()) throw Errors.of("That city has not claimed any land.");

        BlockPos banner = city.hasBanner() && city.bannerDim.equals(dimId) ? BlockPos.of(city.bannerPos) : null;
        ChunkPos center = banner != null ? new ChunkPos(banner) : chunks.get(0);
        int half = MAX_CHUNKS_SIDE / 2;
        chunks.removeIf(c -> Math.abs(c.x - center.x) >= half || Math.abs(c.z - center.z) >= half);

        int minCx = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE, maxCz = Integer.MIN_VALUE;
        for (ChunkPos c : chunks) {
            minCx = Math.min(minCx, c.x); maxCx = Math.max(maxCx, c.x);
            minCz = Math.min(minCz, c.z); maxCz = Math.max(maxCz, c.z);
        }
        int w = (maxCx - minCx + 1) * 16, d = (maxCz - minCz + 1) * 16;
        int ox = minCx * 16, oz = minCz * 16;

        byte[] raw = new byte[w * d];
        int[] groundY = new int[w * d];
        int[] height = new int[w * d];
        KIND.clear(); // the keyword list may have changed

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] out = new int[3];
        for (ChunkPos c : chunks) {
            ChunkAccess chunk = readChunk(level, c);
            if (chunk == null) continue;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int wx = c.getMinBlockX() + lx, wz = c.getMinBlockZ() + lz;
                    scan(level, chunk, lx, lz, wx, wz, pos, out);
                    int i = (wz - oz) * w + (wx - ox);
                    raw[i] = (byte) out[0];
                    groundY[i] = out[1];
                    height[i] = out[2];
                }
            }
        }

        byte[] type = classify(raw, w, d);

        int base = Integer.MAX_VALUE;
        for (int i = 0; i < raw.length; i++) if (raw[i] != 0) base = Math.min(base, groundY[i]);
        byte[] ground = new byte[w * d];
        byte[] tall = new byte[w * d];
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) continue;
            ground[i] = (byte) Math.min(255, Math.max(0, groundY[i] - base));
            tall[i] = (byte) Math.min(255, Math.max(0, height[i]));
        }
        return new CityMapPayload(city.name, kingdomName, "", ox, oz, w, d, type, ground, tall, base,
                banner != null, banner == null ? 0 : banner.getX(), banner == null ? 0 : banner.getZ(), chunks.size());
    }

    private static int kind(BlockState state) {
        if (state.isAir()) return AIR;
        return KIND.computeIfAbsent(state.getBlock(), block -> {
            if (state.is(BlockTags.LEAVES)) return LEAVES;
            if (block instanceof LiquidBlock) return FLUID;
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);
            boolean ore = path.endsWith("_ore") || path.contains("ore_") || path.startsWith("raw_");
            if (!ore) {
                for (String word : Config.SIEGE_MAP_BUILT_KEYWORDS.get()) if (path.contains(word)) return BUILT;
            }
            if (state.is(BlockTags.LOGS)) return LOG;
            return NATURAL;
        });
    }

    /** out = {raw kind, ground y, height of the structure above the ground}. */
    /**
     * A loaded chunk as it is; an unloaded one is only read from disk (blocks and heightmaps) and never promoted to
     * a full, entity-ticking chunk. Loading 80 full chunks for a survey made them all unload again a moment later,
     * entities included, which other mods (Sable ships) do not always survive.
     */
    private static ChunkAccess readChunk(ServerLevel level, ChunkPos c) {
        ChunkAccess loaded = level.getChunkSource().getChunkNow(c.x, c.z);
        if (loaded != null) return loaded;
        try {
            return level.getChunkSource().getChunk(c.x, c.z, ChunkStatus.EMPTY, true);
        } catch (RuntimeException e) {
            com.valoris.valoris2.LOGGER.warn("Siege map: could not read chunk {} {}", c.x, c.z, e);
            return null;
        }
    }

    private static void scan(ServerLevel level, ChunkAccess chunk, int lx, int lz, int wx, int wz,
                             BlockPos.MutableBlockPos pos, int[] out) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
        int minY = Math.max(level.getMinBuildHeight(), top - SCAN_DEPTH);
        boolean tree = false, seen = false, covered = false, water = false;
        int air = 0, built = 0, topBuilt = top, ground = minY;
        for (int y = top; y >= minY; y--) {
            BlockState state = chunk.getBlockState(pos.set(wx, y, wz));
            int k = kind(state);
            if (k == NATURAL && !state.blocksMotion() && state.getFluidState().isEmpty()) k = SKIP; // grass, flowers, snow
            if (k == LOG && tree) continue;                     // a tree trunk
            if (k == LOG) k = BUILT;                             // a log without leaves above: palisade, timber frame
            if (k == AIR || k == SKIP) {
                if (seen) air++;
                continue;
            }
            if (k == LEAVES) {
                if (seen) air++; else tree = true;
                continue;
            }
            if (k == BUILT) {
                if (seen && air >= 2) covered = true;
                if (!seen) topBuilt = y;
                seen = true;
                air = 0;
                built++;
                continue;
            }
            // natural ground or water
            if (seen && air >= 2) covered = true;
            ground = y;
            water = k == FLUID;
            break;
        }
        byte raw;
        if (!seen) raw = water ? R_WATER : R_GROUND;
        else if (covered) raw = R_COVERED;
        else if (built >= 2) raw = R_TALL;
        else raw = R_PAVED;
        out[0] = raw;
        out[1] = ground;
        out[2] = seen ? topBuilt - ground : 0;
    }

    // ------------------------------------------------------------------ walls and buildings

    private static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] N8 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static List<List<Integer>> components(byte[] grid, int w, int d, byte value, int[][] neighbours) {
        List<List<Integer>> out = new ArrayList<>();
        boolean[] seen = new boolean[grid.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < grid.length; start++) {
            if (grid[start] != value || seen[start]) continue;
            List<Integer> comp = new ArrayList<>();
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                comp.add(i);
                int x = i % w, z = i / w;
                for (int[] n : neighbours) {
                    int nx = x + n[0], nz = z + n[1];
                    if (nx < 0 || nz < 0 || nx >= w || nz >= d) continue;
                    int j = nz * w + nx;
                    if (!seen[j] && grid[j] == value) {
                        seen[j] = true;
                        queue.add(j);
                    }
                }
            }
            out.add(comp);
        }
        return out;
    }

    private static boolean touches(byte[] grid, int w, int d, List<Integer> comp, byte value) {
        for (int i : comp) {
            int x = i % w, z = i / w;
            for (int[] n : N8) {
                int nx = x + n[0], nz = z + n[1];
                if (nx >= 0 && nz >= 0 && nx < w && nz < d && grid[nz * w + nx] == value) return true;
            }
        }
        return false;
    }

    private static byte[] classify(byte[] raw, int w, int d) {
        byte[] g = raw.clone();
        // 1. tiny roofed spots inside a wall are gates and arches
        for (List<Integer> comp : components(g, w, d, R_COVERED, N4)) {
            if (comp.size() <= 3 && touches(g, w, d, comp, R_TALL)) for (int i : comp) g[i] = R_TALL;
        }
        // 2. tall structures around a roof are the building's own walls (up to 3 blocks thick)
        for (int pass = 0; pass < 3; pass++) {
            Set<Integer> grow = new HashSet<>();
            for (int i = 0; i < g.length; i++) {
                if (g[i] != R_TALL) continue;
                int x = i % w, z = i / w;
                for (int[] n : N4) {
                    int nx = x + n[0], nz = z + n[1];
                    if (nx >= 0 && nz >= 0 && nx < w && nz < d && g[nz * w + nx] == R_COVERED) {
                        grow.add(i);
                        break;
                    }
                }
            }
            if (grow.isEmpty()) break;
            for (int i : grow) g[i] = R_COVERED;
        }
        // 3. what is left tall is a city wall, unless it is just a post or a statue
        for (List<Integer> comp : components(g, w, d, R_TALL, N8)) {
            if (comp.size() < 4) for (int i : comp) g[i] = R_PAVED;
        }
        byte[] type = new byte[g.length];
        for (int i = 0; i < g.length; i++) {
            type[i] = switch (g[i]) {
                case R_GROUND -> CityMapPayload.GROUND;
                case R_WATER -> CityMapPayload.WATER;
                case R_PAVED -> CityMapPayload.PAVED;
                case R_TALL -> CityMapPayload.WALL;
                case R_COVERED -> CityMapPayload.BUILDING;
                default -> CityMapPayload.OUTSIDE;
            };
        }
        return type;
    }
}
