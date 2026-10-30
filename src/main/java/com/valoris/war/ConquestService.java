package com.valoris.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.Config;
import com.valoris.city.CityService;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Conquest;
import com.valoris.data.ValorisData.ConquestState;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.kingdom.KingdomService;
import com.valoris.realm.Realms;
import com.valoris.realm.Realms.Realm;
import com.valoris.net.ConquestPayload;
import com.valoris.net.Row;
import com.valoris.net.ValorisNet;
import com.valoris.util.Errors;
import com.valoris.util.Msg;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What happens after a city fell: a conquering king appoints a new ruler (a resident of the city or one of his
 * own) or forces the city to submit with its current ruler - both add the city to his kingdom; or he leaves the
 * city alone (it is free again). A conquered city is never destroyed: the winners loot it instead (see Looting).
 * The ruler of an independent city has no kingdom to add it to, so he can only leave it alone. A fallen capital has already ended its kingdom
 * (SiegeService.captured): its other cities are free, and its king may go on ruling it under the conqueror.
 */
public final class ConquestService {
    private ConquestService() {}

    // ------------------------------------------------------------ the conquered city is frozen

    /**
     * While the conqueror decides, the fallen city may not slip away: no disbanding, joining or founding a
     * kingdom, diplomacy, claims, a new banner, handing over or kicking residents.
     */
    public static void checkNotConquered(ValorisData data, City city) throws CommandSyntaxException {
        if (data.conquestOfCity(city.teamId).isPresent()) {
            throw Errors.of("The city of " + city.name + " has fallen and awaits the decision of its conqueror.");
        }
    }

    private static long decisionTicks() {
        return Config.CONQUEST_DECISION_MINUTES.get() * 1200L;
    }

    // ------------------------------------------------------------ menu delivery

    /** Opens the decision menu for the attacking king, if he is online. */
    public static void openMenu(MinecraftServer server, ValorisData data, Conquest c, String note) {
        Optional<Realm> atk = Realms.byId(data, c.attackerKingdom);
        if (atk.isEmpty()) return;
        Realms.leaderOf(data, atk.get()).ifPresent(id -> {
            ServerPlayer king = server.getPlayerList().getPlayer(id);
            if (king != null) ValorisNet.sendConquest(king, buildPayload(server, data, c, atk.get(), note));
        });
    }

    private static ConquestPayload buildPayload(MinecraftServer server, ValorisData data, Conquest c, Realm atk, String note) {
        City city = data.byTeam(c.cityTeam).orElse(null);
        List<Row> residents = new ArrayList<>();
        String ruler = "?";
        if (city != null) {
            ruler = Msg.nameOf(server, city.ruler);
            Team team = FTBTeamsAPI.api().getManager().getTeamByID(c.cityTeam).orElse(null);
            if (team != null) {
                for (UUID id : team.getMembers()) {
                    if (!id.equals(city.ruler)) residents.add(new Row(Msg.nameOf(server, id), id.toString(),
                            "Resident of " + city.name, 0, 0));
                }
            }
        }
        // the conqueror's own people can be put on the throne too (not the rulers of its cities)
        for (UUID own : Realms.citiesOf(atk)) {
            City from = data.byTeam(own).orElse(null);
            Team team = FTBTeamsAPI.api().getManager().getTeamByID(own).orElse(null);
            if (from == null || team == null) continue;
            for (UUID id : team.getMembers()) {
                if (!id.equals(from.ruler)) residents.add(new Row(Msg.nameOf(server, id), id.toString(),
                        "Your resident from " + from.name + " (moves to " + c.cityName + ")", 1, 0));
            }
        }
        boolean full = atk.isKingdom() && atk.kingdom().cities.size() >= Config.MAX_CITIES_PER_KINGDOM.get();
        return new ConquestPayload(c.cityName, c.attackerName, ruler, c.state == ConquestState.AWAITING_RULER, full,
                atk.isKingdom(), note, residents);
    }

    public static void onLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        long now = server.overworld().getGameTime();

        // the king gets the decision menu back
        Realms.of(player)
                .filter(r -> Realms.leaderOf(data, r).map(id -> id.equals(player.getUUID())).orElse(false))
                .flatMap(r -> data.firstConquestOf(r.id()))
                .filter(c -> c.state == ConquestState.CHOOSING)
                .ifPresent(c -> openMenu(server, data, c, ""));

        // a ruler gets the pending request back
        data.byRuler(player.getUUID())
                .flatMap(city -> data.conquestOfCity(city.teamId))
                .filter(c -> c.state == ConquestState.AWAITING_RULER && c.deadline > now)
                .ifPresent(c -> sendRequest(player, c, (c.deadline - now) / 20));
    }

    // ------------------------------------------------------------ king's decisions

    private static Conquest requireOwn(ServerPlayer king) throws CommandSyntaxException {
        Realm r = Realms.requireLeader(king);
        return ValorisData.get(king.getServer()).firstConquestOf(r.id())
                .orElseThrow(() -> Errors.of("You have no pending conquest."));
    }

    /**
     * A conquered city can only be added to a kingdom: the ruler of an independent city (no kingdom) can just leave
     * it alone.
     */
    private static void requireKingdomWithRoom(Realm atk) throws CommandSyntaxException {
        if (!atk.isKingdom()) {
            throw Errors.of("The City of " + atk.name() + " has no kingdom to add the city to. Leave it alone.");
        }
        KingdomService.checkRoom(atk.kingdom());
    }

    public static void reopen(ServerPlayer king) throws CommandSyntaxException {
        Conquest c = requireOwn(king);
        ValorisData data = ValorisData.get(king.getServer());
        Realm atk = Realms.byId(data, c.attackerKingdom).orElseThrow(() -> Errors.of("Your realm no longer exists."));
        ValorisNet.sendConquest(king, buildPayload(king.getServer(), data, c, atk, ""));
    }

    /**
     * Option 1: appoint a new ruler; the city joins the conqueror. The new ruler is a resident of the conquered city,
     * or one of the conqueror's own residents (not a city ruler), who then moves to the conquered city.
     */
    public static void appoint(ServerPlayer king, UUID target) throws CommandSyntaxException {
        Conquest c = requireOwn(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        if (c.state == ConquestState.AWAITING_RULER) throw Errors.of("You are waiting for the ruler's answer.");
        City city = data.byTeam(c.cityTeam).orElseThrow(() -> Errors.of("That city no longer exists."));
        Realm atk = Realms.byId(data, c.attackerKingdom).orElseThrow(() -> Errors.of("Your realm no longer exists."));
        requireKingdomWithRoom(atk);

        PartyTeam party = CityService.partyOf(city);
        if (target.equals(city.ruler)) throw Errors.of("That player already rules the city.");
        GameProfile profile = new GameProfile(target, Msg.nameOf(server, target));
        if (!party.getMembers().contains(target)) {
            // one of the conqueror's own residents: leaves their city and settles in the conquered one
            City from = null;
            for (UUID own : Realms.citiesOf(atk)) {
                City candidate = data.byTeam(own).orElse(null);
                if (candidate != null && CityService.partyOf(candidate).getMembers().contains(target)) from = candidate;
            }
            if (from == null) throw Errors.of("That player is neither a resident of " + city.name + " nor one of yours.");
            if (target.equals(from.ruler)) throw Errors.of("The ruler of " + from.name + " cannot leave their city.");
            PartyTeam fromParty = CityService.partyOf(from);
            com.valoris.events.TeamGuard.allow(() -> fromParty.leave(target));
            com.valoris.events.TeamGuard.allow(() -> party.join(server.getPlayerList().getPlayer(target), profile));
            Msg.toTeam(from.teamId, Msg.text(profile.getName() + " left " + from.name + " to rule the conquered city of "
                    + city.name + ".", ChatFormatting.GOLD));
        }

        party.transferOwnership(server.createCommandSourceStack(), profile);
        city.ruler = target;
        String name = Msg.nameOf(server, target);
        annex(server, data, c, city, atk);
        Msg.toPlayer(server, target, Msg.text("You now rule the conquered city of " + city.name
                + ". Place a new city banner with /valoris city banner.", ChatFormatting.GOLD));
        Msg.ok(king, name + " was appointed ruler of " + city.name + ".");
    }

    /**
     * Option 2: the city is forced into the conquering kingdom and keeps its current ruler (he is not asked).
     * If it was a kingdom's capital, that kingdom already fell when the city did (its other cities are free), so
     * the old king goes on ruling his city as a city of the conqueror's kingdom.
     */
    public static void subjugate(ServerPlayer king) throws CommandSyntaxException {
        Conquest c = requireOwn(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        City city = data.byTeam(c.cityTeam).orElseThrow(() -> Errors.of("That city no longer exists."));
        Realm atk = Realms.byId(data, c.attackerKingdom).orElseThrow(() -> Errors.of("Your realm no longer exists."));
        requireKingdomWithRoom(atk);
        Kingdom joined = annex(server, data, c, city, atk);
        String ruler = Msg.nameOf(server, city.ruler);
        Msg.toPlayer(server, city.ruler, Msg.text("Your city of " + city.name + " was forced to submit to the Kingdom of "
                + joined.name + ". You go on ruling it as one of its cities.", ChatFormatting.GOLD));
        Msg.ok(king, city.name + " now belongs to your kingdom; " + ruler + " goes on ruling it.");
    }

    /** Former option 2 (asking the ruler to join), kept for old saves and commands. */
    public static void invite(ServerPlayer king) throws CommandSyntaxException {
        Conquest c = requireOwn(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        if (c.state == ConquestState.AWAITING_RULER) throw Errors.of("You are already waiting for the ruler's answer.");
        City city = data.byTeam(c.cityTeam).orElseThrow(() -> Errors.of("That city no longer exists."));
        Realm atk = Realms.byId(data, c.attackerKingdom).orElseThrow(() -> Errors.of("Your realm no longer exists."));
        requireKingdomWithRoom(atk);

        long minutes = Config.CONQUEST_RULER_RESPONSE_MINUTES.get();
        c.state = ConquestState.AWAITING_RULER;
        c.deadline = server.overworld().getGameTime() + minutes * 1200L;
        data.touch();
        Msg.ok(king, "Request sent to " + Msg.nameOf(server, city.ruler) + ". The ruler has " + minutes + " minutes to answer.");

        ServerPlayer ruler = server.getPlayerList().getPlayer(city.ruler);
        if (ruler != null) sendRequest(ruler, c, minutes * 60);
    }

    /** Option 4: leave the city alone. It keeps its ruler and its land and is free (independent) again. */
    public static void release(ServerPlayer king) throws CommandSyntaxException {
        Conquest c = requireOwn(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        if (c.state == ConquestState.AWAITING_RULER) throw Errors.of("You are waiting for the ruler's answer.");
        data.removeConquest(c.cityTeam);
        Looting.forgetWinners(c.cityTeam);
        Msg.broadcast(server, Msg.text("The " + Realms.title(data, c.attackerKingdom, c.attackerName) + " spared the city of "
                + c.cityName + ". It is free again.", ChatFormatting.YELLOW));
        data.byTeam(c.cityTeam).ifPresent(city -> Msg.toPlayer(server, city.ruler,
                Msg.text("Your city was spared and is free again.", ChatFormatting.GREEN)));
    }

    /**
     * Option 3: loot the city. Everyone who won the siege may plunder its land for lootingMinutes (see Looting);
     * then the city is free again, like a released one. Open to the ruler of an independent city too.
     */
    public static void loot(ServerPlayer king) throws CommandSyntaxException {
        Conquest c = requireOwn(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        if (c.state == ConquestState.AWAITING_RULER) throw Errors.of("You are waiting for the ruler's answer.");
        if (Looting.minutes() <= 0) throw Errors.of("Looting is turned off on this server.");
        data.byTeam(c.cityTeam).orElseThrow(() -> Errors.of("That city no longer exists."));
        data.removeConquest(c.cityTeam);
        Msg.broadcast(server, Msg.text("The " + Realms.title(data, c.attackerKingdom, c.attackerName) + " loots the city of "
                + c.cityName + "! It will be free again in " + Looting.minutes() + " minutes.", ChatFormatting.DARK_RED));
        Looting.start(server, c.cityTeam, c.cityName, c.attackerKingdom);
    }

    // ------------------------------------------------------------ the conquered ruler's answer

    private static void sendRequest(ServerPlayer ruler, Conquest c, long secondsLeft) {
        ValorisData data = ValorisData.get(ruler.getServer());
        ruler.sendSystemMessage(Msg.text("The " + Realms.title(data, c.attackerKingdom, c.attackerName) + " conquered "
                + c.cityName + " and asks you to join them (" + SiegeService.formatTime(secondsLeft) + " to answer) ", ChatFormatting.GOLD)
                .append(Msg.button("[Accept]", ChatFormatting.GREEN, "/valoris war conquest accept"))
                .append(Component.literal(" "))
                .append(Msg.button("[Decline]", ChatFormatting.RED, "/valoris war conquest decline")));
        com.valoris.diplomacy.Inbox.send(ruler, true, false);
    }

    private static Conquest requestFor(ServerPlayer ruler) throws CommandSyntaxException {
        City city = CityService.requireRuler(ruler);
        return ValorisData.get(ruler.getServer()).conquestOfCity(city.teamId)
                .filter(c -> c.state == ConquestState.AWAITING_RULER)
                .orElseThrow(() -> Errors.of("There is no pending request for your city."));
    }

    public static void accept(ServerPlayer ruler) throws CommandSyntaxException {
        Conquest c = requestFor(ruler);
        MinecraftServer server = ruler.getServer();
        ValorisData data = ValorisData.get(server);
        City city = CityService.requireRuler(ruler);
        Realm atk = Realms.byId(data, c.attackerKingdom).orElseThrow(() -> Errors.of("They no longer exist."));
        requireKingdomWithRoom(atk);
        Kingdom joined = annex(server, data, c, city, atk);
        Msg.ok(ruler, "Your city joined the Kingdom of " + joined.name + ".");
    }

    public static void decline(ServerPlayer ruler) throws CommandSyntaxException {
        Conquest c = requestFor(ruler);
        MinecraftServer server = ruler.getServer();
        ValorisData data = ValorisData.get(server);
        c.state = ConquestState.CHOOSING;
        c.decideBy = server.overworld().getGameTime() + decisionTicks();
        data.touch();
        Msg.ok(ruler, "You declined.");
        openMenu(server, data, c, Msg.nameOf(server, ruler.getUUID()) + " declined to join you.");
    }

    // ------------------------------------------------------------ internals

    /** The conquered city joins the conqueror; an independent conqueror becomes a kingdom for that. */
    private static Kingdom annex(MinecraftServer server, ValorisData data, Conquest c, City city, Realm atk) {
        data.removeConquest(c.cityTeam);
        Looting.forgetWinners(c.cityTeam);
        Kingdom k = Realms.asKingdom(server, data, atk);
        KingdomService.dropCityDiplomacy(data, city);
        k.cities.add(city.teamId);
        KingdomService.onCityRejoined(server, k, city); // a rebel city taken back: its rebellion is put down
        com.valoris.realm.Heraldry.applyKingdomColor(server, k, city.teamId);
        data.touch();
        Msg.toKingdom(k, Msg.text("The conquered city of " + city.name + " has joined the Kingdom of " + k.name + "!",
                ChatFormatting.GREEN));
        com.valoris.tech.TechService.onCityJoined(server, data, k, city);
        return k;
    }

    /** Housekeeping: drop dead conquests and time out unanswered requests. */
    public static void tick(MinecraftServer server, ValorisData data, long now) {
        for (Conquest c : List.copyOf(data.conquests())) {
            if (data.byTeam(c.cityTeam).isEmpty() || Realms.byId(data, c.attackerKingdom).isEmpty()) {
                data.removeConquest(c.cityTeam); // city or conquering kingdom is gone: the city stays independent
                Looting.forgetWinners(c.cityTeam);
                continue;
            }
            if (c.state == ConquestState.CHOOSING) {
                if (c.decideBy <= 0) {            // saved by an older version: start the decision time now
                    c.decideBy = now + decisionTicks();
                    data.touch();
                } else if (now >= c.decideBy) {   // the conqueror never decided: the city goes free
                    data.removeConquest(c.cityTeam);
                    Looting.forgetWinners(c.cityTeam);
                    Msg.broadcast(server, Msg.text("The " + Realms.title(data, c.attackerKingdom, c.attackerName)
                            + " did not decide the fate of " + c.cityName + " in time. The city is free again.",
                            ChatFormatting.YELLOW));
                }
                continue;
            }
            if (c.state == ConquestState.AWAITING_RULER && now >= c.deadline) {
                c.state = ConquestState.CHOOSING;
                c.decideBy = now + decisionTicks();
                data.touch();
                data.byTeam(c.cityTeam).ifPresent(city -> Msg.toPlayer(server, city.ruler,
                        Msg.text("The request of the " + Realms.title(data, c.attackerKingdom, c.attackerName) + " expired.", ChatFormatting.YELLOW)));
                openMenu(server, data, c, "The ruler did not answer in time.");
            }
        }
    }
}
