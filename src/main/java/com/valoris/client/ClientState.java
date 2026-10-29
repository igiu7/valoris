package com.valoris.client;

import com.valoris.client.gui.ConquestScreen;
import com.valoris.client.gui.HubScreen;
import com.valoris.client.gui.ValorisFrame;
import com.valoris.net.ConquestPayload;
import com.valoris.net.GuiState;
import com.valoris.net.OpenRequestPayload;
import com.valoris.net.StatePayload;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-side copy of the latest GUI snapshot plus the delayed refresh after GUI actions. */
public final class ClientState {
    private static GuiState state = new GuiState();
    private static int refreshIn = -1;
    private static long receivedAt = System.currentTimeMillis();

    private ClientState() {}

    public static GuiState get() {
        return state;
    }

    /** When the current snapshot arrived (research countdowns run from here). */
    public static long receivedAt() {
        return receivedAt;
    }

    public static void receive(StatePayload payload) {
        state = payload.state();
        receivedAt = System.currentTimeMillis();
        if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof com.valoris.client.gui.WarScreen war && !payload.open()) {
            war.onStateChanged();
            return;
        }
        if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof com.valoris.client.gui.TechTreeScreen tree) {
            if (!payload.open()) {
                tree.onStateChanged();
                return;
            }
        }
        if (payload.open()) {
            new HubScreen().openGui();
        } else if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper
                && wrapper.getGui() instanceof ValorisFrame screen) {
            screen.onStateChanged();
        }
    }

    public static void receiveConquest(ConquestPayload payload) {
        // replace an already open conquest menu instead of stacking another one on top of it
        if (Minecraft.getInstance().screen instanceof ScreenWrapper wrapper && wrapper.getGui() instanceof ConquestScreen old) {
            old.closeGui(false);
        }
        new ConquestScreen(payload).openGui();
    }

    /** Ask the server for fresh state shortly after a command was sent. */
    public static void scheduleRefresh() {
        refreshIn = 6;
    }

    public static void tick() {
        if (refreshIn > 0 && --refreshIn == 0) {
            refreshIn = -1;
            PacketDistributor.sendToServer(new OpenRequestPayload(false));
        }
    }
}
