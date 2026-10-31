package com.valoris.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.valoris.valoris2;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: the realm colour (RGB) of every online player in a city, for the name above the head, and the
 * title of the realm (kingdom or independent city) of every online player in one, for the grouped tab list.
 */
public record NameColorsPayload(Map<UUID, Integer> colors, Map<UUID, String> realms) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NameColorsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "name_colors"));

    public static final StreamCodec<FriendlyByteBuf, NameColorsPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeMap(p.colors(), (b, id) -> b.writeUUID(id), (b, rgb) -> b.writeInt(rgb));
                buf.writeMap(p.realms(), (b, id) -> b.writeUUID(id), (b, title) -> b.writeUtf(title));
            },
            buf -> new NameColorsPayload(buf.readMap(HashMap::new, b -> b.readUUID(), b -> b.readInt()),
                    buf.readMap(HashMap::new, b -> b.readUUID(), b -> b.readUtf())));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
