package com.valoris.war;

import java.util.Optional;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.War;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.valoris2;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Counts the casualties of every war: a player killed by a player of the other side. */
@EventBusSubscriber(modid = valoris2.MODID)
public final class WarStatsEvents {
    private WarStatsEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim) return;
        Optional<Realm> k = Realms.of(killer), v = Realms.of(victim);
        if (k.isEmpty() || v.isEmpty()) return;
        ValorisData data = ValorisData.get(victim.getServer());
        for (War w : data.warsBetween(k.get().id(), v.get().id())) {
            w.stats(k.get().id())[0]++;
            w.stats(v.get().id())[1]++;
            w.sideKills[w.sideOf(k.get().id())]++;
            data.touch();
        }
    }
}
