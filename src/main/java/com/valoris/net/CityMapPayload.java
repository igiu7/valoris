package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: top-down survey of a city. One cell per block column, row-major (index = z * width + x).
 * cells: the kind of each column, see the constants. ground: terrain height relative to groundBase. height: height of the structure above
 * the ground (walls and buildings). error: non-empty when the map could not be made (then the arrays are empty).
 */
public record CityMapPayload(String city, String kingdom, String error, int originX, int originZ, int width, int depth,
                             byte[] cells, byte[] ground, byte[] height, int groundBase,
                             boolean hasBanner, int bannerX, int bannerZ, int chunks) implements CustomPacketPayload {

    public static final byte OUTSIDE = 0, GROUND = 1, WALL = 2, BUILDING = 3, WATER = 4, PAVED = 5;

    public static final CustomPacketPayload.Type<CityMapPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "city_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityMapPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.city());
                buf.writeUtf(p.kingdom());
                buf.writeUtf(p.error());
                buf.writeInt(p.originX());
                buf.writeInt(p.originZ());
                buf.writeVarInt(p.width());
                buf.writeVarInt(p.depth());
                buf.writeByteArray(p.cells());
                buf.writeByteArray(p.ground());
                buf.writeByteArray(p.height());
                buf.writeInt(p.groundBase());
                buf.writeBoolean(p.hasBanner());
                buf.writeInt(p.bannerX());
                buf.writeInt(p.bannerZ());
                buf.writeVarInt(p.chunks());
            },
            buf -> new CityMapPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readByteArray(), buf.readByteArray(), buf.readByteArray(),
                    buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readVarInt()));

    public static CityMapPayload error(String city, String message) {
        return new CityMapPayload(city, "", message, 0, 0, 0, 0, new byte[0], new byte[0], new byte[0], 0,
                false, 0, 0, 0);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
