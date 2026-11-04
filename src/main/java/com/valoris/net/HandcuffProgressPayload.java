package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: handcuffing in progress (label, ticks done of total ticks; total 0: nothing to show). */
public record HandcuffProgressPayload(String label, int done, int total) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HandcuffProgressPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "handcuff_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandcuffProgressPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HandcuffProgressPayload::label,
            ByteBufCodecs.VAR_INT, HandcuffProgressPayload::done,
            ByteBufCodecs.VAR_INT, HandcuffProgressPayload::total,
            HandcuffProgressPayload::new);

    public static final HandcuffProgressPayload NONE = new HandcuffProgressPayload("", 0, 0);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
