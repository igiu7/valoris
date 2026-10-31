package com.valoris.plant;

import com.mojang.serialization.MapCodec;
import com.valoris.registry.ModPlants;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

/** Rice: an ordinary crop on farmland (8 growth stages, 4 looks). Gives rice and rice seeds when ripe. */
public class RiceCropBlock extends CropBlock {
    public static final MapCodec<RiceCropBlock> CODEC = simpleCodec(RiceCropBlock::new);

    public RiceCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModPlants.RICE_SEEDS.get();
    }
}
