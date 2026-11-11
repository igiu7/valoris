package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.farming.OreRights;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Create's drills, saws and the like (placed): no ores above the tier of the city owning the land (farming.OreRights). */
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity")
public abstract class CreateDrillOreMixin {
    @Shadow(remap = false) protected BlockPos breakingPos;

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false)
    private void valoris$oreRights(BlockState state, float hardness, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (breakingPos != null && !OreRights.machineMayBreak(self.getLevel(), breakingPos, state)) cir.setReturnValue(false);
    }
}
