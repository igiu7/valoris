package com.valoris.compatmixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.valoris.compat.DeferredRemoval;

import net.minecraft.world.entity.Entity;

/**
 * Siege Machines' siege ladder removes its seat entities whenever it is removed - also when its chunk merely
 * unloads. Vanilla unloads a chunk by going through the entities of each chunk section, and removing a seat from that
 * section in the middle of it throws ConcurrentModificationException: the server crashes ("Exception in server tick
 * loop" in PersistentEntitySectionManager.updateChunkStatus). Here, when the ladder is only unloaded, its seats are
 * removed at the end of the server tick instead; when it is destroyed nothing changes. The seats are never saved
 * (SiegeLadderSeatSaveMixin), as before: the ladder makes new ones whenever it is loaded.
 */
@Mixin(targets = "ru.magistu.siegemachines.entity.machine.SiegeLadder")
public abstract class SiegeLadderSeatsMixin {
    @Redirect(method = "remove", at = @At(value = "INVOKE",
            target = "Lru/magistu/siegemachines/entity/machine/LadderSeat;discard()V"))
    private void valoris$discardSeatSafely(@Coerce Entity seat, Entity.RemovalReason reason) {
        if (reason == null || reason.shouldDestroy()) seat.discard();
        else DeferredRemoval.later(seat::discard);
    }

    @Redirect(method = "onRemovedFromLevel", at = @At(value = "INVOKE",
            target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void valoris$seatsLater(List seats, Consumer action) {
        Entity.RemovalReason reason = ((Entity) (Object) this).getRemovalReason();
        if (reason == null || reason.shouldDestroy()) {
            seats.forEach(action);
        } else {
            List copy = new ArrayList(seats);
            DeferredRemoval.later(() -> copy.forEach(action));
        }
    }
}
