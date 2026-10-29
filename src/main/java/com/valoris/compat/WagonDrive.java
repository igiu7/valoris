package com.valoris.compat;

import java.lang.reflect.Field;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Trotting Wagons driven like a horse with Horse Combat Controls: W/S build up or take off speed (it stays when
 * the key is released), A/D turn, quicker when slow. Implemented on the wagon by compatmixin.AbstractWagonMixin;
 * the driver's keys come from WagonInputPayload. No whip needed.
 */
public interface WagonDrive {
    /** The driver's keys: forward (+1 W, -1 S) and strafe (+1 A, -1 D). */
    void valoris$setInput(float forward, float strafe);

    /** Horse Combat Controls' rotation multiplier (server config "rot_mutli"), 1 when the mod is missing. */
    static double rotMultiplier() {
        try {
            if (Holder.rotMulti == null) {
                Field f = Class.forName("github.pitbox46.horsecombatcontrols.Config").getField("ROT_MULTI");
                Holder.rotMulti = (ModConfigSpec.DoubleValue) f.get(null);
            }
            return Holder.rotMulti.get();
        } catch (Throwable t) {
            return 1.0;
        }
    }

    final class Holder {
        private static ModConfigSpec.DoubleValue rotMulti;

        private Holder() {}
    }
}
