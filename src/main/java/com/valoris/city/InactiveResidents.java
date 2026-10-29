package com.valoris.city;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.valoris.Config;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.kingdom.KingdomService;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Residents who have not joined the server for inactiveResidentKickHours are removed from their city (and so from
 * its kingdom). City rulers and kings stay. The last time a player was on the server is the last change of their
 * save file (world/playerdata/<uuid>.dat), which the server writes only while they are online; time the server
 * was off is not counted. Checked a minute after the start and then every 10 minutes.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class InactiveResidents {
    private InactiveResidents() {}

    private static final int FIRST_CHECK = 20 * 60;
    private static final int EVERY = 20 * 60 * 10;

    // Time the server was off does not count as time away (nobody could join then). downtime.txt keeps
    // "alive <millis>" (rewritten every minute while the server runs) and one "<start> <end>" line per stop.
    private static final List<long[]> DOWNTIME = new ArrayList<>();

    private static Path downtimeFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("valoris_data").resolve("downtime.txt");
    }

    private static void loadDowntime(MinecraftServer server) {
        DOWNTIME.clear();
        long lastAlive = 0;
        try {
            Path f = downtimeFile(server);
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f)) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 2 && p[0].equals("alive")) lastAlive = Long.parseLong(p[1]);
                    else if (p.length == 2) DOWNTIME.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])});
                }
            }
        } catch (Exception e) {
            valoris2.LOGGER.warn("Could not read downtime.txt", e);
        }
        long now = System.currentTimeMillis();
        if (lastAlive > 0 && now - lastAlive > 120_000L) DOWNTIME.add(new long[]{lastAlive, now});
        // forget stops older than any kick limit could reach
        long keep = now - Math.max(1, Config.INACTIVE_KICK_HOURS.get()) * 3_600_000L * 4;
        DOWNTIME.removeIf(d -> d[1] < keep);
        saveDowntime(server, now);
    }

    private static void saveDowntime(MinecraftServer server, long now) {
        StringBuilder sb = new StringBuilder("alive ").append(now).append('\n');
        for (long[] d : DOWNTIME) sb.append(d[0]).append(' ').append(d[1]).append('\n');
        try {
            Path f = downtimeFile(server);
            Files.createDirectories(f.getParent());
            Files.writeString(f, sb.toString());
        } catch (Exception e) {
            valoris2.LOGGER.warn("Could not write downtime.txt", e);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        loadDowntime(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 1200 == 0) saveDowntime(server, System.currentTimeMillis());
        if (t == FIRST_CHECK || (t > FIRST_CHECK && (t - FIRST_CHECK) % EVERY == 0)) check(server);
    }

    /** Milliseconds the server was running since the player was last on it, or -1 if unknown (never saved). */
    private static long awayMillis(MinecraftServer server, UUID id) {
        if (server.getPlayerList().getPlayer(id) != null) return 0;
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        try {
            if (!Files.exists(file)) return -1;
            long seen = Files.getLastModifiedTime(file).toMillis();
            long now = System.currentTimeMillis();
            long away = now - seen;
            for (long[] d : DOWNTIME) away -= Math.max(0, Math.min(d[1], now) - Math.max(d[0], seen));
            return away;
        } catch (Exception e) {
            return -1;
        }
    }

    public static void check(MinecraftServer server) {
        int hours = Config.INACTIVE_KICK_HOURS.get();
        if (hours <= 0) return;
        long limit = hours * 3_600_000L;
        ValorisData data = ValorisData.get(server);
        Set<UUID> kings = new HashSet<>();
        for (Kingdom k : data.kingdoms()) KingdomService.kingOf(data, k).ifPresent(kings::add);

        for (City city : List.copyOf(data.all())) {
            Team team = FTBTeamsAPI.api().getManager().getTeamByID(city.teamId).orElse(null);
            if (!(team instanceof PartyTeam party)) continue;
            for (UUID member : List.copyOf(party.getMembers())) {
                if (member.equals(city.ruler) || member.equals(party.getOwner()) || kings.contains(member)) continue;
                long away = awayMillis(server, member);
                if (away < limit) continue;
                String name = Msg.nameOf(server, member);
                try {
                    com.valoris.events.TeamGuard.allow(() -> party.leave(member));
                } catch (Exception e) {
                    valoris2.LOGGER.warn("Could not remove inactive resident {} from {}", name, city.name, e);
                    continue;
                }
                valoris2.LOGGER.info("Removed {} from the city of {}: not on the server for {} hours", name, city.name,
                        away / 3_600_000L);
                Msg.toTeam(city.teamId, Msg.text(name + " was removed from " + city.name + " after more than " + hours
                        + " hours away from the server.", ChatFormatting.YELLOW));
            }
        }
    }
}
