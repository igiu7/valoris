package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.valoris.compat.WagonDrive;

import net.favouriteless.trotting_wagons.common.entities.base.AbstractWagon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Trotting Wagons steered like a horse in Horse Combat Controls instead of with the whip: W/S raise or lower a
 * throttle (-25%..100%, kept when the key is released), A/D turn up to 10 degrees a tick, less the faster the
 * wagon goes. The wagon's own acceleration still limits how fast the real speed follows the throttle.
 */
@Mixin(AbstractWagon.class)
public abstract class AbstractWagonMixin extends Entity implements WagonDrive {
    @Shadow @Final public double speed;
    @Shadow @Final private double acceleration;
    @Shadow @Final private float turnRate;
    @Shadow private int speedLevel;
    @Shadow private double currentSpeed;

    @Unique private float valoris$forward;
    @Unique private float valoris$strafe;
    @Unique private double valoris$throttle;

    protected AbstractWagonMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public void valoris$setInput(float forward, float strafe) {
        valoris$forward = forward;
        valoris$strafe = strafe;
    }

    /** Top speed: the whip's highest level. */
    @Unique
    private double valoris$topSpeed() {
        return speed * (AbstractWagon.SPEED_LEVELS - 1);
    }

    // moveTick drops speedLevel to 0 when there is no player driving or a horse is missing; set it to 1 first so
    // that drop tells us the wagon cannot be driven this tick
    @Inject(method = "moveTick", at = @At("HEAD"))
    private void valoris$markDrivable(CallbackInfo ci) {
        speedLevel = 1;
    }

    @Inject(method = "handleAcceleration", at = @At("HEAD"), cancellable = true)
    private void valoris$accelerate(CallbackInfo ci) {
        ci.cancel();
        if (speedLevel == 0) {
            valoris$forward = 0;
            valoris$strafe = 0;
            valoris$throttle = 0;
        } else {
            // Horse Combat Controls: 5% per tick, 100% forward, 25% back, a small throttle dies away
            if (valoris$forward > 0) valoris$throttle = Math.min(1.0, valoris$throttle + 0.05);
            else if (valoris$forward < 0) valoris$throttle = Math.max(-0.25, valoris$throttle - 0.05);
            if (Math.abs(valoris$throttle) < 0.05) valoris$throttle *= 0.95;
        }
        double top = valoris$topSpeed();
        if (!(top > 0) || !Double.isFinite(top)) { // speed 0 in Trotting Wagons' config: the wagon does not move
            currentSpeed = 0;
            return;
        }
        // dirt paths are roads: faster there (movement.PathSpeed)
        double target = top * valoris$throttle * com.valoris.movement.PathSpeed.multiplier(this);
        double diff = target - currentSpeed;
        // acceleration 0 in the config = instant (avoid speed / 0)
        double accel = acceleration > 0 ? speed / (acceleration * 20.0) : Math.abs(diff);
        currentSpeed += Math.signum(diff) * Math.min(Math.abs(accel), Math.abs(diff));
        if (!Double.isFinite(currentSpeed)) currentSpeed = 0;
    }

    @ModifyVariable(method = "handleSteering", at = @At("STORE"), ordinal = 1)
    private float valoris$steer(float steer) {
        if (speedLevel == 0 || valoris$strafe == 0) return 0;
        double top = valoris$topSpeed();
        if (!(top > 0)) return 0;
        // Horse Combat Controls: atan(0.05 / speed%) degrees, at most 10 (times its rotation multiplier);
        // also scaled by the wagon's own turn rate (Trotting Wagons' config, default 2 = unchanged)
        double percent = Math.min(1.0, Math.abs(currentSpeed) / top);
        double multi = WagonDrive.rotMultiplier() * turnRate / 2.0;
        double delta = Math.min(Math.toDegrees(Math.atan(0.05 / percent)) * multi, 10.0 * multi);
        // a NaN angle would put the horses at NaN coordinates
        if (!Double.isFinite(delta) || delta <= 0) return 0;
        return (float) (valoris$strafe > 0 ? -delta : delta);
    }
}
