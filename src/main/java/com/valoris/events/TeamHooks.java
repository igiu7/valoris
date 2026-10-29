package com.valoris.events;

import com.valoris.city.CityService;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;

/** Keeps cities and kingdoms consistent with FTB Teams (deleted teams, ownership changes). */
public final class TeamHooks {
    private TeamHooks() {}

    public static void init() {
        TeamEvent.DELETED.register(e -> {
            var server = FTBTeamsAPI.api().getManager().getServer();
            CityService.onTeamDeleted(server, e.getTeam().getId());
        });
        TeamEvent.OWNERSHIP_TRANSFERRED.register(e -> {
            var server = FTBTeamsAPI.api().getManager().getServer();
            CityService.onOwnerChanged(server, e.getTeam());
        });
        // the city's own member list follows every real join and leave (see DataVault)
        TeamEvent.PLAYER_JOINED_PARTY.register(e -> com.valoris.data.DataVault.onJoinedParty(
                FTBTeamsAPI.api().getManager().getServer(), e.getTeam().getId(), e.getPlayer().getUUID()));
        TeamEvent.PLAYER_LEFT_PARTY.register(e -> com.valoris.data.DataVault.onLeftParty(
                FTBTeamsAPI.api().getManager().getServer(), e.getTeam().getId(), e.getPlayerId()));
    }
}
