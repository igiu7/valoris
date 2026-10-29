package com.valoris.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Chunk generator "valoris2:map": the normal noise generator (terrain, caves, aquifers, ores, trees and plants)
 * with no structures at all - no villages, temples, mineshafts, strongholds and so on.
 */
public final class MapChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<MapChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(i, i.stable(MapChunkGenerator::new)));

    public MapChunkGenerator(BiomeSource biomeSource, net.minecraft.core.Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * After the normal decoration (ores, trees, plants...): no ore may lie bare on the surface or on a cliff face.
     * Every ore between a few blocks under the lowest neighbouring surface and the top of the column becomes
     * stone - in this chunk and its neighbours, because ore veins placed from here can reach into them.
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        ChunkPos center = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int cx = center.x - 1; cx <= center.x + 1; cx++) {
            for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        int top = MapDesign.height(x, z);
                        if (top < MapDesign.SEA_LEVEL - 2) continue;
                        int low = Math.min(top, Math.min(Math.min(MapDesign.height(x + 1, z), MapDesign.height(x - 1, z)),
                                Math.min(MapDesign.height(x, z + 1), MapDesign.height(x, z - 1))));
                        for (int y = Math.max(low - 6, level.getMinBuildHeight()); y <= top + 4; y++) {
                            pos.set(x, y, z);
                            if (level.getBlockState(pos).is(Tags.Blocks.ORES)) level.setBlock(pos, stone, 2);
                        }
                    }
                }
            }
        }
    }

    /** No structure sets: nothing is ever placed or searched for. */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, biomeSource, Stream.empty());
    }
}
