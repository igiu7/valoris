package com.valoris.client;

import java.util.Map;
import java.util.UUID;

import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Name tags above the head in the colour of the player's realm (sent by chat.NameColors). */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class NameColorsClient {
    private NameColorsClient() {}

    private static Map<UUID, Integer> colors = Map.of();

    public static void receive(Map<UUID, Integer> received) {
        colors = Map.copyOf(received);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) for (Player p : mc.level.players()) p.refreshDisplayName();
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide()) return;
        Integer rgb = colors.get(event.getEntity().getUUID());
        if (rgb == null) return;
        event.setDisplayname(event.getDisplayname().copy().withStyle(s -> s.withColor(TextColor.fromRgb(rgb))));
    }
}
