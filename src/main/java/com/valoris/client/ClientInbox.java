package com.valoris.client;

import java.util.ArrayList;
import java.util.List;

import com.valoris.client.gui.InboxScreen;
import com.valoris.net.InboxPayload;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;

/** Client copy of the requests the player can answer, and the pop-up of the requests window. */
public final class ClientInbox {
    private static List<InboxPayload.Entry> entries = new ArrayList<>();
    private static long receivedAt;
    private static boolean wantPopup;

    private ClientInbox() {}

    public static List<InboxPayload.Entry> entries() {
        return entries;
    }

    /** Seconds left of an entry, counted down since the list arrived (-1 = no limit). */
    public static int secondsLeft(InboxPayload.Entry e) {
        if (e.seconds() < 0) return -1;
        return (int) Math.max(0, e.seconds() - (System.currentTimeMillis() - receivedAt) / 1000);
    }

    public static void remove(String id) {
        entries.removeIf(e -> e.id().equals(id));
    }

    public static void receive(InboxPayload payload) {
        entries = new ArrayList<>(payload.entries());
        receivedAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ScreenWrapper w && w.getGui() instanceof InboxScreen inbox) {
            inbox.refreshWidgets();
            return;
        }
        if (payload.open()) {
            new InboxScreen().openGui();
        } else if (payload.popup() && !entries.isEmpty()) {
            wantPopup = true;
        }
    }

    /** Opens the window for a new request as soon as the player is not in another screen. */
    public static void tick() {
        if (!wantPopup) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        wantPopup = false;
        entries.removeIf(e -> secondsLeft(e) == 0);
        if (!entries.isEmpty()) new InboxScreen().openGui();
    }
}
