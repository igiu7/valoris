package com.valoris.realm;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.city.CityService;
import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Conquest;
import com.valoris.data.ValorisData.Kingdom;
import com.valoris.data.ValorisData.Siege;
import com.valoris.kingdom.KingdomService;
import com.valoris.util.Errors;
import com.valoris.util.Msg;
import com.valoris.util.Names;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Names and map colours. A city's colour on the FTB Chunks map is its FTB team colour; every city of a kingdom
 * wears the kingdom's colour, an independent city chooses its own.
 */
public final class Heraldry {
    private Heraldry() {}

    // ------------------------------------------------------------------ team properties

    /** Sets a team property the way /ftbteams settings does, so FTB Chunks and every client update their maps. */
    private static <T> void setProperty(MinecraftServer server, Team team, TeamProperty<T> property, T value) {
        TeamPropertyCollection old = team.getProperties().copy();
        team.setProperty(property, value);
        team.markDirty();
        TeamEvent.PROPERTIES_CHANGED.invoker().accept(new TeamPropertiesChangedEvent(team, old));
        team.syncOnePropertyToAll(server, property, value);
    }

    public static void setTeamColor(MinecraftServer server, UUID teamId, int rgb) {
        FTBTeamsAPI.api().getManager().getTeamByID(teamId)
                .ifPresent(team -> setProperty(server, team, TeamProperties.COLOR, Color4I.rgb(rgb & 0xFFFFFF)));
    }

    public static int teamColor(UUID teamId) {
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId)
                .map(t -> t.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF).orElse(0xB02E26);
    }

    /** Colour of a kingdom; set from its capital the first time it is needed. */
    public static int kingdomColor(Kingdom k) {
        if (k.color < 0) k.color = teamColor(k.capital);
        return k.color;
    }

    /** A city joined a kingdom: it takes the kingdom's colour. */
    public static void applyKingdomColor(MinecraftServer server, Kingdom k, UUID cityTeam) {
        setTeamColor(server, cityTeam, kingdomColor(k));
    }

    public static int parseColor(String raw) throws CommandSyntaxException {
        String hex = raw.trim().toLowerCase(Locale.ROOT);
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.startsWith("0x")) hex = hex.substring(2);
        if (!hex.matches("[0-9a-f]{6}")) throw Errors.of("Use a colour like ff8800 (red, green, blue in hex).");
        return Integer.parseInt(hex, 16);
    }

    // ------------------------------------------------------------------ colours

    /** The ruler of an independent city picks its colour. */
    public static void cityColor(ServerPlayer ruler, String raw) throws CommandSyntaxException {
        int rgb = parseColor(raw);
        City city = CityService.requireRuler(ruler);
        ValorisData data = ValorisData.get(ruler.getServer());
        if (data.kingdomOfCity(city.teamId).isPresent()) {
            throw Errors.of("Your city wears the colour of its kingdom. The king chooses it.");
        }
        setTeamColor(ruler.getServer(), city.teamId, rgb);
        Msg.ok(ruler, "The colour of " + city.name + " has been changed.");
    }

    /** The king picks the colour of the whole kingdom. */
    public static void kingdomColor(ServerPlayer king, String raw) throws CommandSyntaxException {
        int rgb = parseColor(raw);
        Kingdom k = KingdomService.requireKing(king);
        ValorisData data = ValorisData.get(king.getServer());
        k.color = rgb;
        data.touch();
        for (UUID city : k.cities) setTeamColor(king.getServer(), city, rgb);
        Msg.toKingdom(k, Msg.text("The Kingdom of " + k.name + " has a new colour.", ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ names

    public static void renameCity(ServerPlayer ruler, String rawName) throws CommandSyntaxException {
        String name = Names.check(rawName);
        City city = CityService.requireRuler(ruler);
        MinecraftServer server = ruler.getServer();
        ValorisData data = ValorisData.get(server);
        if (city.name.equals(name)) throw Errors.of("Your city already has that name.");
        Optional<City> other = data.cityByName(name);
        if (other.isPresent() && !other.get().teamId.equals(city.teamId)) throw Errors.of("A city with that name already exists.");
        Optional<Kingdom> sameNameKingdom = data.kingdomByName(name);
        boolean ownKingdom = sameNameKingdom.map(k -> k.cities.contains(city.teamId)).orElse(false);
        if (sameNameKingdom.isPresent() && !ownKingdom) throw Errors.of("A kingdom with that name already exists.");

        String old = city.name;
        city.name = name;
        for (Siege s : data.sieges()) if (s.cityTeam.equals(city.teamId)) s.cityName = name;
        for (Conquest c : data.conquests()) if (c.cityTeam.equals(city.teamId)) c.cityName = name;
        data.touch();
        FTBTeamsAPI.api().getManager().getTeamByID(city.teamId)
                .ifPresent(team -> setProperty(server, team, TeamProperties.DISPLAY_NAME, name));
        Msg.broadcast(server, Msg.text("The city of " + old + " is now called " + name + ".", ChatFormatting.GOLD));
    }

    public static void renameKingdom(ServerPlayer king, String rawName) throws CommandSyntaxException {
        String name = Names.check(rawName);
        Kingdom k = KingdomService.requireKing(king);
        MinecraftServer server = king.getServer();
        ValorisData data = ValorisData.get(server);
        if (k.name.equals(name)) throw Errors.of("Your kingdom already has that name.");
        if (data.kingdomByName(name).filter(o -> !o.id.equals(k.id)).isPresent()) {
            throw Errors.of("A kingdom with that name already exists.");
        }
        if (data.cityByName(name).filter(c -> data.kingdomOfCity(c.teamId).isEmpty()).isPresent()) {
            throw Errors.of("An independent city already uses that name.");
        }
        String old = k.name;
        k.name = name;
        for (Siege s : data.sieges()) {
            if (s.attackerKingdom.equals(k.id)) s.attackerName = name;
            if (s.defenderKingdom.equals(k.id)) s.defenderName = name;
        }
        for (Conquest c : data.conquests()) if (c.attackerKingdom.equals(k.id)) c.attackerName = name;
        data.touch();
        Msg.broadcast(server, Msg.text("The Kingdom of " + old + " is now the Kingdom of " + name + ".", ChatFormatting.GOLD));
    }
}
