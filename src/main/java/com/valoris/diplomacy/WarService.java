package com.valoris.diplomacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.data.Relation;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.War;
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
 * Wars between coalitions.
 * - The realm that declares a war leads the attackers, the realm attacked leads the defenders.
 * - Allies join no war by themselves, not even a defence: the attacked realm (like an attacker) calls them to arms,
 *   and they accept or refuse. When a realm is attacked its allies are told and its leader gets a call button for each.
 * - A realm never joins a side while it is allied with someone on the other side.
 * - Peace between the two leaders ends the whole war; peace between a leader and a member of the other side is a
 *   separate peace: that member leaves the war.
 * - Everyone on a side fights in every siege of that side in the war (see SiegeService).
 */
public final class WarService {
    private WarService() {}

    // ------------------------------------------------------------------ queries

    public static String title(ValorisData data, War w, UUID realm) {
        return Realms.title(data, realm, w.names.getOrDefault(realm, "?"));
    }

    private static String sideTitles(ValorisData data, War w, int side) {
        List<String> out = new ArrayList<>();
        for (UUID id : w.side(side)) out.add(title(data, w, id));
        return String.join(", ", out);
    }

    private static boolean allied(ValorisData data, UUID a, UUID b) {
        return data.relation(a, b).filter(r -> r == Relation.ALLIANCE).isPresent();
    }

    /** Why a realm may not join this side of the war, or null when it may. */
    public static String joinProblem(ValorisData data, War w, UUID realm, int side) {
        if (Realms.byId(data, realm).isEmpty()) return "it no longer exists";
        if (w.sideOf(realm) == side) return "it already fights on this side";
        if (w.sideOf(realm) != 0) return "it fights on the other side";
        for (UUID enemy : w.opposite(side)) {
            if (allied(data, realm, enemy)) return "it is allied with the " + title(data, w, enemy);
        }
        for (UUID friend : w.side(side)) { // no fighting side by side with a realm it is at war with elsewhere
            if (!data.warsBetween(realm, friend).isEmpty()) return "it is at war with the " + title(data, w, friend);
        }
        // no joining a war against the realm it has a truce with, when that realm leads the other side
        // (the one that declared the war, or the one it was declared on); other members of that side do not count
        List<UUID> enemies = w.opposite(side);
        if (!enemies.isEmpty()) {
            UUID enemyLeader = enemies.get(0);
            long until = data.truceUntil(realm, enemyLeader);
            if (until > 0) {
                return "it has a truce with the " + title(data, w, enemyLeader) + ", who leads the other side, for another "
                        + com.valoris.moderation.Durations.format(until - System.currentTimeMillis());
            }
        }
        return null;
    }

    /** Time left of a truce as text, e.g. "5h 12m". */
    public static String truceLeft(long until) {
        return com.valoris.moderation.Durations.format(until - System.currentTimeMillis());
    }

    /** True when peace between a and b would end or shorten a war (one of them leads its side). */
    public static boolean canMakePeace(ValorisData data, UUID a, UUID b) {
        return data.warsBetween(a, b).stream().anyMatch(w -> w.isLeader(a) || w.isLeader(b));
    }

    // ------------------------------------------------------------------ declaring and joining

    public static War declare(MinecraftServer server, ValorisData data, Realm attacker, Realm defender) {
        return declare(server, data, attacker, defender, false);
    }

    /** rebellion: a city rising against its kingdom (already announced as such: no "WAR!" news on Discord). */
    public static War declare(MinecraftServer server, ValorisData data, Realm attacker, Realm defender, boolean rebellion) {
        War w = new War(UUID.randomUUID());
        w.rebellion = rebellion;
        w.attackers.add(attacker.id());
        w.defenders.add(defender.id());
        w.names.put(attacker.id(), attacker.name());
        w.names.put(defender.id(), defender.name());
        long now = System.currentTimeMillis();
        w.joinReasons.put(attacker.id(), "declared the war");
        w.joinReasons.put(defender.id(), "was attacked");
        w.joinedAt.put(attacker.id(), now);
        w.joinedAt.put(defender.id(), now);
        w.log("The " + attacker.title() + " declared war on the " + defender.title() + ".");
        w.started = System.currentTimeMillis();
        data.addWar(w);
        Requests.removeWhere(x -> x.type() == Type.DIPLOMACY && ((x.from().equals(attacker.id()) && x.to().equals(defender.id()))
                || (x.from().equals(defender.id()) && x.to().equals(attacker.id()))));
        String warText = "WAR! The " + attacker.title() + " declared war on the " + defender.title() + "!";
        if (rebellion) Msg.broadcast(server, Msg.text(warText, ChatFormatting.DARK_RED));
        else Msg.news(server, "⚔️", warText, ChatFormatting.DARK_RED);
        // both realms also see it in the middle of the screen
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID realm = Realms.of(p).map(Realm::id).orElse(null);
            if (attacker.id().equals(realm)) {
                com.valoris.war.SiegeAnnouncer.title(p, Component.literal("WAR!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal("You declared war on the " + defender.title()).withStyle(ChatFormatting.GOLD),
                        net.minecraft.sounds.SoundEvents.RAID_HORN.value());
            } else if (defender.id().equals(realm)) {
                com.valoris.war.SiegeAnnouncer.title(p, Component.literal("WAR!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal("The " + attacker.title() + " declared war on you").withStyle(ChatFormatting.GOLD),
                        net.minecraft.sounds.SoundEvents.RAID_HORN.value());
            }
        }
        defendAllies(server, data, w, defender);
        return w;
    }

    /** The allies of an attacked realm hear of it; its leader may call them to arms (they join only if they accept). */
    private static void defendAllies(MinecraftServer server, ValorisData data, War w, Realm attacked) {
        int side = w.sideOf(attacked.id());
        UUID enemyLeader = w.opposite(side).get(0);
        String enemy = title(data, w, enemyLeader);
        String enemyArg = Msg.q(Realms.byId(data, enemyLeader).map(Realm::name).orElse(w.names.getOrDefault(enemyLeader, enemy)));
        net.minecraft.network.chat.MutableComponent calls = Msg.text("You were attacked. Call your allies to arms:", ChatFormatting.GOLD);
        boolean any = false;
        for (Map.Entry<UUID, Relation> e : data.relationsOf(attacked.id()).entrySet()) {
            if (e.getValue() != Relation.ALLIANCE) continue;
            Optional<Realm> ally = Realms.byId(data, e.getKey());
            if (ally.isEmpty() || joinProblem(data, w, ally.get().id(), side) != null) continue;
            Realms.message(ally.get(), Msg.text("Your ally, the " + attacked.title() + ", was attacked by the " + enemy
                    + ". You join its defence only if it calls you to arms.", ChatFormatting.YELLOW));
            calls.append(Component.literal(" ")).append(Msg.button("[Call the " + ally.get().title() + "]", ChatFormatting.GREEN,
                    "/valoris diplomacy call " + Msg.q(ally.get().name()) + " " + enemyArg));
            any = true;
        }
        if (any) Realms.toLeader(server, data, attacked, calls);
    }

    public static void join(MinecraftServer server, ValorisData data, War w, Realm realm, int side, String why) {
        w.side(side).add(realm.id());
        w.names.put(realm.id(), realm.name());
        w.joinReasons.put(realm.id(), why.replaceFirst("^to defend", "came to defend").replaceFirst("^at the call of", "answered the call of"));
        w.joinedAt.put(realm.id(), System.currentTimeMillis());
        w.log("The " + realm.title() + " joined the side of the " + title(data, w, w.side(side).get(0)) + " " + why + ".");
        data.touch();
        for (UUID enemy : w.opposite(side)) {
            Requests.removeWhere(x -> x.type() == Type.DIPLOMACY && x.proposal() == Requests.Proposal.ALLIANCE
                    && ((x.from().equals(realm.id()) && x.to().equals(enemy)) || (x.from().equals(enemy) && x.to().equals(realm.id()))));
        }
        UUID leader = w.side(side).get(0);
        UUID enemyLeader = w.opposite(side).get(0);
        Msg.news(server, "⚔️", "The " + realm.title() + " joins the war against the " + title(data, w, enemyLeader)
                + " " + why + ".", ChatFormatting.RED);
        Realms.message(realm, Msg.text("You are now at war with: " + sideTitles(data, w, side == 1 ? 2 : 1)
                + ". Your army takes part in every siege of the " + title(data, w, leader) + "'s side in this war.",
                ChatFormatting.GOLD));
    }

    /** Two realms became allies: neither joins the other's wars by itself (only by a call to arms). */
    public static void allianceFormed(MinecraftServer server, ValorisData data, Realm a, Realm b) {
    }

    // ------------------------------------------------------------------ calls to arms

    private static War sharedWar(ValorisData data, Realm own, String enemyName) throws CommandSyntaxException {
        Realm enemy = Realms.byName(data, enemyName).orElseThrow(() -> Errors.of("No kingdom or city called " + enemyName + "."));
        return data.warsBetween(own.id(), enemy.id()).stream().findFirst()
                .orElseThrow(() -> Errors.of("There is no war between you and the " + enemy.title() + "."));
    }

    /** A leader asks an ally to join one of its wars. */
    public static void call(ServerPlayer leader, String allyName, String enemyName) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm ally = Realms.byName(data, allyName).orElseThrow(() -> Errors.of("No kingdom or city called " + allyName + "."));
        if (!allied(data, own.id(), ally.id())) throw Errors.of("The " + ally.title() + " is not your ally.");
        War w = sharedWar(data, own, enemyName);
        int side = w.sideOf(own.id());
        String problem = joinProblem(data, w, ally.id(), side);
        if (problem != null) throw Errors.of("The " + ally.title() + " cannot join that war: " + problem + ".");

        String enemy = title(data, w, w.opposite(side).get(0));
        Requests.add(server, Type.WAR_CALL, own.id(), ally.id(), null, w.id);
        Msg.ok(leader, "You called the " + ally.title() + " to arms against the " + enemy + ".");
        UUID enemyLeader = w.opposite(side).get(0);
        String enemyArg = Msg.q(Realms.byId(data, enemyLeader).map(Realm::name).orElse(w.names.getOrDefault(enemyLeader, enemyName)));
        Realms.toLeader(server, data, ally, Msg.text("Your ally, the " + own.title() + ", calls you to war against the "
                        + enemy + " ", ChatFormatting.GOLD)
                .append(Msg.button("[Join]", ChatFormatting.GREEN, "/valoris diplomacy join " + Msg.q(own.name()) + " " + enemyArg))
                .append(Component.literal(" "))
                .append(Msg.button("[Refuse]", ChatFormatting.RED, "/valoris diplomacy refuse " + Msg.q(own.name()) + " " + enemyArg)));
        Inbox.notifyLeader(server, ally);
    }

    /** The ally's answer to a call to arms. */
    public static void answer(ServerPlayer leader, String callerName, String enemyName, boolean accept) throws CommandSyntaxException {
        Realm own = Realms.requireActingLeader(leader);
        MinecraftServer server = leader.getServer();
        ValorisData data = ValorisData.get(server);
        Realm caller = Realms.byName(data, callerName).orElseThrow(() -> Errors.of("No kingdom or city called " + callerName + "."));
        War w = sharedWar(data, caller, enemyName);
        Requests.Request r = Requests.find(server, Type.WAR_CALL, caller.id(), own.id(), w.id)
                .orElseThrow(() -> Errors.of("You have no (valid) call to arms for that war."));
        Requests.remove(r);
        if (accept && !allied(data, own.id(), caller.id())) throw Errors.of("You are no longer allied with the " + caller.title() + ".");
        if (!accept) {
            Msg.ok(leader, "You refused the call to arms.");
            Realms.toLeader(server, data, caller, Msg.text("The " + own.title() + " refused to join your war.", ChatFormatting.RED));
            return;
        }
        int side = w.sideOf(caller.id());
        String problem = joinProblem(data, w, own.id(), side);
        if (problem != null) throw Errors.of("You cannot join that war: " + problem + ".");
        join(server, data, w, own, side, "at the call of its ally, the " + caller.title());
    }

    // ------------------------------------------------------------------ peace and leaving

    /**
     * Peace agreed between a and b: ends their wars, or takes the non-leader out of them. Then every pair of realms
     * that stopped fighting because of it gets a truce (Config truceHours): peace between the leaders gives every
     * attacker a truce with every defender; a separate peace gives the realm that left a truce with the whole side it
     * fought. Pairs still at war through another war get none.
     */
    public static void makePeace(MinecraftServer server, ValorisData data, Realm a, Realm b) {
        List<UUID[]> pairs = new ArrayList<>();
        for (War w : data.warsBetween(a.id(), b.id())) {
            boolean la = w.isLeader(a.id()), lb = w.isLeader(b.id());
            if (la && lb) {
                for (UUID x : w.attackers) for (UUID y : w.defenders) pairs.add(new UUID[]{x, y});
                end(server, data, w);
            } else if (la || lb) {
                Realm member = la ? b : a;
                for (UUID y : w.opposite(w.sideOf(member.id()))) pairs.add(new UUID[]{member.id(), y});
                leave(server, data, w, member.id(), "made a separate peace");
            }
        }
        long hours = com.valoris.Config.TRUCE_HOURS.get();
        if (hours <= 0) return;
        long until = System.currentTimeMillis() + hours * 3_600_000L;
        java.util.Set<UUID> told = new java.util.HashSet<>();
        for (UUID[] p : pairs) {
            if (!data.warsBetween(p[0], p[1]).isEmpty()) continue; // still fighting each other elsewhere
            data.setTruce(p[0], p[1], until);
            told.add(p[0]);
            told.add(p[1]);
        }
        Component msg = Msg.text("Truce: realms that made peace may not go to war with each other for " + hours
                + " hours.", ChatFormatting.AQUA);
        for (UUID id : told) Realms.message(data, id, msg);
    }

    public static void leave(MinecraftServer server, ValorisData data, War w, UUID realm, String why) {
        String name = title(data, w, realm);
        boolean wasLeader = w.isLeader(realm);
        int side = w.sideOf(realm);
        if (side != 0 && w.side(side).size() == 1) { // the last of its side: the war is over
            boolean peace = why.equals("made a separate peace");
            end(server, data, w, peace ? 0 : side, "the " + name + " " + why);
            return;
        }
        w.remove(realm);
        w.log("The " + name + " " + why + " and left the war.");
        data.touch();
        Msg.news(server, "🏳️", "The " + name + " " + why + " and leaves the war.", ChatFormatting.YELLOW);
        if (wasLeader && side != 0 && !w.side(side).isEmpty()) {
            Msg.broadcast(server, Msg.text("The " + title(data, w, w.side(side).get(0)) + " now leads its side of the war.",
                    ChatFormatting.GOLD));
        }
    }

    /** Peace was made. */
    public static void end(MinecraftServer server, ValorisData data, War w) {
        end(server, data, w, 0, null);
    }

    /**
     * The war is over. lostSide: the side that has no realm left in it (1 attackers, 2 defenders), which lost;
     * 0 = peace was made, and the side that did better wins (cities taken, then sieges repelled, then kills).
     */
    private static void end(MinecraftServer server, ValorisData data, War w, int lostSide, String how) {
        data.removeWar(w.id);
        Requests.removeWhere(x -> x.type() == Type.WAR_CALL && w.id.equals(x.ref()));
        String atk = w.attackers.isEmpty() ? "?" : sideTitles(data, w, 1);
        String def = w.defenders.isEmpty() ? "?" : sideTitles(data, w, 2);
        int winner = lostSide != 0 ? 3 - lostSide : betterSide(w);
        String result = winner == 0 ? "It ended in a draw." : "The " + (winner == 1 ? atk : def) + " won"
                + (how != null ? " (" + how + ")." : ".");
        if (!w.rebellion) {
            Msg.news(server, "🕊️", "PEACE! The war between " + atk + " and " + def + " is over. " + result,
                    ChatFormatting.GREEN);
        } else { // the rebel city leads the attackers, the kingdom it rose against the defenders
            String rebel = w.attackers.isEmpty() ? atk : title(data, w, w.attackers.get(0));
            String kingdom = w.defenders.isEmpty() ? def : title(data, w, w.defenders.get(0));
            if (winner == 1) {
                Msg.news(server, "🏴", "The rebellion succeeded: the " + rebel + " has won its independence from the "
                        + kingdom + "!", ChatFormatting.GOLD);
            } else if (winner == 2) {
                Msg.news(server, "⚔️", "The rebellion failed: the " + kingdom + " crushed the uprising of the "
                        + rebel + ".", ChatFormatting.GOLD);
            } else {
                Msg.news(server, "🤝", "The rebellion of the " + rebel + " against the " + kingdom
                        + " ended without a winner; the city stays free.", ChatFormatting.GOLD);
            }
        }
        com.valoris.discord.DiscordLink.announceRaw(summary(data, w, atk, def, winner, how));
    }

    private static int betterSide(War w) {
        int[][] keys = {w.citiesTaken, w.siegesRepelled, w.sideKills};
        for (int[] k : keys) {
            if (k[1] != k[2]) return k[1] > k[2] ? 1 : 2;
        }
        return 0;
    }

    /** The end of the war for the Discord news channel: result, length and the numbers of both sides and every realm. */
    private static String summary(ValorisData data, War w, String atk, String def, int winner, String how) {
        java.util.function.Function<String, String> p = com.valoris.discord.DiscordLink::plain;
        long minutes = Math.max(0, (System.currentTimeMillis() - w.started) / 60_000L);
        String length = minutes >= 1440 ? (minutes / 1440) + " d " + (minutes % 1440 / 60) + " h"
                : minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min" : minutes + " min";
        StringBuilder sb = new StringBuilder();
        sb.append("📊 **").append(w.rebellion ? "The rebellion is over" : "The war is over").append(": ")
                .append(p.apply(atk)).append(" vs ").append(p.apply(def)).append("**\n");
        sb.append("Result: **").append(winner == 0 ? "draw" : p.apply(winner == 1 ? atk : def) + " won").append("**");
        if (how != null && winner != 0) sb.append(" (").append(p.apply(how)).append(")");
        sb.append("\nLasted: ").append(length).append("\n\n");
        String[] sideName = {"", "Attackers", "Defenders"};
        for (int side = 1; side <= 2; side++) {
            sb.append("**").append(sideName[side]).append("**: ").append(p.apply(side == 1 ? atk : def)).append("\n")
                    .append("Kills ").append(w.sideKills[side]).append(" · cities taken ").append(w.citiesTaken[side])
                    .append(" · sieges repelled ").append(w.siegesRepelled[side]).append("\n\n");
        }
        if (!w.realmStats.isEmpty()) {
            sb.append("**Realms** (kills / deaths)\n");
            for (Map.Entry<UUID, int[]> e : w.realmStats.entrySet()) {
                sb.append("• ").append(p.apply(title(data, w, e.getKey()))).append(": ")
                        .append(e.getValue()[0]).append(" / ").append(e.getValue()[1]).append("\n");
            }
        }
        return sb.length() > 1900 ? sb.substring(0, 1900) + "…" : sb.toString();
    }
}
