package com.valoris.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.data.AbstractTeamBase;

/** The members of an FTB team and their ranks, for two rulers swapping cities after a coup (coup.CoupService). */
@Mixin(value = AbstractTeamBase.class, remap = false)
public interface AbstractTeamBaseAccessor {
    @Accessor("ranks")
    Map<UUID, TeamRank> valoris$ranks();
}
