package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player looks at this animal. */
public record AnimalInfoRequestPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnimalInfoRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "animal_info_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnimalInfoRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, AnimalInfoRequestPayload::entityId, AnimalInfoRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
