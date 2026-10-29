package com.valoris.client;

import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Anti-logout on the client: while the player is in combat, the pause menu's Disconnect button is greyed out and
 * shows the seconds left ("Disconnect (42)"). The server punishes leaving in any other way.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class CombatClient {
    private static final String KEY = "menu.disconnect";
    private static long until; // epoch millis, 0 = not in combat
    private static Button disconnect;

    private CombatClient() {}

    public static void receive(long remainingMillis) {
        until = remainingMillis <= 0 ? 0 : System.currentTimeMillis() + remainingMillis;
        update();
    }

    private static long secondsLeft() {
        return until == 0 ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    private static boolean isDisconnect(Button b) {
        return b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(KEY);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        disconnect = null;
        if (!(event.getScreen() instanceof PauseScreen)) return;
        for (var child : event.getListenersList()) {
            if (child instanceof Button b && isDisconnect(b)) disconnect = b;
        }
        update();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (disconnect != null && !(Minecraft.getInstance().screen instanceof PauseScreen)) disconnect = null;
        update();
    }

    private static void update() {
        if (disconnect == null) return;
        long s = secondsLeft();
        disconnect.active = s == 0;
        disconnect.setMessage(s == 0 ? Component.translatable(KEY)
                : Component.translatable(KEY).append(" (" + s + ")"));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        until = 0;
        disconnect = null;
    }
}
