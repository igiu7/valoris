package com.valoris.moderation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.valoris.mixin.ChunkMapAccessor;
import com.valoris.mixin.TrackedEntityAccessor;
import com.valoris.valoris2;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * /vx vanish: the staff member disappears for everybody except the staff - from the world (never sent to other
 * players, see TrackedEntityMixin), the tab list and private messages, with a fake "left the game" message (and a fake
 * "joined" one when they come back). Vanished staff cannot be hurt and mobs do not target them. Vanish ends on logout;
 * leaving while vanished is not announced (PlayerLeaveMessageMixin).
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Vanish {
    private static final Set<UUID> VANISHED = new HashSet<>();

    private Vanish() {}

    public static boolean isVanished(ServerPlayer p) {
        return VANISHED.contains(p.getUUID());
    }

    /** True when the viewer may not see the target: the target is vanished and the viewer is not staff. */
    public static boolean hiddenFrom(ServerPlayer target, ServerPlayer viewer) {
        return target != viewer && VANISHED.contains(target.getUUID()) && !ModerationService.isStaff(viewer);
    }

    public static void toStaff(PlayerList list, Component message) {
        for (ServerPlayer p : list.getPlayers()) if (ModerationService.isStaff(p)) p.sendSystemMessage(message);
    }

    /** Re-checks who sees the player in their level (entity sent or taken away). */
    private static void refreshTracking(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).valoris$entityMap().get(p.getId());
        if (tracked instanceof TrackedEntityAccessor t) t.valoris$updatePlayers(level.players());
    }

    /** Returns true if the player is vanished now. */
    public static boolean toggle(ServerPlayer staff) {
        PlayerList list = staff.getServer().getPlayerList();
        String name = staff.getGameProfile().getName();
        boolean vanish = !VANISHED.contains(staff.getUUID());
        if (vanish) {
            VANISHED.add(staff.getUUID());
            refreshTracking(staff); // the entity first, then the tab list entry
            var remove = new ClientboundPlayerInfoRemovePacket(List.of(staff.getUUID()));
            Component left = Component.translatable("multiplayer.player.left", staff.getDisplayName()).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer p : list.getPlayers()) {
                if (hiddenFrom(staff, p)) {
                    p.connection.send(remove);
                    p.sendSystemMessage(left);
                }
            }
        } else {
            VANISHED.remove(staff.getUUID());
            // the tab list entry must arrive before the player entity, or the client cannot show them
            var info = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(staff));
            Component joined = Component.translatable("multiplayer.player.joined", staff.getDisplayName()).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer p : list.getPlayers()) {
                if (p != staff && !ModerationService.isStaff(p)) {
                    p.connection.send(info);
                    p.sendSystemMessage(joined);
                }
            }
            refreshTracking(staff);
        }
        ModerationService.toStaff(staff.getServer(), Component.literal(name + (vanish ? " vanished." : " is visible again."))
                .withStyle(ChatFormatting.AQUA));
        return vanish;
    }

    // ------------------------------------------------------------------ keeping it up

    /** Players who join do not get vanished staff in their tab list. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined) || VANISHED.isEmpty()) return;
        List<UUID> hidden = new java.util.ArrayList<>();
        for (ServerPlayer p : joined.getServer().getPlayerList().getPlayers()) {
            if (hiddenFrom(p, joined)) hidden.add(p.getUUID());
        }
        if (!hidden.isEmpty()) joined.connection.send(new ClientboundPlayerInfoRemovePacket(hidden));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VANISHED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isVanished(p)) event.setCanceled(true);
    }

    /** Mobs do not notice vanished staff. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer p && isVanished(p)) event.setCanceled(true);
    }
}
