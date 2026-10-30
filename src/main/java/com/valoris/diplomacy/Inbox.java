package com.valoris.diplomacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.valoris.city.Roles;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Conquest;
import com.valoris.data.ValorisData.ConquestState;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.data.ValorisData.War;
import com.valoris.diplomacy.Requests.Proposal;
import com.valoris.diplomacy.Requests.Request;
import com.valoris.diplomacy.Requests.Type;
import com.valoris.kingdom.KingdomService;
import com.valoris.net.InboxPayload;
import com.valoris.net.InboxPayload.Entry;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.util.Msg;
import com.valoris.valoris2;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every request a player may answer, described for the requests window. A request is listed only for the one
 * who can answer it: invitations to a player for that player, join requests of a city for its ruler, kingdom
 * invitations for the ruler of the (independent) city, requests to a kingdom for its king, diplomacy and calls
 * to arms for the leader of the realm (the king, or the ruler of an independent city), conquest requests for
 * the ruler of the conquered city. The commands behind the buttons check the same thing again.
 */
@EventBusSubscriber(modid = valoris2.MODID)
public final class Inbox {
    private Inbox() {}

    private static int seconds(MinecraftServer server, Request r) {
        return (int) Math.max(0, (r.expires() - server.overworld().getGameTime()) / 20);
    }

    public static List<Entry> collect(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ValorisData data = ValorisData.get(server);
        List<Entry> out = new ArrayList<>();
        Optional<City> city = Roles.cityOfMember(p);

        // a city invites me (I have no city yet)
        for (Request r : city.isPresent() ? List.<Request>of() : Requests.listTo(server, Type.CITY_INVITE, p.getUUID())) {
            data.byTeam(r.from()).filter(c -> c.founded).ifPresent(c -> {
                String realm = data.kingdomOfCity(c.teamId).map(k -> "part of the Kingdom of " + k.name).orElse("an independent city");
                out.add(new Entry("ci:" + c.teamId, "city", "Invitation to " + c.name,
                        Msg.nameOf(server, c.ruler) + " invites you to live in " + c.name + " (" + realm + "). As a resident "
                                + "you may build on its land, and your skills help the city discover new technologies.",
                        "Accept", "valoris resident accept " + Msg.q(c.name),
                        "Decline", "valoris resident decline " + Msg.q(c.name), seconds(server, r)));
            });
        }

        // I rule a founded city
        city.filter(c -> c.founded && c.ruler.equals(p.getUUID())).ifPresent(c -> {
            for (Request r : Requests.listTo(server, Type.CITY_JOIN, c.teamId)) {
                String name = Msg.nameOf(server, r.from());
                out.add(new Entry("cj:" + r.from(), "city", name + " asks to join " + c.name,
                        name + " would like to become a resident of your city. Residents may build on the city's land "
                                + "and their skills count for the city's technologies.",
                        "Accept", "valoris city accept " + name, "Decline", "valoris city decline " + name, seconds(server, r)));
            }
            if (data.kingdomOfCity(c.teamId).isEmpty()) {
                for (Request r : Requests.listTo(server, Type.KINGDOM_INVITE, c.teamId)) {
                    data.kingdomById(r.from()).ifPresent(k -> {
                        String king = KingdomService.kingOf(data, k).map(id -> Msg.nameOf(server, id)).orElse("?");
                        boolean atWar = data.relationsOf(c.teamId).containsValue(com.valoris.data.Relation.WAR);
                        out.add(new Entry("ki:" + k.id, "kingdom", "The Kingdom of " + k.name + " invites " + c.name,
                                "King " + king + " invites your city into the Kingdom of " + k.name + " (" + k.cities.size()
                                        + " cities). The king then leads your diplomacy and wars, your city wears the "
                                        + "kingdom's colour, your own alliances end, and you can leave only by rebellion "
                                        + "or the king's decision." + (atWar ? " Your city is at war: make peace before joining." : ""),
                                "Join the kingdom", "valoris city kingdom accept " + Msg.q(k.name),
                                "Decline", "valoris city kingdom decline " + Msg.q(k.name), seconds(server, r)));
                    });
                }
            }
        });

        // I am the king
        Roles.kingdomOf(p).filter(k -> KingdomService.kingOf(data, k).map(id -> id.equals(p.getUUID())).orElse(false))
                .ifPresent(k -> {
                    for (Request r : Requests.listTo(server, Type.KINGDOM_JOIN, k.id)) {
                        data.byTeam(r.from()).ifPresent(c -> out.add(new Entry("kj:" + c.teamId, "kingdom",
                                c.name + " asks to join your kingdom",
                                "The city of " + c.name + " (ruler " + Msg.nameOf(server, c.ruler) + ") wants to become part "
                                        + "of the Kingdom of " + k.name + ". It would wear your colour and you would lead its "
                                        + "diplomacy and wars.",
                                "Accept", "valoris kingdom accept " + Msg.q(c.name),
                                "Decline", "valoris kingdom decline " + Msg.q(c.name), seconds(server, r))));
                    }
                });

        // I lead a realm: diplomacy and calls to arms
        Optional<Realm> realm = Realms.of(p);
        if (realm.isPresent() && Realms.isLeader(p)) {
            Realm own = realm.get();
            for (Request r : Requests.listTo(server, Type.DIPLOMACY, own.id())) {
                Realms.byId(data, r.from()).ifPresent(from -> out.add(diplomacy(data, own, from, r, seconds(server, r))));
            }
            for (Request r : Requests.listTo(server, Type.WAR_CALL, own.id())) {
                Optional<War> war = data.warById(r.ref());
                Optional<Realm> caller = Realms.byId(data, r.from());
                if (war.isEmpty() || caller.isEmpty()) continue;
                War w = war.get();
                int side = w.sideOf(caller.get().id());
                if (side == 0) continue;
                List<String> enemies = new ArrayList<>();
                for (UUID e : w.opposite(side)) enemies.add(WarService.title(data, w, e));
                UUID leader = w.opposite(side).get(0);
                String enemyName = Realms.byId(data, leader).map(Realm::name).orElse(w.names.getOrDefault(leader, "?"));
                out.add(new Entry("wc:" + w.id + ":" + caller.get().id(), "war",
                        "The " + caller.get().title() + " calls you to arms",
                        "Your ally asks you to join its war against the " + WarService.title(data, w, leader) + ". You would "
                                + "be at war with: " + String.join(", ", enemies) + ", and your army would fight in every "
                                + "siege of your ally's side in this war.",
                        "Join the war", "valoris diplomacy join " + Msg.q(caller.get().name()) + " " + Msg.q(enemyName),
                        "Refuse", "valoris diplomacy refuse " + Msg.q(caller.get().name()) + " " + Msg.q(enemyName),
                        seconds(server, r)));
            }
        }

        // my city was conquered and the conqueror asks me to join
        city.filter(c -> c.ruler.equals(p.getUUID())).flatMap(c -> data.conquestOfCity(c.teamId))
                .filter(c -> c.state == ConquestState.AWAITING_RULER).ifPresent(c -> out.add(conquest(server, data, c)));
        return out;
    }

    private static Entry diplomacy(ValorisData data, Realm own, Realm from, Request r, int seconds) {
        String accept = "valoris diplomacy accept " + Msg.q(from.name());
        String decline = "valoris diplomacy decline " + Msg.q(from.name());
        if (r.proposal() == Proposal.ALLIANCE) {
            return new Entry("da:" + from.id(), "alliance", "The " + from.title() + " proposes an alliance",
                    "Allies come to each other's defence automatically when one of them is attacked, can call each other "
                            + "to arms in their wars and can survey each other's cities. Allies cannot fight each other.",
                    "Accept the alliance", accept, "Decline", decline, seconds);
        }
        boolean wholeWar = data.warsBetween(own.id(), from.id()).stream()
                .anyMatch(w -> w.isLeader(own.id()) && w.isLeader(from.id()));
        String text = wholeWar
                ? "Accepting ends the war between your sides for everyone fighting in it."
                : "This is a separate peace: whichever of you does not lead its side leaves the war; the others fight on.";
        return new Entry("dp:" + from.id(), "peace", "The " + from.title() + " offers peace", text,
                "Accept peace", accept, "Decline", decline, seconds);
    }

    private static Entry conquest(MinecraftServer server, ValorisData data, Conquest c) {
        String by = Realms.title(data, c.attackerKingdom, c.attackerName);
        int left = (int) Math.max(0, (c.deadline - server.overworld().getGameTime()) / 20);
        return new Entry("cq:" + c.cityTeam, "conquest", "The " + by + " conquered " + c.cityName,
                "Your city fell in a siege. The " + by + " asks you to stay its ruler and join them. If you decline, they "
                        + "decide again: they may appoint another resident as ruler or let the city go.",
                "Join them", "valoris war conquest accept", "Decline", "valoris war conquest decline", left);
    }

    // ------------------------------------------------------------------ delivery

    public static void send(ServerPlayer p, boolean popup, boolean open) {
        PacketDistributor.sendToPlayer(p, new InboxPayload(collect(p), popup, open));
    }

    /** A new request for this player: show it (as soon as no other screen is open). */
    public static void notify(MinecraftServer server, UUID playerId) {
        ServerPlayer p = server.getPlayerList().getPlayer(playerId);
        if (p != null) send(p, true, false);
    }

    public static void notifyLeader(MinecraftServer server, Realm realm) {
        Realms.leaderOf(ValorisData.get(server), realm).ifPresent(id -> notify(server, id));
    }

    public static void notifyKing(MinecraftServer server, Kingdom k) {
        KingdomService.kingOf(ValorisData.get(server), k).ifPresent(id -> notify(server, id));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !collect(p).isEmpty()) send(p, true, false);
    }
}
