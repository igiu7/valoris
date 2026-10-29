package com.valoris.heal;

import com.valoris.Config;
import com.valoris.valoris2;

import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Natural regeneration is switched off on every server start (config: healing.disableNaturalRegeneration). */
@EventBusSubscriber(modid = valoris2.MODID)
public final class RegenRule {
    private RegenRule() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!Config.DISABLE_NATURAL_REGENERATION.get()) return;
        event.getServer().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, event.getServer());
        valoris2.LOGGER.info("Natural regeneration disabled (healing items only)");
    }
}
