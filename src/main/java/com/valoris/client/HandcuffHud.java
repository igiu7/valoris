package com.valoris.client;

import com.valoris.net.HandcuffProgressPayload;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * A blue bar in the middle of the screen while a handcuffing during a siege goes on (war.SiegeHandcuffs), for the
 * captor and the downed player. The server sends the progress now and then; it is counted on between.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class HandcuffHud {
    private HandcuffHud() {}

    private static String label = "";
    private static int done, total;

    public static void receive(HandcuffProgressPayload payload) {
        label = payload.label();
        done = payload.done();
        total = payload.total();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) total = 0;
        else if (total > 0 && done < total) done++;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (total <= 0 || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 34; // under the gate / siege machine bar (GateHud)
        int seconds = Math.max(0, (total - done + 19) / 20);
        g.drawCenteredString(mc.font, label + " " + seconds + "s", x, y, 0x55AAFF);
        int w = 60, left = x - w / 2, top = y + 11;
        g.fill(left - 1, top - 1, left + w + 1, top + 5, 0xFF000000);
        g.fill(left, top, left + w, top + 4, 0xFF10203A);
        g.fill(left, top, left + Math.round(w * Math.min(1, done / (float) total)), top + 4, 0xFF2070E0);
    }
}
