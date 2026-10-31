package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;

/**
 * No cave spider spawners in mineshafts: every corridor counts as if its spawner were already placed. Spider
 * corridors keep their cobwebs.
 */
@Mixin(MineshaftPieces.MineShaftCorridor.class)
public abstract class MineshaftSpiderMixin {
    @Shadow private boolean hasPlacedSpider;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void valoris$noSpawner(CallbackInfo ci) {
        this.hasPlacedSpider = true;
    }
}
