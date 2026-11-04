package com.valoris.gate;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A portcullis block: a 4 px thick dark iron grid (bars, riveted bands, spikes) standing along the X or Z axis. Placed
 * freely; the connected blocks of one plane are one gate (Gates). Right click with anything but a portcullis raises
 * or lowers it. SHIFT (0-15) lifts the grid pixel by pixel while the gate moves (the raising and lowering animation);
 * BOTTOM shows spikes on the lowest block.
 */
public class PortcullisBlock extends Block {
    public static final MapCodec<PortcullisBlock> CODEC = simpleCodec(PortcullisBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty SHIFT = IntegerProperty.create("shift", 0, 15);
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

    private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);

    public PortcullisBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(SHIFT, 0).setValue(BOTTOM, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, SHIFT, BOTTOM);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    /** Across the player's view, or in line with a gate it is placed against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getHorizontalDirection().getClockWise().getAxis();
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if (against.getBlock() instanceof PortcullisBlock) axis = against.getValue(AXIS);
        return defaultBlockState().setValue(AXIS, axis)
                .setValue(BOTTOM, !(context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof PortcullisBlock));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) return state.setValue(BOTTOM, !(neighbor.getBlock() instanceof PortcullisBlock));
        return state;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() == asItem()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // builds on
        if (!level.isClientSide && player instanceof ServerPlayer sp) Gates.toggle((ServerLevel) level, pos, sp);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) Gates.toggle((ServerLevel) level, pos, sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
