package com.valoris.net;

import com.valoris.treaty.TreatyData;
import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: save the Treaty and Quill in this hand as a Treaty. */
public record TreatySavePayload(boolean offhand, String title, String content) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TreatySavePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "treaty_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TreatySavePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TreatySavePayload::offhand,
            ByteBufCodecs.stringUtf8(TreatyData.TITLE_MAX * 4), TreatySavePayload::title,
            ByteBufCodecs.stringUtf8(TreatyData.CONTENT_MAX * 4), TreatySavePayload::content,
            TreatySavePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
