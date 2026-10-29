package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client, every second: what is left of the cold and heat meters (0-1000, 1000 = full) and the climate
 * zone the player stands in (climate.Climate.NONE / COLD / HOT).
 */
public record ClimatePayload(int cold, int heat, int zone) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClimatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "climate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClimatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClimatePayload::cold,
            ByteBufCodecs.VAR_INT, ClimatePayload::heat,
            ByteBufCodecs.VAR_INT, ClimatePayload::zone,
            ClimatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
