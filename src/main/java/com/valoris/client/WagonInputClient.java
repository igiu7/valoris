package com.valoris.client;

import com.valoris.compat.WagonDrive;
import com.valoris.net.WagonInputPayload;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends W/S and A/D of the player driving a wagon to the server when they change (and once a second). */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class WagonInputClient {
    private WagonInputClient() {}

    private static float lastForward, lastStrafe;
    private static int lastVehicle = -1, sinceSent;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof WagonDrive)
                || player.getVehicle().getControllingPassenger() != player) {
            lastVehicle = -1;
            return;
        }
        int id = player.getVehicle().getId();
        float forward = Math.signum(player.input.forwardImpulse);
        float strafe = Math.signum(player.input.leftImpulse);
        if (id != lastVehicle || forward != lastForward || strafe != lastStrafe || ++sinceSent >= 20) {
            PacketDistributor.sendToServer(new WagonInputPayload(id, forward, strafe));
            lastVehicle = id;
            lastForward = forward;
            lastStrafe = strafe;
            sinceSent = 0;
        }
    }
}
