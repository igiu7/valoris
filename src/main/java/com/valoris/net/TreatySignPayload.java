package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: sign the Treaty in this hand for a party (0 first, 1 second) with these strokes. */
public record TreatySignPayload(boolean offhand, int slot, String strokes) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TreatySignPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "treaty_sign"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TreatySignPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TreatySignPayload::offhand,
            ByteBufCodecs.VAR_INT, TreatySignPayload::slot,
            ByteBufCodecs.stringUtf8(16384), TreatySignPayload::strokes,
            TreatySignPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
