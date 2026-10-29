package com.valoris.client;

import com.valoris.client.gui.RespawnScreen;
import com.valoris.net.RespawnChoicePayload;
import com.valoris.net.RespawnOptionsPayload;
import com.valoris.valoris2;

import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Replaces the vanilla death screen with the respawn choice and keeps it open until the player respawns. The same
 * choice opens on the very first join (where to begin), until the player picks.
 */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class RespawnClient {
    private RespawnClient() {}

    private static RespawnOptionsPayload options;
    private static long receivedAt;
    private static long goAt;
    private static long requestedAt;

    public static RespawnOptionsPayload options() {
        return options;
    }

    /** Seconds left for a banner, counted down on the client. */
    public static int secondsLeft(RespawnOptionsPayload.Banner b) {
        long passed = (System.currentTimeMillis() - receivedAt) / 1000L;
        return (int) Math.max(0, b.secondsLeft() - passed);
    }

    public static void receive(RespawnOptionsPayload payload) {
        options = payload;
        receivedAt = System.currentTimeMillis();
        if (Minecraft.getInstance().screen instanceof ScreenWrapper w && w.getGui() instanceof RespawnScreen screen) {
            screen.optionsArrived();
        }
    }

    public static void choose(String city) {
        PacketDistributor.sendToServer(new RespawnChoicePayload(city));
    }

    /** The server accepted the choice: respawn now (on the first join the server just moves the player). */
    public static void go() {
        Minecraft mc = Minecraft.getInstance();
        goAt = System.currentTimeMillis();
        boolean start = options != null && options.firstJoin();
        if (!start && mc.player != null) mc.player.respawn();
        mc.setScreen(null);
        options = null;
    }

    /** First join: the player (alive) chooses where to begin. */
    public static boolean starting() {
        return options != null && options.firstJoin();
    }

    private static boolean showing(Minecraft mc) {
        return mc.screen instanceof ScreenWrapper w && w.getGui() instanceof RespawnScreen;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (starting()) {
            // the very first join: keep the choice open (once the world has loaded) until the player picks
            if (!showing(mc) && !(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)) {
                new RespawnScreen().openGui();
            }
            return;
        }
        if (!mc.player.isDeadOrDying()) {
            if (showing(mc)) mc.setScreen(null);
            return;
        }
        if (mc.level.getLevelData().isHardcore()) return;
        long now = System.currentTimeMillis();
        if (now - goAt < 3000) return; // respawn on its way
        if (showing(mc)) return;
        if (mc.screen == null || mc.screen instanceof DeathScreen) {
            if (options == null && now - requestedAt > 2000) {
                requestedAt = now;
                PacketDistributor.sendToServer(new RespawnChoicePayload("?"));
            }
            new RespawnScreen().openGui();
        }
    }
}
