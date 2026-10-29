package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: survey this city and send its map. */
public record CityMapRequestPayload(String city) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CityMapRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "city_map_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityMapRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CityMapRequestPayload::city, CityMapRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
