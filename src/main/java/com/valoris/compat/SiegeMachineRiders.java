package com.valoris.compat;

import com.valoris.valoris2;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * People in a siege machine (battering ram, ballista, ladder seats...) can be hit: the machine's big box catches the
 * blow on the client, so a hit on the machine that the attacker aimed at one of its riders goes to that rider instead.
 * Done on the server only.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class SiegeMachineRiders {
    private SiegeMachineRiders() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker) || !Compat.isSiegeMachine(event.getTarget())) return;
        Entity machine = event.getTarget().getRootVehicle();
        Vec3 eye = attacker.getEyePosition();
        Vec3 end = eye.add(attacker.getViewVector(1.0F).scale(attacker.entityInteractionRange() + 1.0));
        LivingEntity aimed = null;
        double best = Double.MAX_VALUE;
        for (Entity e : machine.getIndirectPassengers()) {
            if (!(e instanceof LivingEntity rider) || rider == attacker || Compat.isSiegeMachine(rider) || !rider.isAlive()) continue;
            AABB box = rider.getBoundingBox().inflate(0.3);
            double dist = box.contains(eye) ? 0 : box.clip(eye, end).map(eye::distanceToSqr).orElse(Double.MAX_VALUE);
            if (dist < best) {
                best = dist;
                aimed = rider;
            }
        }
        if (aimed == null) return;
        event.setCanceled(true);
        attacker.attack(aimed);
    }
}
