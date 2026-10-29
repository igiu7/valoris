package com.valoris.net;

import net.minecraft.network.FriendlyByteBuf;

/** Generic row of GUI data: three strings and two ints (meaning depends on the list, see StateBuilder). */
public record Row(String a, String b, String c, int n, int m) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(a);
        buf.writeUtf(b);
        buf.writeUtf(c);
        buf.writeVarInt(n);
        buf.writeVarInt(m);
    }

    public static Row read(FriendlyByteBuf buf) {
        return new Row(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }
}
