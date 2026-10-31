package com.valoris.rules;

import com.valoris.valoris2;

import net.minecraft.world.entity.ExperienceOrb;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Vanilla experience is not used (the experience bar shows the soil tier, see client.TierBar), so no experience
 * orb ever enters a world: not from mobs, ores, furnaces, breeding, fishing or bottles o' enchanting. Orbs saved in
 * old chunks are dropped when the chunk loads.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class NoExperienceOrbs {
    private NoExperienceOrbs() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ExperienceOrb) event.setCanceled(true);
    }
}
