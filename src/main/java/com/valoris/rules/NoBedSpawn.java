package com.valoris.rules;

import com.valoris.valoris2;

import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/** Beds do not set the respawn point (sleeping through the night still works); commands still can. */
@EventBusSubscriber(modid = valoris2.MODID)
public final class NoBedSpawn {
    private NoBedSpawn() {}

    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.isForced() || event.getNewSpawn() == null) return;
        if (event.getEntity().level().getBlockState(event.getNewSpawn()).getBlock() instanceof BedBlock) {
            event.setCanceled(true);
        }
    }
}
