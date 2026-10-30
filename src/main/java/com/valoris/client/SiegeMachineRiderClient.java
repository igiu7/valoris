package com.valoris.client;

import com.valoris.compat.Compat;
import com.valoris.compat.ShipRotation;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The local player riding a siege machine on a Create Aeronautics ship turns with the ship every frame (see
 * ShipRotation.riderFrame), so the machine - which aims where its rider looks - keeps facing the same way on the
 * deck, and the view turns smoothly instead of jumping each tick.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class SiegeMachineRiderClient {
    private SiegeMachineRiderClient() {}

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (!Compat.SABLE || !Compat.SIEGE_MACHINES) return;
        Minecraft mc = Minecraft.getInstance();
        Entity vehicle = mc.player == null || mc.level == null || mc.isPaused() ? null : mc.player.getVehicle();
        ShipRotation.riderFrame(mc.player, Compat.isSiegeMachine(vehicle) ? vehicle : null);
    }
}
