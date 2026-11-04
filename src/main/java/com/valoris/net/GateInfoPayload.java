package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: hit points of the gate the player looks at (max 0: not under siege, nothing to show). */
public record GateInfoPayload(BlockPos pos, int left, int max) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GateInfoPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "gate_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GateInfoPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GateInfoPayload::pos,
            ByteBufCodecs.VAR_INT, GateInfoPayload::left,
            ByteBufCodecs.VAR_INT, GateInfoPayload::max,
            GateInfoPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
