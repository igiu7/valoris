package com.valoris.war;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.valoris.Config;
import com.valoris.net.HandcuffProgressPayload;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Handcuffing during an active siege is slow: the captor clicks a downed player once, then has to stay next to him
 * with the handcuffs in hand for Config.SIEGE_HANDCUFF_SECONDS while a blue bar fills on both screens
 * (client.HandcuffHud). Walking away, putting the handcuffs away, dying, leaving or the downed player being revived
 * (or dying) stops it. When the time is up the handcuffs go on the usual way (compatmixin.CuffedRestrainMixin).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class SiegeHandcuffs {
    private SiegeHandcuffs() {}

    private static final double RANGE_SQR = 4.0 * 4.0;
    private static final int SYNC_TICKS = 10;

    private static final class Cuffing {
        final UUID target;
        final Item item;
        final BooleanSupplier downed;
        final Runnable finish;
        int done;

        Cuffing(UUID target, Item item, BooleanSupplier downed, Runnable finish) {
            this.target = target;
            this.item = item;
            this.downed = downed;
            this.finish = finish;
        }
    }

    /** captor -> handcuffing in progress */
    private static final Map<UUID, Cuffing> ACTIVE = new HashMap<>();
    /** captors whose time is up: their handcuffs go on now */
    private static final Set<UUID> FINISHING = new HashSet<>();

    public static boolean finishing(ServerPlayer captor) {
        return FINISHING.contains(captor.getUUID());
    }

    private static int total() {
        return Config.SIEGE_HANDCUFF_SECONDS.get() * 20;
    }

    /** Starts handcuffing target (a click on him); a click while it is already going on changes nothing. */
    public static void start(ServerPlayer captor, ServerPlayer target, Item item, BooleanSupplier downed, Runnable finish) {
        Cuffing current = ACTIVE.get(captor.getUUID());
        if (current != null && current.target.equals(target.getUUID())) return;
        if (current != null) stop(captor, current, null);
        for (Cuffing c : ACTIVE.values()) {
            if (c.target.equals(target.getUUID())) {
                captor.displayClientMessage(Component.literal(target.getGameProfile().getName()
                        + " is already being handcuffed.").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        Cuffing c = new Cuffing(target.getUUID(), item, downed, finish);
        ACTIVE.put(captor.getUUID(), c);
        sync(captor, target, c);
        captor.displayClientMessage(Component.literal("Handcuffing " + target.getGameProfile().getName()
                + " - stay close and keep the handcuffs in hand.").withStyle(ChatFormatting.AQUA), true);
    }

    private static void sync(ServerPlayer captor, ServerPlayer target, Cuffing c) {
        PacketDistributor.sendToPlayer(captor, new HandcuffProgressPayload(
                "Handcuffing " + target.getGameProfile().getName(), c.done, total()));
        PacketDistributor.sendToPlayer(target, new HandcuffProgressPayload(
                "Handcuffed by " + captor.getGameProfile().getName(), c.done, total()));
    }

    /** Ends a handcuffing (why: told to the captor, null for nothing) and clears both bars. */
    private static void stop(ServerPlayer captor, Cuffing c, String why) {
        ACTIVE.remove(captor.getUUID());
        PacketDistributor.sendToPlayer(captor, HandcuffProgressPayload.NONE);
        ServerPlayer target = captor.getServer().getPlayerList().getPlayer(c.target);
        if (target != null) PacketDistributor.sendToPlayer(target, HandcuffProgressPayload.NONE);
        if (why != null) captor.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true);
    }

    private static boolean holding(ServerPlayer captor, Item item) {
        return captor.getMainHandItem().is(item) || captor.getOffhandItem().is(item);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        var players = event.getServer().getPlayerList();
        Iterator<Map.Entry<UUID, Cuffing>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cuffing> e = it.next();
            Cuffing c = e.getValue();
            ServerPlayer captor = players.getPlayer(e.getKey());
            ServerPlayer target = players.getPlayer(c.target);
            String why = null;
            if (captor == null || !captor.isAlive()) why = "";
            else if (target == null || !target.isAlive() || !c.downed.getAsBoolean()) why = "Handcuffing stopped.";
            else if (target.level() != captor.level() || target.distanceToSqr(captor) > RANGE_SQR) why = "Handcuffing stopped: you moved away.";
            else if (!holding(captor, c.item)) why = "Handcuffing stopped: keep the handcuffs in hand.";
            if (why != null) {
                it.remove();
                if (target != null) PacketDistributor.sendToPlayer(target, HandcuffProgressPayload.NONE);
                if (captor != null) {
                    PacketDistributor.sendToPlayer(captor, HandcuffProgressPayload.NONE);
                    if (!why.isEmpty()) captor.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true);
                }
                continue;
            }
            if (++c.done < total()) {
                if (c.done % SYNC_TICKS == 0) sync(captor, target, c);
                continue;
            }
            it.remove();
            PacketDistributor.sendToPlayer(captor, HandcuffProgressPayload.NONE);
            PacketDistributor.sendToPlayer(target, HandcuffProgressPayload.NONE);
            FINISHING.add(captor.getUUID());
            try {
                c.finish.run();
            } finally {
                FINISHING.remove(captor.getUUID());
            }
        }
    }
}
