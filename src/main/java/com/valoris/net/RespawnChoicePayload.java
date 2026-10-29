package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: respawn at this city's banner ("" = a random place; "?" = just send the options again). */
public record RespawnChoicePayload(String city) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RespawnChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "respawn_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RespawnChoicePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RespawnChoicePayload::city, RespawnChoicePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
