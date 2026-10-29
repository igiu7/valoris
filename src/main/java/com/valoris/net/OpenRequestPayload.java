package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: send me the GUI state. open = true opens the GUI, false only refreshes it. */
public record OpenRequestPayload(boolean open) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "open_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, OpenRequestPayload::open, OpenRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
