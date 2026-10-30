package com.valoris.war;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.valoris.Config;
import com.valoris.data.ValorisData;
import com.valoris.realm.Realms;
import com.valoris.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Looting time, one of the conqueror's choices after a won siege (ConquestService.loot): every realm that fought
 * on the winning side may do whatever it likes on the fallen city's land for lootingMinutes (5 by default) - break
 * and place blocks, open chests, doors and every other block, use entities - as if it lived there
 * (mixin.ChunkTeamDataImplMixin asks mayLoot). Bystanders stay shut out by the city's claim protection. Then the
 * city is free again. Neither the looted city nor a looting realm can be disbanded meanwhile. Kept in memory: a
 * server restart ends the looting (the city is already free).
 */
public final class Looting {
    private Looting() {}

    private record Loot(String cityName, Set<UUID> realms, long endTick) {}

    /** city team -> its looting */
    private static final Map<UUID, Loot> ACTIVE = new ConcurrentHashMap<>();
    /** fallen city -> the realms that won its siege, until the conqueror decides */
    private static final Map<UUID, Set<UUID>> WINNERS = new ConcurrentHashMap<>();

    public static int minutes() {
        return Config.LOOTING_MINUTES.get();
    }

    /** The realms that won the siege of a fallen city: they all loot it if the conqueror chooses to. */
    public static void rememberWinners(UUID cityTeam, Set<UUID> realms) {
        WINNERS.put(cityTeam, Set.copyOf(realms));
    }

    /**
     * Starts the looting of a fallen city by the realms that won its siege (just the conqueror after a restart).
     * The winners online see "Looting Time!", the city's residents that their city is being looted.
     */
    public static void start(MinecraftServer server, UUID cityTeam, String cityName, UUID conqueror) {
        int minutes = minutes();
        Set<UUID> realms = new java.util.HashSet<>(WINNERS.getOrDefault(cityTeam, Set.of()));
        WINNERS.remove(cityTeam);
        realms.add(conqueror);
        long end = server.overworld().getGameTime() + minutes * 1200L;
        ACTIVE.put(cityTeam, new Loot(cityName, Set.copyOf(realms), end));
        ValorisData data = ValorisData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean looter = Realms.of(p).map(r -> realms.contains(r.id())).orElse(false);
            boolean resident = com.valoris.city.Roles.cityOfMember(p).map(c -> c.teamId.equals(cityTeam)).orElse(false);
            if (looter) {
                SiegeAnnouncer.title(p, SiegeAnnouncer.big("Looting Time!", ChatFormatting.GOLD),
                        SiegeAnnouncer.small("Plunder " + cityName + " for " + minutes + " minutes"),
                        net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
            } else if (resident) {
                SiegeAnnouncer.title(p, SiegeAnnouncer.big("Looting Time!", ChatFormatting.RED),
                        SiegeAnnouncer.small("The enemy plunders " + cityName + " for " + minutes + " minutes"),
                        net.minecraft.sounds.SoundEvents.RAID_HORN.value());
            }
        }
        for (UUID realm : realms) {
            Realms.message(data, realm, Msg.text("Looting time! For " + minutes + " minutes you may take whatever you want in "
                    + cityName + ": open its chests and doors, break and build.", ChatFormatting.GOLD));
        }
        Msg.toTeam(cityTeam, Msg.text("The enemy is looting " + cityName + " for " + minutes + " minutes!", ChatFormatting.RED));
    }

    /** The conqueror decided otherwise (or the conquest ended): the winners of that siege are no longer needed. */
    public static void forgetWinners(UUID cityTeam) {
        WINNERS.remove(cityTeam);
    }

    /** True if the player fights for a realm that is looting the city owning these claims. */
    public static boolean mayLoot(ServerPlayer player, UUID claimTeam) {
        Loot loot = ACTIVE.get(claimTeam);
        if (loot == null || player instanceof FakePlayer) return false;
        MinecraftServer server = player.getServer();
        if (server == null || server.overworld().getGameTime() >= loot.endTick()) return false;
        return Realms.of(player).map(r -> loot.realms().contains(r.id())).orElse(false);
    }

    /** The city is being looted, or the realm is looting a city. */
    public static boolean involves(UUID cityOrRealm) {
        if (ACTIVE.containsKey(cityOrRealm)) return true;
        for (Loot loot : ACTIVE.values()) if (loot.realms().contains(cityOrRealm)) return true;
        return false;
    }

    /** Called every second: ends the lootings whose time is up. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = server.overworld().getGameTime();
        ValorisData data = ValorisData.get(server);
        for (Map.Entry<UUID, Loot> e : List.copyOf(ACTIVE.entrySet())) {
            Loot loot = e.getValue();
            if (now < loot.endTick() && data.byTeam(e.getKey()).isPresent()) continue;
            ACTIVE.remove(e.getKey());
            for (UUID realm : loot.realms()) {
                Realms.message(data, realm, Msg.text("The looting of " + loot.cityName() + " is over.", ChatFormatting.YELLOW));
            }
            Msg.toTeam(e.getKey(), Msg.text("The looting of " + loot.cityName() + " is over. Your city is free again.",
                    ChatFormatting.YELLOW));
        }
    }
}
