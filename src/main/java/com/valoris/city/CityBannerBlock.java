package com.valoris.city;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The city banner: a sword in the stone. Placing it founds a city; besiegers win by destroying it.
 * Who may break it is decided in SiegeEvents. It drops nothing (no loot table) and does not need
 * a block below it - removing the ground does not destroy it.
 */
public class CityBannerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CityBannerBlock> CODEC = simpleCodec(CityBannerBlock::new);

    // the rock plus the part of the sword inside the block space
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1.5, 15, 6.5, 14.5),
            Block.box(4.5, 6.5, 4.5, 11.5, 10, 11),
            Block.box(6.5, 10, 6.5, 9.5, 16, 9.5));

    public CityBannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
