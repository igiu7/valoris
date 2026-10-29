package com.valoris.farming;

import java.util.HashMap;
import java.util.Map;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Ores by soil tier. Each ore has a tier (config "ores.oreTiers"); an ore stays in the ground only under biomes whose
 * soil tier is the same or higher, so tier 1 land (plains) holds only tier 1 ores and tier 4 land holds all.
 * The biome is the surface biome of the column, not the cave biome. Works on any ore with a "c:ores/<name>" tag
 * (vanilla, All The Ores, Create...), and the raw ore blocks of big ore veins ("c:storage_blocks/raw_<name>").
 * Applied once, when a new chunk is finished (directly on its data): removed ores become stone, deepslate or netherrack.
 * Only the overworld; chunks generated before keep their ores.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class OreZones {
    private OreZones() {}

    /** block -> ore tier (0 = not a listed ore) */
    private static final Map<Block, Integer> CACHE = new HashMap<>();

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static TagKey<Block> tag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    public static int oreTier(BlockState state) {
        Block block = state.getBlock();
        synchronized (CACHE) {
            Integer cached = CACHE.get(block);
            if (cached != null) return cached;
            int tier = 0;
            for (String entry : Config.ORE_TIERS.get()) {
                int eq = entry.lastIndexOf('=');
                if (eq <= 0) continue;
                String name = entry.substring(0, eq).trim();
                try {
                    String value = entry.substring(eq + 1).trim();
                    // "none" (or 0): never stays in the ground; 5 is above every soil tier
                    int t = value.equalsIgnoreCase("none") ? 5 : Integer.parseInt(value);
                    if (state.is(tag("c:ores/" + name)) || state.is(tag("c:storage_blocks/raw_" + name))) {
                        tier = t <= 0 ? 5 : Math.min(5, t);
                        break;
                    }
                } catch (NumberFormatException | net.minecraft.ResourceLocationException ignored) {
                }
            }
            CACHE.put(block, tier);
            return tier;
        }
    }

    private static BlockState replacement(BlockState ore) {
        String id = BuiltInRegistries.BLOCK.getKey(ore.getBlock()).getPath();
        if (id.contains("deepslate")) return Blocks.DEEPSLATE.defaultBlockState();
        if (id.contains("nether")) return Blocks.NETHERRACK.defaultBlockState();
        if (id.contains("tuff")) return Blocks.TUFF.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !Config.ORE_ZONES_ENABLED.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        try {
            filter(chunk);
        } catch (Throwable t) {
            valoris2.LOGGER.error("Ore zones: failed on chunk {}", chunk.getPos(), t);
        }
    }

    /**
     * Edits the chunk's own block data directly, while the new chunk is being finished and before players get it.
     * No block updates, no other chunks are touched (going through Level.setBlock here makes other mods, e.g. Sable,
     * look up neighbouring chunks that are still generating, and the server freezes).
     */
    private static void filter(LevelChunk chunk) {
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        // soil tier per column, from the surface biome stored in this chunk
        int[][] soil = new int[16][16];
        int[][] byQuart = new int[4][4];
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                int lx = qx * 4 + 2, lz = qz * 4 + 2;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                y = Math.max(chunk.getMinBuildHeight(), Math.min(chunk.getMaxBuildHeight() - 1, y));
                byQuart[qx][qz] = SoilQuality.tier(chunk.getLevel(), baseX + lx, baseZ + lz, chunk.getNoiseBiome(
                        QuartPos.fromBlock(baseX + lx), QuartPos.fromBlock(y), QuartPos.fromBlock(baseZ + lz)));
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) soil[x][z] = byQuart[x >> 2][z >> 2];
        }

        LevelChunkSection[] sections = chunk.getSections();
        int removed = 0;
        for (LevelChunkSection section : sections) {
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(s -> oreTier(s) > 1)) continue; // tier 1 ores stay everywhere
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (oreTier(state) <= soil[x][z]) continue;
                        section.setBlockState(x, y, z, replacement(state), false);
                        removed++;
                    }
                }
            }
        }
        if (removed > 0) {
            chunk.setUnsaved(true);
            if (Config.ORE_ZONES_DEBUG.get()) {
                valoris2.LOGGER.info("Ore zones: removed {} ore blocks from chunk {}", removed, chunk.getPos());
            }
        }
    }
}
