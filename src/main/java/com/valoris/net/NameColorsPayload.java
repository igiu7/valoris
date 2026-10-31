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

/** Server -> client: the realm colour (RGB) of every online player in a city, for the name above the head. */
public record NameColorsPayload(Map<UUID, Integer> colors) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NameColorsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "name_colors"));

    public static final StreamCodec<FriendlyByteBuf, NameColorsPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeMap(p.colors(), (b, id) -> b.writeUUID(id), (b, rgb) -> b.writeInt(rgb)),
            buf -> new NameColorsPayload(buf.readMap(HashMap::new, b -> b.readUUID(), b -> b.readInt())));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
