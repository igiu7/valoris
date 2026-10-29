package com.valoris.war;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.Siege;
import com.valoris.data.ValorisData.SiegePhase;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

/**
 * What the two sides see: big titles in the middle of the screen at the turning points of a siege, and a boss bar
 * with the countdown while it lasts. Shown to everyone taking part (both realms and the city's residents).
 */
public final class SiegeAnnouncer {
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private SiegeAnnouncer() {}

    public static List<ServerPlayer> participants(MinecraftServer server, Siege s) {
        return server.getPlayerList().getPlayers().stream().filter(p -> SiegeService.isParticipant(p, s)).toList();
    }

    public static void title(ServerPlayer p, Component title, Component subtitle, SoundEvent sound) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 25));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        if (sound != null) p.playNotifySound(sound, SoundSource.MASTER, 1.0F, 1.0F);
    }

    private static Component big(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color, ChatFormatting.BOLD);
    }

    private static Component small(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    /** The siege was declared: the preparation countdown starts. */
    public static void declared(MinecraftServer server, Siege s, String attackerTitle, long prepSeconds) {
        String when = SiegeService.formatTime(prepSeconds);
        for (ServerPlayer p : participants(server, s)) {
            boolean attacker = SiegeService.isAttacker(p, s);
            title(p, big("SIEGE DECLARED", ChatFormatting.RED), small(attacker
                    ? "Gather your army: the siege of " + s.cityName + " begins in " + when
                    : "The " + attackerTitle + " will attack " + s.cityName + " in " + when), SoundEvents.RAID_HORN.value());
        }
    }

    /** A realm joined a running siege: its members get the call to arms. */
    public static void joined(MinecraftServer server, Siege s, com.valoris.realm.Realms.Realm realm, String leader) {
        for (ServerPlayer p : participants(server, s)) {
            boolean ours = com.valoris.realm.Realms.of(p).map(r -> r.id().equals(realm.id())).orElse(false);
            if (ours) {
                title(p, big("TO ARMS", ChatFormatting.RED), small("You join the " + leader + " in the siege of " + s.cityName),
                        SoundEvents.RAID_HORN.value());
            } else {
                p.displayClientMessage(Component.literal("The " + realm.title() + " joins the siege of " + s.cityName)
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** The active phase began. */
    public static void began(MinecraftServer server, Siege s, long durationSeconds) {
        String time = SiegeService.formatTime(durationSeconds);
        for (ServerPlayer p : participants(server, s)) {
            boolean attacker = SiegeService.isAttacker(p, s);
            title(p, big("THE SIEGE HAS BEGUN", ChatFormatting.DARK_RED), small(attacker
                    ? "Destroy the city banner of " + s.cityName + " within " + time
                    : "Defend the city banner of " + s.cityName + " for " + time), SoundEvents.RAID_HORN.value());
        }
    }

    public static void captured(MinecraftServer server, Siege s, List<ServerPlayer> before) {
        for (ServerPlayer p : before) {
            boolean attacker = SiegeService.isAttacker(p, s);
            title(p, attacker ? big("VICTORY", ChatFormatting.GREEN) : big("DEFEAT", ChatFormatting.RED),
                    small(s.cityName + " has fallen"),
                    attacker ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_DEATH);
        }
    }

    public static void defended(MinecraftServer server, Siege s) {
        for (ServerPlayer p : participants(server, s)) {
            boolean attacker = SiegeService.isAttacker(p, s);
            title(p, attacker ? big("DEFEAT", ChatFormatting.RED) : big("VICTORY", ChatFormatting.GREEN),
                    small(s.cityName + " held out"),
                    attacker ? SoundEvents.WITHER_DEATH : SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
        }
    }

    public static void cancelled(MinecraftServer server, Siege s, String reason) {
        for (ServerPlayer p : participants(server, s)) {
            title(p, big("SIEGE CALLED OFF", ChatFormatting.YELLOW), small(reason), null);
        }
    }

    // ------------------------------------------------------------------ boss bar

    /** Called every second for every siege. */
    public static void updateBar(MinecraftServer server, Siege s, long now) {
        ServerBossEvent bar = BARS.computeIfAbsent(s.id, id -> new ServerBossEvent(Component.empty(),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
        boolean prep = s.phase == SiegePhase.PREPARATION;
        long total = Math.max(1, s.endTick - s.startTick);
        long left = Math.max(0, (prep ? s.startTick : s.endTick) - now);
        bar.setColor(prep ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        bar.setName(Component.literal(prep ? "Siege of " + s.cityName + " begins in " + clock(left / 20)
                : "Siege of " + s.cityName + "  -  " + clock(left / 20) + " left").withStyle(ChatFormatting.BOLD));
        bar.setProgress(prep ? 1F : Math.max(0F, Math.min(1F, left / (float) total)));

        Set<ServerPlayer> want = new HashSet<>(participants(server, s));
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!want.contains(p)) bar.removePlayer(p);
        for (ServerPlayer p : want) bar.addPlayer(p);
    }

    private static String clock(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static void removeBar(UUID siegeId) {
        ServerBossEvent bar = BARS.remove(siegeId);
        if (bar != null) bar.removeAllPlayers();
    }

    /** Bars of sieges that no longer exist (removed elsewhere). */
    public static void cleanBars(ValorisData data) {
        BARS.keySet().removeIf(id -> {
            boolean gone = data.sieges().stream().noneMatch(s -> s.id.equals(id));
            if (gone) BARS.get(id).removeAllPlayers();
            return gone;
        });
    }
}
