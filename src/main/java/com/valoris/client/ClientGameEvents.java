package com.valoris.client;

import com.valoris.net.OpenRequestPayload;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        while (ClientModEvents.OPEN_GUI.consumeClick()) {
            PacketDistributor.sendToServer(new OpenRequestPayload(true));
        }
        ClientState.tick();
        ClientInbox.tick();
    }
}
