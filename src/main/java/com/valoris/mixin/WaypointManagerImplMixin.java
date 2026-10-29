package com.valoris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.ftb.mods.ftbchunks.client.map.WaypointImpl;
import dev.ftb.mods.ftbchunks.client.map.WaypointManagerImpl;

/**
 * Waypoints are disabled: nothing ever enters the waypoint list - not saved ones, death points, shared ones or
 * ones from commands - so none show on the map, the minimap or in the world.
 */
@Mixin(value = WaypointManagerImpl.class, remap = false)
public abstract class WaypointManagerImplMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void valoris$noWaypoints(WaypointImpl waypoint, CallbackInfo ci) {
        ci.cancel();
    }
}
