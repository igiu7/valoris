package com.valoris.war;

import java.util.HashMap;
import java.util.Map;

import com.valoris.data.ValorisData;
import com.valoris.tech.TechService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fortified Masonry (tech wallResistancePercent): on the land of a realm knowing it, that percentage of the walls a
 * siege machine's blast would destroy stays standing - each wall block survives with that chance. Walls are every
 * stone block: stone, cobblestone, bricks, deepslate, sandstone, basalt, blackstone, quartz, prismarine... and Create's
 * stones (asurine, crimsite, limestone, ochrum, scorchia, scoria, veridium) with all their cut, polished, brick and
 * pillar forms (compatmixin.SiegeExplosionMixin).
 */
public final class Masonry {
    private Masonry() {}

    private static final String[] STONE = {"stone", "cobble", "brick", "deepslate", "andesite", "diorite", "granite",
            "tuff", "calcite", "basalt", "blackstone", "sandstone", "dripstone", "quartz", "prismarine", "purpur",
            "asurine", "crimsite", "limestone", "ochrum", "scorchia", "scoria", "veridium", "mossy_", "polished_"};
    /** contain a stone word but are not wall stone */
    private static final String[] NOT = {"redstone", "glowstone", "lodestone", "stonecutter", "grindstone", "_ore",
            "button", "pressure_plate", "infested"};

    /** One explosion's survival chances by chunk: it asks for the same chunks many times. */
    private static Explosion cached;
    private static final Map<Long, Float> BY_CHUNK = new HashMap<>();

    public static boolean isWall(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        for (String n : NOT) if (path.contains(n)) return false;
        for (String s : STONE) if (path.contains(s)) return true;
        return false;
    }

    /** True if this wall block, which the blast would destroy, stays standing (its realm knows Fortified Masonry). */
    public static boolean survives(Explosion explosion, ServerLevel level, BlockPos pos, BlockState state) {
        if (!isWall(state)) return false;
        if (explosion != cached) {
            cached = explosion;
            BY_CHUNK.clear();
        }
        float chance = BY_CHUNK.computeIfAbsent(ChunkPos.asLong(pos), k -> {
            ValorisData data = ValorisData.get(level.getServer());
            return SiegeEvents.cityAt(data, level, pos).map(c -> TechService.wallResistancePercent(c) / 100.0F).orElse(0.0F);
        });
        return chance > 0 && level.random.nextFloat() < chance;
    }
}
