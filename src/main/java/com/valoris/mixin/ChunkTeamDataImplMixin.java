package com.valoris.mixin;

import java.util.OptionalInt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.valoris.city.ClaimLimitHook;
import com.valoris.kingdom.KingdomProtection;

import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Chunks' own limits are replaced by the Valoris ones: a city may claim exactly its city limit (which the
 * claim manager then shows), and no team may force-load chunks (0/0).
 */
@Mixin(value = ChunkTeamDataImpl.class, remap = false)
public abstract class ChunkTeamDataImplMixin {
    @Inject(method = "getMaxClaimChunks", at = @At("HEAD"), cancellable = true)
    private void valoris$cityClaimLimit(CallbackInfoReturnable<Integer> cir) {
        OptionalInt limit = ClaimLimitHook.maxClaims((ChunkTeamDataImpl) (Object) this);
        if (limit.isPresent()) cir.setReturnValue(limit.getAsInt());
    }

    @Inject(method = "getMaxForceLoadChunks", at = @At("HEAD"), cancellable = true)
    private void valoris$noForceLoading(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }

    /**
     * Everyone from any city of a kingdom may do everything in every city of that kingdom, like its residents; so may
     * the winners of a siege in the fallen city during its looting time (war.Looting).
     */
    @Inject(method = "canPlayerUse", at = @At("HEAD"), cancellable = true)
    private void valoris$kingdomShares(ServerPlayer player, PrivacyProperty property, CallbackInfoReturnable<Boolean> cir) {
        java.util.UUID team = ((ChunkTeamDataImpl) (Object) this).getTeam().getId();
        if (KingdomProtection.sameKingdom(player, team) || com.valoris.war.Looting.mayLoot(player, team)) {
            cir.setReturnValue(true);
        }
    }
}
