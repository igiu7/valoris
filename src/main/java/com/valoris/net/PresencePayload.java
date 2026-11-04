package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: what the player's Discord Rich Presence shows (client.RichPresence). appId: the Discord
 * application whose name and pictures are shown; image: its art asset key or an https link.
 */
public record PresencePayload(String appId, String image, String details, String state, int online, int max)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PresencePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "presence"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PresencePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PresencePayload::appId,
            ByteBufCodecs.STRING_UTF8, PresencePayload::image,
            ByteBufCodecs.STRING_UTF8, PresencePayload::details,
            ByteBufCodecs.STRING_UTF8, PresencePayload::state,
            ByteBufCodecs.VAR_INT, PresencePayload::online,
            ByteBufCodecs.VAR_INT, PresencePayload::max,
            PresencePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
