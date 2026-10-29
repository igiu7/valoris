package com.valoris.diplomacy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.data.Relation;
import com.valoris.data.ValorisData;
import com.valoris.diplomacy.Requests.Proposal;
import com.valoris.diplomacy.Requests.Type;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.util.Errors;
import com.valoris.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Diplomacy between realms: kingdoms (acted for by their king) and independent cities (acted for by their ruler).
 * Alliance and peace need the other side to accept; war and breaking an alliance are one-sided.
 */
public final class DiplomacyService {
    private DiplomacyService() {}

    /** Relation between the realms of two city teams (empty = neutral or the same realm). */
    public static Optional<Relation> relationBetweenCities(MinecraftServer server, UUID cityTeamA, UUID cityTeamB) {
        ValorisData data = ValorisData.get(server);
        Optional<Realm> a = data.byTeam(cityTeamA).flatMap(c -> Realms.ofCity(data, c));
        Optional<Realm> b = data.byTeam(cityTeamB).flatMap(c -> Realms.ofCity(data, c));
        if (a.isEmpty() || b.isEmpty() || a.get().id().equals(b.get().id())) return Optional.empty();
        return data.relation(a.get().id(), b.get().id());
    }

    private static Realm other(ValorisData data, Realm own, String name) throws CommandSyntaxException {
        Realm r = Realms.byName(data, name).orElseThrow(() -> Errors.of("No kingdom or city called " + name + "."));
        if (r.id().equals(own.id())) throw Errors.of("That is your own realm.");
        return r;
    }

    // ------------------------------------------------------------ proposals

    public static void propose(ServerPlayer leader, String targetName, Proposal proposal) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm target = other(data, own, targetName);
        Optional<Relation> current = data.relation(own.id(), target.id());

        switch (proposal) {
            case ALLIANCE -> {
                if (current.filter(r -> r == Relation.WAR).isPresent()) throw Errors.of("You are at war. Make peace first.");
                if (current.filter(r -> r == Relation.ALLIANCE).isPresent()) throw Errors.of("You are already allied.");
            }
            case PEACE -> {
                if (current.filter(r -> r == Relation.WAR).isEmpty()) throw Errors.of("You are not at war with them.");
                if (!WarService.canMakePeace(data, own.id(), target.id())) {
                    throw Errors.of("Neither of you leads a side of this war. Offer peace to the leader of their side.");
                }
            }
        }
        // both sides proposed the same thing: that is an agreement
        Optional<Requests.Request> mirror = Requests.find(server, Type.DIPLOMACY, target.id(), own.id())
                .filter(r -> r.proposal() == proposal);
        if (mirror.isPresent()) {
            agree(server, data, own, target, proposal);
            return;
        }

        Requests.add(server, Type.DIPLOMACY, own.id(), target.id(), proposal);
        Msg.ok(leader, "Proposal (" + describe(proposal) + ") sent to the " + target.title() + ".");
        Realms.toLeader(server, data, target,
                Msg.text("The " + own.title() + " proposes " + describe(proposal) + " ", ChatFormatting.GOLD)
                        .append(Msg.button("[Accept]", ChatFormatting.GREEN, "/valoris diplomacy accept " + Msg.q(own.name())))
                        .append(Component.literal(" "))
                        .append(Msg.button("[Decline]", ChatFormatting.RED, "/valoris diplomacy decline " + Msg.q(own.name()))));
        Inbox.notifyLeader(server, target);
    }

    public static void accept(ServerPlayer leader, String fromName) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm from = other(data, own, fromName);
        Requests.Request r = Requests.find(server, Type.DIPLOMACY, from.id(), own.id())
                .orElseThrow(() -> Errors.of("They have no (valid) proposal for you."));
        // things may have changed since the proposal was sent
        Optional<Relation> current = data.relation(own.id(), from.id());
        if (r.proposal() == Proposal.ALLIANCE && current.isPresent()) {
            Requests.remove(r);
            throw Errors.of(current.get() == Relation.WAR ? "You are at war with them now." : "You are already allied.");
        }
        if (r.proposal() == Proposal.PEACE && !WarService.canMakePeace(data, own.id(), from.id())) {
            Requests.remove(r);
            throw Errors.of("That peace offer no longer applies.");
        }
        agree(server, data, own, from, r.proposal());
    }

    private static void agree(MinecraftServer server, ValorisData data, Realm a, Realm b, Proposal proposal) {
        switch (proposal) {
            case ALLIANCE -> data.setRelation(a.id(), b.id(), Relation.ALLIANCE);
            case PEACE -> WarService.makePeace(server, data, a, b);
        }
        Requests.removeWhere(x -> x.type() == Type.DIPLOMACY
                && ((x.from().equals(a.id()) && x.to().equals(b.id())) || (x.from().equals(b.id()) && x.to().equals(a.id()))));
        Component msg = Msg.text("Diplomacy: the " + a.title() + " and the " + b.title() + " agreed on "
                + describe(proposal) + ".", ChatFormatting.GREEN);
        Realms.message(a, msg);
        Realms.message(b, msg);
        // new allies defend each other in the wars in which they were attacked
        if (proposal == Proposal.ALLIANCE) WarService.allianceFormed(server, data, a, b);
    }

    public static void decline(ServerPlayer leader, String fromName) throws CommandSyntaxException {
        Realm own = Realms.requireLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm from = other(data, own, fromName);
        Requests.find(server, Type.DIPLOMACY, from.id(), own.id()).ifPresent(Requests::remove);
        Msg.ok(leader, "Proposal declined.");
        Realms.toLeader(server, data, from, Msg.text("The " + own.title() + " declined your proposal.", ChatFormatting.RED));
    }

    /** Withdraws a proposal you sent. */
    public static void withdraw(ServerPlayer leader, String toName) throws CommandSyntaxException {
        Realm own = Realms.requireLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm to = other(data, own, toName);
        Requests.Request r = Requests.find(server, Type.DIPLOMACY, own.id(), to.id())
                .orElseThrow(() -> Errors.of("You have no pending proposal for them."));
        Requests.remove(r);
        Msg.ok(leader, "Proposal withdrawn.");
    }

    // ------------------------------------------------------------ unilateral actions

    public static void declareWar(ServerPlayer leader, String targetName) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm target = other(data, own, targetName);
        Optional<Relation> current = data.relation(own.id(), target.id());
        if (current.filter(r -> r == Relation.WAR).isPresent()) throw Errors.of("You are already at war.");
        if (current.isPresent()) throw Errors.of("Break the " + current.get().label() + " first.");
        long truce = data.truceUntil(own.id(), target.id());
        if (truce > 0) {
            throw Errors.of("You have a truce with the " + target.title() + " for another " + WarService.truceLeft(truce) + ".");
        }

        WarService.declare(server, data, own, target);
    }

    public static void breakTreaty(ServerPlayer leader, String targetName) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm target = other(data, own, targetName);
        Relation rel = data.relation(own.id(), target.id())
                .filter(r -> r != Relation.WAR)
                .orElseThrow(() -> Errors.of("You have no alliance with them."));
        data.setRelation(own.id(), target.id(), null);

        Component msg = Msg.text("Diplomacy: the " + own.title() + " broke the " + rel.label() + " with the "
                + target.title() + ".", ChatFormatting.YELLOW);
        Realms.message(own, msg);
        Realms.message(target, msg);
    }

    // ------------------------------------------------------------ info

    public static void status(ServerPlayer player) throws CommandSyntaxException {
        Realm own = Realms.of(player).orElseThrow(() -> Errors.of("You do not belong to a founded city."));
        ValorisData data = ValorisData.get(player.getServer());
        Map<UUID, Relation> rels = data.relationsOf(own.id());
        StringBuilder sb = new StringBuilder("Diplomacy of the " + own.title() + ":");
        Map<UUID, Long> truces = data.trucesOf(own.id());
        if (rels.isEmpty() && truces.isEmpty()) sb.append("\n - no alliances, wars or truces");
        rels.forEach((id, rel) -> sb.append("\n - ")
                .append(Realms.title(data, id, "?")).append(": ").append(rel.label()));
        truces.forEach((id, until) -> sb.append("\n - ").append(Realms.title(data, id, "?"))
                .append(": truce, ").append(WarService.truceLeft(until)).append(" left"));
        Msg.info(player, sb.toString());
    }

    private static String describe(Proposal p) {
        return switch (p) {
            case ALLIANCE -> "an alliance";
            case PEACE -> "peace";
        };
    }
}
