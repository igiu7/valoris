package com.valoris.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.ftb.mods.ftbteams.data.PartyTeam;

/** The owner of an FTB party, for two rulers swapping cities after a coup (coup.CoupService). */
@Mixin(value = PartyTeam.class, remap = false)
public interface PartyTeamAccessor {
    @Accessor("owner")
    void valoris$setOwner(UUID owner);
}
