package com.valoris.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Statistics of one war, seen from the viewer's side ("our" = the viewer's side).
 * logTimes / logTexts: the chronicle, oldest first.
 */
public record WarInfo(String warId, long started, int ourKills, int theirKills, int ourTaken, int theirTaken,
                      int ourRepelled, int theirRepelled, List<Long> logTimes, List<String> logTexts) {

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(warId);
        buf.writeLong(started);
        buf.writeVarInt(ourKills);
        buf.writeVarInt(theirKills);
        buf.writeVarInt(ourTaken);
        buf.writeVarInt(theirTaken);
        buf.writeVarInt(ourRepelled);
        buf.writeVarInt(theirRepelled);
        buf.writeVarInt(logTexts.size());
        for (int i = 0; i < logTexts.size(); i++) {
            buf.writeLong(logTimes.get(i));
            buf.writeUtf(logTexts.get(i));
        }
    }

    public static WarInfo read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        long started = buf.readLong();
        int ok = buf.readVarInt(), tk = buf.readVarInt(), ot = buf.readVarInt(), tt = buf.readVarInt();
        int orp = buf.readVarInt(), trp = buf.readVarInt();
        int n = buf.readVarInt();
        List<Long> times = new ArrayList<>(n);
        List<String> texts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            times.add(buf.readLong());
            texts.add(buf.readUtf());
        }
        return new WarInfo(id, started, ok, tk, ot, tt, orp, trp, times, texts);
    }
}
