package com.valoris.events;

import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.valoris.data.ValorisData;
import com.valoris.valoris2;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The FTB team of a city changes only through Valoris (or the server console): leaving, kicking and disbanding from
 * anywhere else - the FTB Teams screen, a modified client, another mod - is refused (see PartyTeamMixin). This is what
 * keeps a city, its residents and its technologies from vanishing by accident.
 */
public final class TeamGuard {
    private static int depth;

    private TeamGuard() {}

    @FunctionalInterface public interface Action<T> { T run() throws CommandSyntaxException; }

    /** Runs a Valoris action that may change city teams. */
    public static <T> T allow(Action<T> action) throws CommandSyntaxException {
        depth++;
        try {
            return action.run();
        } finally {
            depth--;
        }
    }

    public static void allowVoid(Action<?> action) throws CommandSyntaxException {
        allow(action);
    }

    /** True if this change of the team may go ahead. */
    public static boolean permits(MinecraftServer server, UUID teamId, CommandSourceStack source) {
        if (depth > 0 || server == null) return true;
        if (ValorisData.get(server).byTeam(teamId).isEmpty()) return true; // not a city
        if (source != null && !(source.getEntity() instanceof ServerPlayer)) return true; // the console
        valoris2.LOGGER.warn("Refused a change of the FTB team of a city that did not come from Valoris (team {})", teamId);
        return false;
    }
}
