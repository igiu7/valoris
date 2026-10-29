package com.valoris.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.kingdom.KingdomService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A realm is a side in diplomacy and war: a kingdom (led by its king) or an independent founded city that
 * belongs to no kingdom (led by its ruler). Relations, proposals, sieges and conquests store realm ids:
 * the kingdom id or the city's team id.
 */
public final class Realms {
    private Realms() {}

    /** kingdom != null for a kingdom realm, otherwise city is the independent city. */
    public record Realm(UUID id, String name, Kingdom kingdom, City city) {
        public boolean isKingdom() { return kingdom != null; }

        /** "Kingdom of X" or "City of X". */
        public String title() { return (isKingdom() ? "Kingdom of " : "City of ") + name; }
    }

    private static Realm of(Kingdom k) { return new Realm(k.id, k.name, k, null); }
    private static Realm of(City c) { return new Realm(c.teamId, c.name, null, c); }

    /** The realm a founded city fights for: its kingdom, or the city itself when independent. */
    public static Optional<Realm> ofCity(ValorisData data, City city) {
        if (!city.founded) return Optional.empty();
        return Optional.of(data.kingdomOfCity(city.teamId).map(Realms::of).orElseGet(() -> of(city)));
    }

    public static Optional<Realm> of(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        return Roles.cityOfMember(player).flatMap(c -> ofCity(data, c));
    }

    /** A kingdom id, or the team id of a founded city that is (still) independent. */
    public static Optional<Realm> byId(ValorisData data, UUID id) {
        Optional<Kingdom> k = data.kingdomById(id);
        if (k.isPresent()) return k.map(Realms::of);
        return data.byTeam(id).filter(c -> c.founded && data.kingdomOfCity(c.teamId).isEmpty()).map(Realms::of);
    }

    /** By kingdom name first; a city name means the city's realm (its kingdom, or the city itself). */
    public static Optional<Realm> byName(ValorisData data, String name) {
        Optional<Kingdom> k = data.kingdomByName(name);
        if (k.isPresent()) return k.map(Realms::of);
        return data.cityByName(name).flatMap(c -> ofCity(data, c));
    }

    public static List<Realm> all(ValorisData data) {
        List<Realm> out = new ArrayList<>();
        for (Kingdom k : data.kingdoms()) out.add(of(k));
        for (City c : data.all()) if (c.founded && data.kingdomOfCity(c.teamId).isEmpty()) out.add(of(c));
        return out;
    }

    public static Optional<UUID> leaderOf(ValorisData data, Realm r) {
        return r.isKingdom() ? KingdomService.kingOf(data, r.kingdom()) : Optional.of(r.city().ruler);
    }

    public static List<UUID> citiesOf(Realm r) {
        return r.isKingdom() ? new ArrayList<>(r.kingdom().cities) : List.of(r.city().teamId);
    }

    public static boolean isLeader(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        return of(player).flatMap(r -> leaderOf(data, r)).map(id -> id.equals(player.getUUID())).orElse(false);
    }

    /** The realm the player leads: a king his kingdom, the ruler of an independent city his city. */
    public static Realm requireLeader(ServerPlayer player) throws CommandSyntaxException {
        ValorisData data = ValorisData.get(player.getServer());
        City city = Roles.cityOfMember(player).orElseThrow(() -> Errors.of("You do not belong to a city."));
        if (!city.founded) throw Errors.of("Your city has not been founded yet.");
        Realm realm = ofCity(data, city).orElseThrow(() -> Errors.of("Your city has not been founded yet."));
        if (!leaderOf(data, realm).map(id -> id.equals(player.getUUID())).orElse(false)) {
            throw Errors.of(realm.isKingdom() ? "Only the king can do that." : "Only the city ruler can do that.");
        }
        return realm;
    }

    /** {@link #requireLeader}, for actions a fallen city may not take while its conqueror decides (diplomacy, war). */
    public static Realm requireActingLeader(ServerPlayer player) throws CommandSyntaxException {
        Realm realm = requireLeader(player);
        if (!realm.isKingdom()) {
            com.valoris.war.ConquestService.checkNotConquered(ValorisData.get(player.getServer()), realm.city());
        }
        return realm;
    }

    /** Title of a stored realm id, falling back to the stored name if the realm is gone. */
    public static String title(ValorisData data, UUID id, String fallback) {
        return byId(data, id).map(Realm::title).orElse(fallback);
    }

    public static void message(Realm r, Component c) {
        if (r.isKingdom()) Msg.toKingdom(r.kingdom(), c);
        else Msg.toTeam(r.city().teamId, c);
    }

    public static void message(ValorisData data, UUID id, Component c) {
        byId(data, id).ifPresent(r -> message(r, c));
    }

    public static void toLeader(MinecraftServer server, ValorisData data, Realm r, Component c) {
        leaderOf(data, r).ifPresent(id -> Msg.toPlayer(server, id, c));
    }

    /**
     * Makes sure the realm is a kingdom (an independent city that conquers another city founds one, named after
     * itself, with its relations, sieges and conquests carried over).
     */
    public static Kingdom asKingdom(MinecraftServer server, ValorisData data, Realm r) {
        if (r.isKingdom()) return r.kingdom();
        String name = r.name();
        int n = 2;
        while (data.kingdomByName(name).isPresent()) name = r.name() + " " + (n++);
        Kingdom k = new Kingdom(UUID.randomUUID(), name, r.city().teamId);
        k.color = Heraldry.teamColor(r.city().teamId);
        data.addKingdom(k);
        data.replaceRealm(r.city().teamId, k.id);
        Msg.broadcast(server, Msg.text("The city of " + r.name() + " has grown into the Kingdom of " + name + "!",
                ChatFormatting.GOLD));
        return k;
    }
}
