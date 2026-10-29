package com.valoris.plant;

import com.mojang.serialization.MapCodec;
import com.valoris.registry.ModPlants;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Dry desert/badlands relative of the medicinal herb bush; grows on sand and terracotta, gives medicinal roots. */
public class DeadMedicinalHerbBlock extends MedicinalHerbBushBlock {
    public static final MapCodec<DeadMedicinalHerbBlock> CODEC = simpleCodec(DeadMedicinalHerbBlock::new);

    public DeadMedicinalHerbBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected Item product() {
        return ModPlants.MEDICINAL_ROOT.get();
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.DIRT);
    }
}
