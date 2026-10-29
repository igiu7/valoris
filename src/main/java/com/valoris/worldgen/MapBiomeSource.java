package com.valoris.worldgen;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Biome source "valoris2:map": the biome of every column is drawn from the Valoris map (MapDesign.biome). */
public final class MapBiomeSource extends BiomeSource {
    public static final MapCodec<MapBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, i.stable(MapBiomeSource::new)));

    private final Map<String, Holder<Biome>> biomes = new HashMap<>();

    private MapBiomeSource(HolderGetter<Biome> getter) {
        for (String name : MapDesign.BIOMES) {
            ResourceLocation id = name.contains(":") ? ResourceLocation.parse(name) : ResourceLocation.withDefaultNamespace(name);
            biomes.put(name, getter.getOrThrow(ResourceKey.create(Registries.BIOME, id)));
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.values().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // x, z are quarter positions (4 blocks): take the middle of the quarter
        Holder<Biome> b = biomes.get(MapDesign.biome((x << 2) + 2, (z << 2) + 2));
        return b != null ? b : biomes.get("ocean");
    }
}
