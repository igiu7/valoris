package com.valoris.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-persistent counters behind the leaderboard: every player's kills (players they killed) and deaths (any
 * cause), and every city's won and repelled sieges. They are kept per player and per city, so a realm's score is
 * the sum of its residents and its cities - it follows them into and out of kingdoms.
 */
public class LeaderboardData extends SavedData {
    private static final String NAME = "valoris_leaderboard";

    /** player -> {kills, deaths} */
    private final Map<UUID, int[]> players = new HashMap<>();
    /** city team -> {sieges won, sieges repelled} */
    private final Map<UUID, int[]> cities = new HashMap<>();

    public static LeaderboardData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(LeaderboardData::new, LeaderboardData::load, null), NAME);
    }

    private int[] player(UUID id) {
        return players.computeIfAbsent(id, k -> new int[2]);
    }

    private int[] city(UUID id) {
        return cities.computeIfAbsent(id, k -> new int[2]);
    }

    public void addKill(UUID player) { player(player)[0]++; setDirty(); }
    public void addDeath(UUID player) { player(player)[1]++; setDirty(); }
    public void addSiegeWon(UUID city) { city(city)[0]++; setDirty(); }
    public void addSiegeRepelled(UUID city) { city(city)[1]++; setDirty(); }

    public int kills(UUID player) { int[] v = players.get(player); return v == null ? 0 : v[0]; }
    public int deaths(UUID player) { int[] v = players.get(player); return v == null ? 0 : v[1]; }
    public int siegesWon(UUID city) { int[] v = cities.get(city); return v == null ? 0 : v[0]; }
    public int siegesRepelled(UUID city) { int[] v = cities.get(city); return v == null ? 0 : v[1]; }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag p = new CompoundTag();
        players.forEach((id, v) -> p.putIntArray(id.toString(), v));
        tag.put("players", p);
        CompoundTag c = new CompoundTag();
        cities.forEach((id, v) -> c.putIntArray(id.toString(), v));
        tag.put("cities", c);
        return tag;
    }

    public static LeaderboardData load(CompoundTag tag, HolderLookup.Provider registries) {
        LeaderboardData d = new LeaderboardData();
        read(tag.getCompound("players"), d.players);
        read(tag.getCompound("cities"), d.cities);
        return d;
    }

    private static void read(CompoundTag t, Map<UUID, int[]> into) {
        for (String key : t.getAllKeys()) {
            try {
                int[] v = t.getIntArray(key);
                into.put(UUID.fromString(key), new int[]{v.length > 0 ? v[0] : 0, v.length > 1 ? v[1] : 0});
            } catch (IllegalArgumentException ignored) {
                // not a UUID: skip
            }
        }
    }
}
