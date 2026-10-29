package com.valoris.client;

import com.valoris.mobs.Breeding;
import com.valoris.net.AnimalInfoPayload;
import com.valoris.net.AnimalInfoRequestPayload;
import com.valoris.valoris2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Under the crosshair: whether the animal you look at can breed, or how long until it can. */
@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class AnimalHud {
    private AnimalHud() {}

    private static int targetId = -1;
    private static int sinceRequest;
    private static AnimalInfoPayload info;
    private static long infoAt;

    public static void receive(AnimalInfoPayload payload) {
        info = payload;
        infoAt = System.currentTimeMillis();
    }

    private static Animal target() {
        Minecraft mc = Minecraft.getInstance();
        // wolves do not breed, so they get no label
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Animal a
                && !(a instanceof net.minecraft.world.entity.animal.Wolf)) return a;
        return null;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) return;
        Animal a = target();
        if (a == null) {
            targetId = -1;
            return;
        }
        if (a.getId() != targetId || ++sinceRequest >= 20) {
            targetId = a.getId();
            sinceRequest = 0;
            PacketDistributor.sendToServer(new AnimalInfoRequestPayload(targetId));
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui || info == null) return;
        Animal a = target();
        if (a == null || a.getId() != info.entityId()) return;
        long elapsed = (System.currentTimeMillis() - infoAt) / 1000L;
        long left = Math.max(0, info.seconds() - elapsed);
        String text;
        int color;
        switch (info.state()) {
            case Breeding.READY -> { text = "Ready to breed"; color = 0x7CFC7C; }
            case Breeding.IN_LOVE -> { text = "Looking for a mate"; color = 0xFF8FB8; }
            case Breeding.BABY -> { text = "Young - grows up in ~" + AnimalInfoPayload.time(left); color = 0xBFBFBF; }
            default -> {
                if (left <= 0) { text = "Ready to breed"; color = 0x7CFC7C; }
                else { text = "Can breed again in " + AnimalInfoPayload.time(left); color = 0xFFC04D; }
            }
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 + 12;
        g.drawCenteredString(mc.font, text, x, y, color);
        if (info.stewSeconds() >= 0) {
            long stew = Math.max(0, info.stewSeconds() - elapsed);
            g.drawCenteredString(mc.font, stew <= 0 ? "Stew ready" : "Stew in " + AnimalInfoPayload.time(stew),
                    x, y + 11, stew <= 0 ? 0x7CFC7C : 0xFFC04D);
        }
    }
}
