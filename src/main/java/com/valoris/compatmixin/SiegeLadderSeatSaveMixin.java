package com.valoris.compatmixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Siege ladder seats are never saved with the chunk: the ladder makes new seats whenever it is loaded, so a saved
 * seat would come back as a stray duplicate. (Siege Machines got the same result by removing the seats whenever the
 * ladder unloaded, which crashed the server - see SiegeLadderSeatsMixin.)
 */
@Mixin(targets = "ru.magistu.siegemachines.entity.machine.LadderSeat")
public abstract class SiegeLadderSeatSaveMixin {
    public boolean shouldBeSaved() {
        return false;
    }
}
