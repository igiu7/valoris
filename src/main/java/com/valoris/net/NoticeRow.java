package com.valoris.net;

import net.minecraft.network.FriendlyByteBuf;

/** One notice on the board. */
public record NoticeRow(String city, String kingdom, String ruler, String title, String body, int secondsLeft,
                        boolean mine, int color) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(city);
        buf.writeUtf(kingdom);
        buf.writeUtf(ruler);
        buf.writeUtf(title);
        buf.writeUtf(body);
        buf.writeVarInt(secondsLeft);
        buf.writeBoolean(mine);
        buf.writeInt(color);
    }

    public static NoticeRow read(FriendlyByteBuf buf) {
        return new NoticeRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readBoolean(), buf.readInt());
    }
}
