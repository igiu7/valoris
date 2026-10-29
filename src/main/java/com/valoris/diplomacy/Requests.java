package com.valoris.diplomacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.valoris.Config;

import net.minecraft.server.MinecraftServer;

/** Pending, time-limited invitations and requests. Held in memory only (cleared on restart). */
public final class Requests {
    public enum Type {
        CITY_INVITE,     // from = city team id,   to = player uuid
        CITY_JOIN,       // from = player uuid,    to = city team id
        KINGDOM_INVITE,  // from = kingdom id,     to = city team id
        KINGDOM_JOIN,    // from = city team id,   to = kingdom id
        DIPLOMACY,       // from = realm id,       to = realm id
        WAR_CALL         // from = realm id,       to = allied realm id, ref = war id
    }

    public enum Proposal { ALLIANCE, PEACE }

    public record Request(Type type, UUID from, UUID to, Proposal proposal, UUID ref, long expires) {}

    private static final List<Request> PENDING = new ArrayList<>();

    private Requests() {}

    private static void purge(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        PENDING.removeIf(r -> r.expires() <= now);
    }

    public static void add(MinecraftServer server, Type type, UUID from, UUID to, Proposal proposal) {
        add(server, type, from, to, proposal, null);
    }

    /** ref distinguishes several requests between the same two parties (the war of a call to arms). */
    public static void add(MinecraftServer server, Type type, UUID from, UUID to, Proposal proposal, UUID ref) {
        purge(server);
        PENDING.removeIf(r -> r.type() == type && r.from().equals(from) && r.to().equals(to)
                && java.util.Objects.equals(r.ref(), ref));
        long expires = server.overworld().getGameTime() + Config.REQUEST_EXPIRY_SECONDS.get() * 20L;
        PENDING.add(new Request(type, from, to, proposal, ref, expires));
    }

    public static Optional<Request> find(MinecraftServer server, Type type, UUID from, UUID to, UUID ref) {
        purge(server);
        return PENDING.stream()
                .filter(r -> r.type() == type && r.from().equals(from) && r.to().equals(to) && java.util.Objects.equals(r.ref(), ref))
                .findFirst();
    }

    public static Optional<Request> find(MinecraftServer server, Type type, UUID from, UUID to) {
        purge(server);
        return PENDING.stream()
                .filter(r -> r.type() == type && r.from().equals(from) && r.to().equals(to))
                .findFirst();
    }

    /** All pending requests of a type addressed to the given id. */
    public static List<Request> listTo(MinecraftServer server, Type type, UUID to) {
        purge(server);
        return PENDING.stream().filter(r -> r.type() == type && r.to().equals(to)).toList();
    }

    /** All pending requests of a type sent by the given id. */
    public static List<Request> listFrom(MinecraftServer server, Type type, UUID from) {
        purge(server);
        return PENDING.stream().filter(r -> r.type() == type && r.from().equals(from)).toList();
    }

    public static void remove(Request r) {
        PENDING.remove(r);
    }

    public static void removeWhere(Predicate<Request> predicate) {
        PENDING.removeIf(predicate);
    }

    public static void clear() {
        PENDING.clear();
    }
}
