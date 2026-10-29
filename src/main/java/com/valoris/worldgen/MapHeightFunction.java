package com.valoris.worldgen;

import com.mojang.serialization.MapCodec;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function "valoris2:map_height": the surface height of the Valoris map at this column (blocks).
 * data/valoris2/worldgen/density_function/map/terrain.json turns it into the terrain density.
 */
public final class MapHeightFunction implements DensityFunction.SimpleFunction {
    public static final MapHeightFunction INSTANCE = new MapHeightFunction();
    public static final KeyDispatchDataCodec<MapHeightFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

    private MapHeightFunction() {}

    @Override
    public double compute(FunctionContext context) {
        return MapDesign.exactHeight(context.blockX(), context.blockZ());
    }

    @Override
    public double minValue() {
        return 8;
    }

    @Override
    public double maxValue() {
        return MapDesign.MAX_HEIGHT;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
