package com.valoris.kingdom;

import java.util.UUID;

import com.valoris.city.Roles;
import com.valoris.data.ValorisData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Claim protection inside a kingdom: everyone from any of its cities may build, break and use things in every city
 * of the kingdom, like its own residents (see mixin.ChunkTeamDataImplMixin). Machines (fake players) are left out.
 */
public final class KingdomProtection {
    private KingdomProtection() {}

    /** True if the player lives in a city of the same kingdom as the city owning these claims. */
    public static boolean sameKingdom(ServerPlayer player, UUID claimTeam) {
        if (player instanceof FakePlayer) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ValorisData data = ValorisData.get(server);
        return data.kingdomOfCity(claimTeam)
                .map(k -> Roles.cityOfMember(player).map(c -> k.cities.contains(c.teamId)).orElse(false))
                .orElse(false);
    }
}
