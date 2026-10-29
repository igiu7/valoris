package com.valoris.city;

import java.util.Optional;

import com.valoris.data.ValorisData;
import com.valoris.data.ValorisData.City;
import com.valoris.data.ValorisData.Kingdom;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.server.level.ServerPlayer;

/** Hierarchy: resident -> city ruler -> king. */
public final class Roles {
    public enum Role {
        NONE("Wanderer"), RESIDENT("Resident"), CITY_RULER("City Ruler"), KING("King");

        public final String title;

        Role(String title) {
            this.title = title;
        }
    }

    private Roles() {}

    /** The city whose FTB team the player currently belongs to (as resident or ruler). */
    public static Optional<City> cityOfMember(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).flatMap(t -> data.byTeam(t.getId()));
    }

    public static Optional<Kingdom> kingdomOf(ServerPlayer player) {
        ValorisData data = ValorisData.get(player.getServer());
        return cityOfMember(player).flatMap(c -> data.kingdomOfCity(c.teamId));
    }

    public static Role of(ServerPlayer player) {
        Optional<City> opt = cityOfMember(player);
        if (opt.isEmpty()) return Role.NONE;
        City city = opt.get();
        if (!city.ruler.equals(player.getUUID())) return Role.RESIDENT;
        boolean king = ValorisData.get(player.getServer()).kingdomOfCity(city.teamId)
                .map(k -> k.capital.equals(city.teamId)).orElse(false);
        return king ? Role.KING : Role.CITY_RULER;
    }
}
