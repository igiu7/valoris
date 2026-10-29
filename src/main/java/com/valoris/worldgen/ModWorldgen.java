package com.valoris.worldgen;

import com.mojang.serialization.MapCodec;
import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Valoris map world type (world preset valoris2:valoris_map): MapDesign shapes the land and picks the biomes,
 * everything else is vanilla world generation without structures. For a server: level-type=valoris2:valoris_map
 * in server.properties, on a new world.
 */
public final class ModWorldgen {
    private ModWorldgen() {}

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, valoris2.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, valoris2.MODID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, valoris2.MODID);

    static {
        DENSITY_FUNCTIONS.register("map_height", MapHeightFunction.CODEC::codec);
        BIOME_SOURCES.register("map", () -> MapBiomeSource.CODEC);
        CHUNK_GENERATORS.register("map", () -> MapChunkGenerator.CODEC);
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTIONS.register(modBus);
        BIOME_SOURCES.register(modBus);
        CHUNK_GENERATORS.register(modBus);
        NeoForge.EVENT_BUS.addListener(ModWorldgen::onCreateSpawn);
    }

    /** A new Valoris map world starts on the plains of the heart continent, not wherever vanilla would search. */
    private static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(level.getChunkSource().getGenerator() instanceof MapChunkGenerator)) return;
        int[] p = MapDesign.spawnPoint();
        level.setDefaultSpawnPos(new BlockPos(p[0], p[1] + 1, p[2]), 0.0F);
        event.setCanceled(true);
        valoris2.LOGGER.info("Valoris map: world spawn set to {} {} {}", p[0], p[1] + 1, p[2]);
    }
}
