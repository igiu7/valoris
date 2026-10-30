package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.compat.Compat;
import com.valoris.compat.ShipRotation;

import net.minecraft.world.entity.Mob;

/**
 * Siege Machines on a Create Aeronautics ship turn with it, manned or not. Sable moves an entity standing on a ship
 * with it but never turns it; so on the server, before the machine's own turning (aiStep), the hull is turned by the
 * ship's turn of the last tick (only when unmanned: a manned machine is turned by its rider's client) - yaw, body and head together, the previous values too so the rendering stays smooth
 * - and so is the direction it was turning to (its yaw "destination"), or it would swing back. The turret yaw is
 * relative to the hull and follows by itself. A manned machine aims where its rider looks; the rider is turned with
 * the ship on their own client (ShipRotation.riderFrame).
 */
@Mixin(targets = "ru.magistu.siegemachines.entity.machine.Machine")
public abstract class SiegeMachineShipMixin {
    @Shadow(remap = false)
    public abstract float getYawDest();

    @Shadow(remap = false)
    public abstract void setYawDest(float yaw);

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void valoris$turnWithShip(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!Compat.SABLE || self.level().isClientSide || !self.isAlive()) return;
        // a manned machine is moved and turned by its rider's client, which sends its facing back every tick;
        // there it turns with the ship (ShipRotation.riderFrame), also while reloading - turning it here too
        // would only fight that
        if (self.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player) return;
        float d = ShipRotation.machineTickDelta(self);
        if (d == 0) return;
        ShipRotation.turn(self, d);
        float dest = getYawDest() + d;
        if (Float.isFinite(dest)) setYawDest(dest);
    }
}
