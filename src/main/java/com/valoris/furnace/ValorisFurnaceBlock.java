package com.valoris.furnace;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.MenuProvider;

/** Shared block class for the bloomery (steel = false) and the steel furnace (steel = true). */
public class ValorisFurnaceBlock extends AbstractFurnaceBlock {
    public static final MapCodec<ValorisFurnaceBlock> BLOOMERY_CODEC = simpleCodec(p -> new ValorisFurnaceBlock(p, false));
    public static final MapCodec<ValorisFurnaceBlock> STEEL_CODEC = simpleCodec(p -> new ValorisFurnaceBlock(p, true));
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    private final boolean steel;

    public ValorisFurnaceBlock(Properties properties, boolean steel) {
        super(properties);
        this.steel = steel;
    }

    @Override
    protected MapCodec<? extends AbstractFurnaceBlock> codec() {
        return steel ? STEEL_CODEC : BLOOMERY_CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return steel ? ValorisFurnaceBlockEntity.steel(pos, state) : ValorisFurnaceBlockEntity.bloomery(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createFurnaceTicker(level, type, steel ? FurnaceRegistry.STEEL_FURNACE_BE.get() : FurnaceRegistry.BLOOMERY_BE.get());
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof ValorisFurnaceBlockEntity be) {
            player.openMenu((MenuProvider) be);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Fire crackle at the mouth and smoke from the chimney while lit. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        Direction facing = state.getValue(FACING);
        double off = 0.52;
        level.addParticle(ParticleTypes.FLAME, x + facing.getStepX() * off, y + 0.25, z + facing.getStepZ() * off, 0, 0, 0);
        double top = steel ? 2.0 : 1.55;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + top, z, 0, 0.04, 0);
        if (steel && random.nextDouble() < 0.3) {
            level.addParticle(ParticleTypes.LAVA, x, y + top, z, 0, 0, 0);
        }
    }
}
