package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: craft an armory entry at the table at pos. */
public record ArmoryCraftPayload(BlockPos pos, String entryId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArmoryCraftPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "armory_craft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryCraftPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArmoryCraftPayload::pos,
            ByteBufCodecs.STRING_UTF8, ArmoryCraftPayload::entryId,
            ArmoryCraftPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
