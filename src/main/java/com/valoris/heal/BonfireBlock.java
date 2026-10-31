package com.valoris.heal;

import com.mojang.serialization.MapCodec;
import com.valoris.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A large fire two blocks tall: a ring of stones with a cone of logs over the flames. Within {@link #RADIUS}
 * blocks it warms cold players (climate.Climate) and lets resting players heal (CampfireRest), like a campfire
 * close by. The lower half carries the whole model, the upper half is an invisible part of it; breaking either
 * breaks both. Standing in it burns.
 */
public class BonfireBlock extends Block {
    public static final MapCodec<BonfireBlock> CODEC = simpleCodec(BonfireBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Reach of the warmth and the rest, in blocks. */
    public static final int RADIUS = 10;

    private static final VoxelShape LOWER = Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(2, 5, 2, 14, 16, 14));
    private static final VoxelShape UPPER = Block.box(4, 0, 4, 12, 12, 12);

    public BonfireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /** One half without the other disappears (the lower half drops the item, see the loot table). */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            boolean partner = neighbor.is(this) && neighbor.getValue(HALF) != half;
            if (!partner) return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Breaking the upper half breaks the lower one, which drops the bonfire. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) level.destroyBlock(below, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && entity instanceof LivingEntity && !entity.fireImmune()) {
            entity.hurt(level.damageSources().inFire(), 1.0F);
        }
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        if (random.nextInt(8) == 0) {
            level.playLocalSound(x, y + 0.5, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(3) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    x + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1), y + 1.8,
                    z + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1), 0, 0.07, 0);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.6, y + 0.3 + random.nextDouble() * 1.2,
                    z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.LAVA, x, y + 0.5, z, 0, 0, 0);
        }
    }

    /** Is there a bonfire within {@link #RADIUS} blocks (3 up or down) of the position? */
    public static boolean near(ServerLevel level, BlockPos center) {
        Block bonfire = ModBlocks.BONFIRE.get();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -3, -RADIUS), center.offset(RADIUS, 3, RADIUS))) {
            if (level.getBlockState(p).is(bonfire)) return true;
        }
        return false;
    }
}
