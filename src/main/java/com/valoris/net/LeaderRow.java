package com.valoris.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One realm of the leaderboard (a kingdom or an independent city): its name, its leader, its map colour, whether it
 * is the viewer's own realm, and its statistics (see the STAT_ constants).
 */
public record LeaderRow(String name, String leader, boolean kingdom, int color, boolean own, int[] stats) {
    public static final int KILLS = 0, DEATHS = 1, LAND = 2, RESIDENTS = 3, CITIES = 4, TECHS = 5, SIEGES_WON = 6,
            SIEGES_REPELLED = 7, COUNT = 8;

    public int stat(int i) {
        return i < stats.length ? stats[i] : 0;
    }

    /** Kills per death (kills when there are no deaths). */
    public double kd() {
        int d = stat(DEATHS);
        return d == 0 ? stat(KILLS) : stat(KILLS) / (double) d;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(leader);
        buf.writeBoolean(kingdom);
        buf.writeInt(color);
        buf.writeBoolean(own);
        buf.writeVarIntArray(stats);
    }

    public static LeaderRow read(FriendlyByteBuf buf) {
        return new LeaderRow(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readInt(), buf.readBoolean(), buf.readVarIntArray());
    }
}
