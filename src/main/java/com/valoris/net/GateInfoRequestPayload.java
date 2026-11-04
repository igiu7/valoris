package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player looks at this gate block. */
public record GateInfoRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GateInfoRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "gate_info_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GateInfoRequestPayload> CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, GateInfoRequestPayload::pos, GateInfoRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
