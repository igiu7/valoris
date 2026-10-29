package com.valoris.net;

import java.util.List;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: every request the player can answer right now. popup = open the requests window as soon as
 * no other screen is open (a new request arrived); false = only refresh / open on demand.
 */
public record InboxPayload(List<Entry> entries, boolean popup, boolean open) implements CustomPacketPayload {

    /** kind: icon hint (city, kingdom, alliance, peace, war, conquest). seconds: time left to answer (-1 = none). */
    public record Entry(String id, String kind, String title, String description, String accept, String acceptCommand,
                        String decline, String declineCommand, int seconds) {
        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(kind);
            buf.writeUtf(title);
            buf.writeUtf(description);
            buf.writeUtf(accept);
            buf.writeUtf(acceptCommand);
            buf.writeUtf(decline);
            buf.writeUtf(declineCommand);
            buf.writeVarInt(seconds + 1);
        }

        static Entry read(RegistryFriendlyByteBuf buf) {
            return new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readUtf(), buf.readVarInt() - 1);
        }
    }

    public static final CustomPacketPayload.Type<InboxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "inbox"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InboxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries().size());
                for (Entry e : p.entries()) e.write(buf);
                buf.writeBoolean(p.popup());
                buf.writeBoolean(p.open());
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> list = new java.util.ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(Entry.read(buf));
                return new InboxPayload(list, buf.readBoolean(), buf.readBoolean());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
