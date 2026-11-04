package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: a coup d'etat broke out in the player's realm; choose a side within the seconds given (the
 * window cannot be closed before). side: 1 the ruler, 2 the leader of the coup. locked: the ruler and the leader
 * themselves see the window too, but their side is fixed.
 */
public record CoupPayload(String leader, String ruler, String rulerTitle, String realm, int seconds, int side, boolean locked)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoupPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "coup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CoupPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.leader(), 64);
                buf.writeUtf(p.ruler(), 64);
                buf.writeUtf(p.rulerTitle(), 64);
                buf.writeUtf(p.realm(), 128);
                buf.writeVarInt(p.seconds());
                buf.writeVarInt(p.side());
                buf.writeBoolean(p.locked());
            },
            buf -> new CoupPayload(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readUtf(128), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
