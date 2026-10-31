package com.valoris.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.valoris.net.NameColorsPayload;
import com.valoris.valoris2;

import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Names in the realm's colour (ChatService.realmColor) in the tab list and above the head. Every few seconds the
 * colours of the online players are checked; when one changed, the tab list names are refreshed and every client
 * gets the new colours (client.NameColorsClient colours the name tags with them).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class NameColors {
    private NameColors() {}

    private static final int INTERVAL_TICKS = 60;
    private static Map<UUID, Integer> sent = Map.of();
    private static int ticks;

    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        int rgb = ChatService.realmColor(p);
        if (rgb < 0) return;
        event.setDisplayName(net.minecraft.network.chat.Component.literal(p.getGameProfile().getName())
                .withStyle(s -> s.withColor(TextColor.fromRgb(rgb))));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new NameColorsPayload(sent));
        ticks = INTERVAL_TICKS; // update everybody (the new player's colour) on the next tick
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < INTERVAL_TICKS) return;
        ticks = 0;
        MinecraftServer server = event.getServer();
        Map<UUID, Integer> now = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int rgb = ChatService.realmColor(p);
            if (rgb >= 0) now.put(p.getUUID(), rgb);
        }
        if (now.equals(sent)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!Objects.equals(now.get(p.getUUID()), sent.get(p.getUUID()))) {
                p.refreshTabListName();
                p.refreshDisplayName();
            }
        }
        sent = Map.copyOf(now);
        PacketDistributor.sendToAllPlayers(new NameColorsPayload(sent));
    }
}
