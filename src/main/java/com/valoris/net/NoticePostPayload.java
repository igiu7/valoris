package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: post the city's notice (empty title and body = take it down). */
public record NoticePostPayload(String title, String body) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NoticePostPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "notice_post"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoticePostPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), NoticePostPayload::title,
            ByteBufCodecs.stringUtf8(1024), NoticePostPayload::body,
            NoticePostPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
