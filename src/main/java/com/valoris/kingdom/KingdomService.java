package com.valoris.kingdom;

import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.Config;
import com.valoris.city.CityService;
import com.valoris.city.Roles;
import com.valoris.data.Relation;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.diplomacy.Requests;
import com.valoris.realm.Heraldry;
import com.valoris.diplomacy.Requests.Type;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.util.Names;
import com.valoris.war.SiegeService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Kingdom lifecycle. The king is always the ruler of the capital city. */
public final class KingdomService {
    private KingdomService() {}

    // ------------------------------------------------------------ lookups

    public static Kingdom requireKing(ServerPlayer player) throws CommandSyntaxException {
        if (Roles.of(player) != Roles.Role.KING) throw Errors.of("Only a king can do that.");
        return Roles.kingdomOf(player).orElseThrow(() -> Errors.of("You have no kingdom."));
    }

    public static Optional<UUID> kingOf(ValorisData data, Kingdom k) {
        return data.byTeam(k.capital).map(c -> c.ruler);
    }

    private static Kingdom byName(ValorisData data, String name) throws CommandSyntaxException {
        return data.kingdomByName(name).orElseThrow(() -> Errors.of("No such kingdom."));
    }

    /** Kingdom names may not hide an independent city of the same name in diplomacy. */
    private static void checkNameFree(ValorisData data, String name, City own) throws CommandSyntaxException {
        if (data.cityByName(name).filter(c -> !c.teamId.equals(own.teamId) && data.kingdomOfCity(c.teamId).isEmpty())
                .isPresent()) {
            throw Errors.of("An independent city already uses that name.");
        }
    }

    /**
     * An independent city that joins a kingdom gives up its own diplomacy: it may not be at war or in a siege,
     * and its alliances end.
     */
    /**
     * {@link #checkCanJoin(ValorisData, City)}, and the city's truces must not clash with the kingdom's wars: a city
     * with a truce with some realm cannot join a kingdom at war with that realm (it would be at war with it at once).
     */
    public static void checkCanJoin(ValorisData data, City city, Kingdom k) throws CommandSyntaxException {
        checkCanJoin(data, city);
        for (var t : data.trucesOf(city.teamId).entrySet()) {
            if (data.relation(k.id, t.getKey()).filter(r -> r == Relation.WAR).isPresent()) {
                throw Errors.of(city.name + " has a truce with the " + com.valoris.realm.Realms.title(data, t.getKey(), "?")
                        + " (" + com.valoris.diplomacy.WarService.truceLeft(t.getValue()) + " left), which the "
                        + k.title() + " is at war with.");
            }
        }
    }

    public static void checkCanJoin(ValorisData data, City city) throws CommandSyntaxException {
        com.valoris.war.ConquestService.checkNotConquered(data, city);
        if (data.relationsOf(city.teamId).containsValue(Relation.WAR)) {
            throw Errors.of(city.name + " is at war. It must make peace before joining a kingdom.");
        }
        boolean siege = data.sieges().stream().anyMatch(s -> s.cityTeam.equals(city.teamId)
                || s.isAttacking(city.teamId) || s.defenderKingdom.equals(city.teamId));
        if (siege) throw Errors.of(city.name + " is involved in a siege.");
    }

    /** Forget the diplomacy of a city that became part of a kingdom. */
    public static void dropCityDiplomacy(ValorisData data, City city) {
        data.clearRelations(city.teamId);
        Requests.removeWhere(r -> r.type() == Type.DIPLOMACY && (r.from().equals(city.teamId) || r.to().equals(city.teamId)));
    }

    public static void checkRoom(Kingdom k) throws CommandSyntaxException {
        if (k.cities.size() >= Config.MAX_CITIES_PER_KINGDOM.get()) {
            throw Errors.of("The kingdom is full (" + Config.MAX_CITIES_PER_KINGDOM.get() + " cities).");
        }
    }

    // ------------------------------------------------------------ creation

    /**
     * A kingdom is founded by a city of at least minResidentsForKingdom residents (the ruler included), unless its
     * ruler was allowed by an operator (/valoris admin kingdomfounder).
     */
    public static void checkEnoughResidents(ValorisData data, City city) throws CommandSyntaxException {
        if (data.kingdomFounders.containsKey(city.ruler)) return;
        int needed = Config.MIN_RESIDENTS_FOR_KINGDOM.get();
        int residents = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().getTeamByID(city.teamId)
                .map(t -> t.getMembers().size()).orElse(0);
        if (residents < needed) {
            throw Errors.of("A kingdom can only be founded by a city of at least " + needed + " residents. "
                    + city.name + " has " + residents + ".");
        }
    }

    public static void create(ServerPlayer player, String rawName) throws CommandSyntaxException {
        String name = Names.check(rawName);
        MinecraftServer server = player.getServer();
        City city = CityService.requireFounded(player);
        ValorisData data = ValorisData.get(server);
        if (data.kingdomOfCity(city.teamId).isPresent()) throw Errors.of("Your city already belongs to a kingdom.");
        com.valoris.war.ConquestService.checkNotConquered(data, city);
        checkEnoughResidents(data, city);
        if (data.kingdomByName(name).isPresent()) throw Errors.of("A kingdom with that name already exists.");
        checkNameFree(data, name, city);

        Kingdom k = new Kingdom(UUID.randomUUID(), name, city.teamId);
        data.addKingdom(k);
        // the independent city's treaties, wars and sieges are now the kingdom's
        data.replaceRealm(city.teamId, k.id);
        k.color = Heraldry.teamColor(city.teamId);
        com.valoris.city.ClaimLimitHook.syncLimits(server, city); // capital: larger claim limit
        Msg.broadcast(server, Msg.text("The " + k.title() + " has been proclaimed by "
                + player.getGameProfile().getName() + "!", ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------ king invites a city

    public static void invite(ServerPlayer king, ServerPlayer target) throws CommandSyntaxException {
        Kingdom k = requireKing(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        City city = data.byRuler(target.getUUID()).filter(c -> c.founded)
                .orElseThrow(() -> Errors.of("That player does not rule a founded city."));
        if (data.kingdomOfCity(city.teamId).isPresent()) throw Errors.of("That city already belongs to a kingdom.");
        checkRoom(k);

        Requests.add(server, Type.KINGDOM_INVITE, k.id, city.teamId, null);
        Msg.ok(king, "Invitation sent to " + city.name + ".");
        target.sendSystemMessage(Msg.text(k.rulerTitle + " " + king.getGameProfile().getName() + " invites "
                + city.name + " to join the " + k.title() + " ", ChatFormatting.GOLD)
                .append(Msg.button("[Accept]", ChatFormatting.GREEN, "/valoris city kingdom accept " + Msg.q(k.name)))
                .append(Component.literal(" "))
                .append(Msg.button("[Decline]", ChatFormatting.RED, "/valoris city kingdom decline " + Msg.q(k.name))));
        com.valoris.diplomacy.Inbox.notify(server, target.getUUID());
    }

    /** City ruler accepts the king's invitation. */
    public static void acceptInvite(ServerPlayer ruler, String kingdomName) throws CommandSyntaxException {
        MinecraftServer server = ruler.getServer();
        City city = CityService.requireFounded(ruler);
        ValorisData data = ValorisData.get(server);
        Kingdom k = byName(data, kingdomName);
        Requests.Request r = Requests.find(server, Type.KINGDOM_INVITE, k.id, city.teamId)
                .orElseThrow(() -> Errors.of("You have no (valid) invitation from that kingdom."));
        if (data.kingdomOfCity(city.teamId).isPresent()) throw Errors.of("Your city already belongs to a kingdom.");
        checkRoom(k);
        checkCanJoin(data, city, k);
        addCity(server, data, k, city);
        Requests.remove(r);
    }

    public static void declineInvite(ServerPlayer ruler, String kingdomName) throws CommandSyntaxException {
        MinecraftServer server = ruler.getServer();
        City city = CityService.requireFounded(ruler);
        ValorisData data = ValorisData.get(server);
        Kingdom k = byName(data, kingdomName);
        Requests.find(server, Type.KINGDOM_INVITE, k.id, city.teamId).ifPresent(Requests::remove);
        Msg.ok(ruler, "Invitation declined.");
        kingOf(data, k).ifPresent(id -> Msg.toPlayer(server, id,
                Msg.text(city.name + " declined your invitation.", ChatFormatting.RED)));
    }

    // ------------------------------------------------------------ city asks to join

    public static void requestJoin(ServerPlayer ruler, String kingdomName) throws CommandSyntaxException {
        MinecraftServer server = ruler.getServer();
        City city = CityService.requireFounded(ruler);
        ValorisData data = ValorisData.get(server);
        if (data.kingdomOfCity(city.teamId).isPresent()) throw Errors.of("Your city already belongs to a kingdom.");
        Kingdom k = byName(data, kingdomName);
        checkRoom(k);
        checkCanJoin(data, city, k);

        Requests.add(server, Type.KINGDOM_JOIN, city.teamId, k.id, null);
        Msg.ok(ruler, "Your request was sent to the ruler of the " + k.title() + ".");
        kingOf(data, k).ifPresent(id -> Msg.toPlayer(server, id,
                Msg.text("The city of " + city.name + " asks to join your kingdom ", ChatFormatting.GOLD)
                        .append(Msg.button("[Accept]", ChatFormatting.GREEN, "/valoris kingdom accept " + Msg.q(city.name)))
                        .append(Component.literal(" "))
                        .append(Msg.button("[Decline]", ChatFormatting.RED, "/valoris kingdom decline " + Msg.q(city.name)))));
        com.valoris.diplomacy.Inbox.notifyKing(server, k);
    }

    public static void acceptRequest(ServerPlayer king, String cityName) throws CommandSyntaxException {
        Kingdom k = requireKing(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        City city = data.cityByName(cityName).orElseThrow(() -> Errors.of("No such city."));
        Requests.Request r = Requests.find(server, Type.KINGDOM_JOIN, city.teamId, k.id)
                .orElseThrow(() -> Errors.of("That city has no (valid) request to your kingdom."));
        if (data.kingdomOfCity(city.teamId).isPresent()) throw Errors.of("That city already belongs to a kingdom.");
        checkRoom(k);
        checkCanJoin(data, city, k);
        addCity(server, data, k, city);
        Requests.remove(r);
    }

    public static void declineRequest(ServerPlayer king, String cityName) throws CommandSyntaxException {
        Kingdom k = requireKing(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        City city = data.cityByName(cityName).orElseThrow(() -> Errors.of("No such city."));
        Requests.find(server, Type.KINGDOM_JOIN, city.teamId, k.id).ifPresent(Requests::remove);
        Msg.ok(king, "Request declined.");
        Msg.toPlayer(server, city.ruler, Msg.text("The " + k.title() + " declined your request.", ChatFormatting.RED));
    }

    private static void addCity(MinecraftServer server, ValorisData data, Kingdom k, City city) {
        data.copyTruces(city.teamId, k.id); // the kingdom keeps the city's truces: joining is no way around them
        dropCityDiplomacy(data, city);
        k.cities.add(city.teamId);
        onCityRejoined(server, k, city);
        Heraldry.applyKingdomColor(server, k, city.teamId);
        data.touch();
        Msg.toKingdom(k, Msg.text("The city of " + city.name + " has joined the " + k.title() + "!", ChatFormatting.GREEN));
        com.valoris.tech.TechService.onCityJoined(server, data, k, city);
    }

    // ------------------------------------------------------------ leaving / expelling

    /**
     * A city rebels against its kingdom: it leaves and becomes an independent city, and a rebellion (a war of the
     * city against the kingdom) begins. This is the only way for a city to leave on its own - otherwise only the
     * king can release it (expel), or another kingdom conquers it. A city whose rebellion was put down (it is back
     * in that kingdom) may not rebel again for a while (config rebelCooldownHours).
     */
    public static void rebel(ServerPlayer ruler) throws CommandSyntaxException {
        MinecraftServer server = ruler.getServer();
        City city = CityService.requireFounded(ruler);
        ValorisData data = ValorisData.get(server);
        Kingdom old = data.kingdomOfCity(city.teamId).orElseThrow(() -> Errors.of("Your city is not in a kingdom."));
        if (old.capital.equals(city.teamId)) throw Errors.of("You rule this kingdom. A king cannot rebel against himself.");
        if (SiegeService.isUnderSiege(data, city.teamId)) throw Errors.of("Your city is under siege.");
        long now = System.currentTimeMillis();
        if (city.rebelCooldownUntil > now) {
            throw Errors.of("Your last rebellion was put down. " + city.name + " can rise again in "
                    + com.valoris.war.SiegeService.formatTime((city.rebelCooldownUntil - now) / 1000) + ".");
        }

        old.cities.remove(city.teamId);
        city.rebelledFrom = old.id;
        data.copyTruces(old.id, city.teamId); // the rebels stay bound by the truces they made as part of the kingdom
        Requests.removeWhere(r -> r.from().equals(city.teamId) || r.to().equals(city.teamId));
        data.touch();

        Msg.broadcast(server, Msg.text("REBELLION! The city of " + city.name + " has risen against the "
                + old.title() + " and declared its independence.", ChatFormatting.DARK_RED));
        // the rebels attack: the old kingdom's allies come to its defence
        var rebelRealm = com.valoris.realm.Realms.byId(data, city.teamId);
        var oldRealm = com.valoris.realm.Realms.byId(data, old.id);
        if (rebelRealm.isPresent() && oldRealm.isPresent()) {
            ValorisData.War war = com.valoris.diplomacy.WarService.declare(server, data, rebelRealm.get(), oldRealm.get());
            war.rebellion = true;
            war.log("The city of " + city.name + " rebelled against the " + old.title() + ".");
            data.touch();
        }
    }

    /**
     * A city joined a kingdom. If it is back in the kingdom it had rebelled against, the rebellion was put down:
     * the city may not rebel again for a while.
     */
    public static void onCityRejoined(MinecraftServer server, Kingdom k, City city) {
        if (k.id.equals(city.rebelledFrom)) {
            long hours = Config.REBEL_COOLDOWN_HOURS.get();
            city.rebelCooldownUntil = System.currentTimeMillis() + hours * 3_600_000L;
            Msg.toKingdom(k, Msg.text("The rebellion of " + city.name + " has been put down."
                    + (hours > 0 ? " The city cannot rebel again for " + hours + " hours." : ""), ChatFormatting.GOLD));
        }
        city.rebelledFrom = null;
    }

    public static void expel(ServerPlayer king, String cityName) throws CommandSyntaxException {
        Kingdom k = requireKing(king);
        ValorisData data = ValorisData.get(king.getServer());
        City city = data.cityByName(cityName).filter(c -> k.cities.contains(c.teamId))
                .orElseThrow(() -> Errors.of("That city is not in your kingdom."));
        if (city.teamId.equals(k.capital)) throw Errors.of("You cannot expel your own capital.");
        if (SiegeService.isUnderSiege(data, city.teamId)) throw Errors.of("That city is under siege.");
        Msg.toKingdom(k, Msg.text("The city of " + city.name + " was expelled from the " + k.title() + ".", ChatFormatting.RED));
        k.cities.remove(city.teamId);
        data.copyTruces(k.id, city.teamId); // no expelling a city to attack a realm the kingdom has a truce with
        data.touch();
    }

    // ------------------------------------------------------------ crown / disband

    /** Moves the capital (and with it the crown) to another city of the kingdom. */
    public static void crown(ServerPlayer king, ServerPlayer target) throws CommandSyntaxException {
        Kingdom k = requireKing(king);
        ValorisData data = ValorisData.get(king.getServer());
        City city = data.byRuler(target.getUUID()).filter(c -> k.cities.contains(c.teamId))
                .orElseThrow(() -> Errors.of("That player does not rule a city of your kingdom."));
        if (city.teamId.equals(k.capital)) throw Errors.of("You already hold the crown.");
        UUID oldCapital = k.capital;
        data.byTeam(oldCapital).ifPresent(old -> com.valoris.tech.TechService.moveResearch(data, old, city));
        k.capital = city.teamId;
        data.touch();
        data.byTeam(oldCapital).ifPresent(old -> com.valoris.city.ClaimLimitHook.syncLimits(king.getServer(), old));
        com.valoris.city.ClaimLimitHook.syncLimits(king.getServer(), city);
        Msg.toKingdom(k, Msg.text(target.getGameProfile().getName() + " of " + city.name
                + " is now the " + k.rulerTitle + " of the " + k.title() + ". The capital moves to " + city.name + ".", ChatFormatting.GOLD));
    }

    public static void disband(ServerPlayer king) throws CommandSyntaxException {
        Kingdom k = requireKing(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        // dissolving would end every war and call off every siege of the kingdom at once
        if (!data.warsOf(k.id).isEmpty()) throw Errors.of("Your kingdom is at war. Make peace before dissolving it.");
        boolean siege = data.sieges().stream().anyMatch(s -> s.isAttacking(k.id) || s.defenderKingdom.equals(k.id));
        if (siege) throw Errors.of("Your kingdom is involved in a siege.");
        if (com.valoris.war.Looting.involves(k.id)) throw Errors.of("Your kingdom is looting a city. Wait until the looting time is over.");
        Msg.broadcast(server, Msg.text("The " + k.title() + " has been dissolved by its " + k.rulerTitle + ".", ChatFormatting.GOLD));
        dissolve(ValorisData.get(server), k);
    }

    /** Removes the kingdom and all its relations. */
    public static void dissolve(ValorisData data, Kingdom k) {
        Msg.toKingdom(k, Msg.text("The " + k.title() + " no longer exists.", ChatFormatting.RED));
        // its cities become free, and each keeps the kingdom's truces
        for (UUID city : k.cities) data.copyTruces(k.id, city);
        com.valoris.data.DataVault.archiveKingdom(k, data);
        data.removeKingdom(k.id);
        // the former capital loses its larger claim limit
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) data.byTeam(k.capital).ifPresent(c -> com.valoris.city.ClaimLimitHook.syncLimits(server, c));
        Requests.removeWhere(r -> r.from().equals(k.id) || r.to().equals(k.id));
    }

    /** A city vanished (team deleted). Capital lost = kingdom falls; otherwise just drop the city. */
    public static void detachCity(MinecraftServer server, ValorisData data, Kingdom k, City city) {
        if (k.capital.equals(city.teamId)) {
            Msg.broadcast(server, Msg.text("The capital " + city.name + " has fallen; the "
                    + k.title() + " is no more.", ChatFormatting.DARK_RED));
            dissolve(data, k);
        } else {
            k.cities.remove(city.teamId);
            data.touch();
            Msg.toKingdom(k, Msg.text("The city of " + city.name + " is no longer part of the kingdom.", ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------ info

    public static void info(ServerPlayer player, Kingdom k) {
        MinecraftServer server = player.getServer();
        ValorisData data = ValorisData.get(server);
        String king = kingOf(data, k).map(id -> Msg.nameOf(server, id)).orElse("unknown");
        StringBuilder sb = new StringBuilder(k.title() + "\n" + k.rulerTitle + ": " + king + "\nCities (" + k.cities.size() + "):");
        for (UUID id : k.cities) {
            data.byTeam(id).ifPresent(c -> sb.append("\n - ").append(c.name)
                    .append(id.equals(k.capital) ? " (capital)" : "")
                    .append(" - ruler ").append(Msg.nameOf(server, c.ruler)));
        }
        Msg.info(player, sb.toString());
    }

    public static void list(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        if (data.kingdoms().isEmpty()) {
            Msg.info(player, "There are no kingdoms yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("Kingdoms:");
        for (Kingdom k : data.kingdoms()) {
            sb.append("\n - ").append(k.name).append(" (").append(k.cities.size()).append(" cities)");
        }
        Msg.info(player, sb.toString());
    }
}
