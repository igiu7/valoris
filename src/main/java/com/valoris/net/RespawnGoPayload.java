package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the choice was accepted, send the respawn request now. */
public record RespawnGoPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RespawnGoPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "respawn_go"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RespawnGoPayload> CODEC = StreamCodec.unit(new RespawnGoPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
