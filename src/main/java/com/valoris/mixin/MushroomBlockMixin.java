package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.Config;
import com.valoris.farming.SoilQuality;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Mushrooms spread at the soil speed of the biome, like crops (they fire no crop growth event). */
@Mixin(MushroomBlock.class)
public abstract class MushroomBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void valoris2$slowSpread(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!Config.SOIL_QUALITY_ENABLED.get()) return;
        if (random.nextDouble() >= SoilQuality.speed(level, pos, state)) ci.cancel();
    }
}
