package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.farming.OreRights;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Create's drills on moving contraptions: no ores above the tier of the city owning the land (farming.OreRights). */
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour")
public abstract class CreateContraptionDrillOreMixin {
    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false)
    private void valoris$oreRights(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!OreRights.machineMayBreak(level, pos, state)) cir.setReturnValue(false);
    }
}
