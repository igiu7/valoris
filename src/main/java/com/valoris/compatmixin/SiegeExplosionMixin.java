package com.valoris.compatmixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Siege Machines: missiles also destroy blocks in water and behind water. An explosion's rays lose strength on
 * every block by its blast resistance, and water has 100, so normally nothing under or behind water breaks.
 * Here water does not hold the blast back (a waterlogged block counts as the block alone), and the water itself
 * is never removed.
 */
@Mixin(targets = "ru.magistu.siegemachines.entity.projectile.ExplosiveBasedExplosionDamageCalculator")
public abstract class SiegeExplosionMixin {
    private static final String SUPER_RESISTANCE = "Lnet/minecraft/world/level/ExplosionDamageCalculator;getBlockExplosionResistance("
            + "Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)Ljava/util/Optional;";

    @ModifyArg(method = "getBlockExplosionResistance", at = @At(value = "INVOKE", target = SUPER_RESISTANCE), index = 3)
    private BlockState valoris$waterIsNoBlock(BlockState state) {
        return state.getBlock() instanceof LiquidBlock ? Blocks.AIR.defaultBlockState() : state;
    }

    @ModifyArg(method = "getBlockExplosionResistance", at = @At(value = "INVOKE", target = SUPER_RESISTANCE), index = 4)
    private FluidState valoris$ignoreWater(FluidState fluid) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Inject(method = "shouldBlockExplode", at = @At("HEAD"), cancellable = true)
    private void valoris$keepWater(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof LiquidBlock) cir.setReturnValue(false);
    }
}
