package com.valoris.compat;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.valoris.valoris2;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Work that must not run while the game is going through a list of entities (removing an entity from a chunk
 * section there throws ConcurrentModificationException and crashes the server): queued and run at the end of the
 * server tick. Used for the seats of Siege Machines ladders (SiegeLadderSeatsMixin).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class DeferredRemoval {
    private DeferredRemoval() {}

    private static final Queue<Runnable> LATER = new ConcurrentLinkedQueue<>();

    public static void later(Runnable task) {
        LATER.add(task);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Runnable task;
        while ((task = LATER.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                valoris2.LOGGER.warn("Deferred entity removal failed", e);
            }
        }
    }
}
