package com.valoris.plant;

import com.mojang.serialization.MapCodec;
import com.valoris.registry.ModPlants;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/** Flax: an ordinary crop on farmland (8 growth stages, 4 looks). Gives flax and flax seeds when ripe. */
public class FlaxCropBlock extends CropBlock {
    public static final MapCodec<FlaxCropBlock> CODEC = simpleCodec(FlaxCropBlock::new);

    public FlaxCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModPlants.FLAX_SEEDS.get();
    }
}
