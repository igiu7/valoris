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

/** Name tags above the head in the colour of the player's realm (sent by chat.NameColors); realms for RealmTabList. */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class NameColorsClient {
    private NameColorsClient() {}

    private static Map<UUID, Integer> colors = Map.of();
    private static Map<UUID, String> realms = Map.of();
    private static boolean received;

    /** Realm colour (RGB) of a player, or null. */
    public static Integer color(UUID player) { return colors.get(player); }
    /** Title of the realm (kingdom or independent city) of a player, or null. */
    public static String realm(UUID player) { return realms.get(player); }
    /** True once the server sent the colours (a Valoris server). */
    public static boolean received() { return received; }

    /** Forgets everything (leaving the server). */
    public static void reset() {
        colors = Map.of();
        realms = Map.of();
        received = false;
    }

    public static void receive(Map<UUID, Integer> receivedColors, Map<UUID, String> receivedRealms) {
        colors = Map.copyOf(receivedColors);
        realms = Map.copyOf(receivedRealms);
        received = true;
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
