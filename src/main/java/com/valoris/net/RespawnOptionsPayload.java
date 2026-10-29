package com.valoris.net;

import java.util.ArrayList;
import java.util.List;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client, after death (or on the very first join, firstJoin = true, while alive): where the player may
 * (re)spawn. secondsLeft of a banner counts from now. noBannersReason: why the banner option is unavailable
 * ("" = there are banners).
 */
public record RespawnOptionsPayload(String deathMessage, String randomArea, boolean inKingdom, String noBannersReason,
                                    List<Banner> banners, boolean firstJoin) implements CustomPacketPayload {

    public record Banner(String city, int x, int y, int z, boolean besieged, int secondsLeft, boolean own) {}

    public static final CustomPacketPayload.Type<RespawnOptionsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "respawn_options"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RespawnOptionsPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.deathMessage());
                buf.writeUtf(p.randomArea());
                buf.writeBoolean(p.inKingdom());
                buf.writeUtf(p.noBannersReason());
                buf.writeVarInt(p.banners().size());
                for (Banner b : p.banners()) {
                    buf.writeUtf(b.city());
                    buf.writeInt(b.x());
                    buf.writeInt(b.y());
                    buf.writeInt(b.z());
                    buf.writeBoolean(b.besieged());
                    buf.writeVarInt(b.secondsLeft());
                    buf.writeBoolean(b.own());
                }
                buf.writeBoolean(p.firstJoin());
            },
            buf -> {
                String death = buf.readUtf();
                String area = buf.readUtf();
                boolean inKingdom = buf.readBoolean();
                String reason = buf.readUtf();
                int n = buf.readVarInt();
                List<Banner> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(new Banner(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(),
                            buf.readVarInt(), buf.readBoolean()));
                }
                return new RespawnOptionsPayload(death, area, inKingdom, reason, list, buf.readBoolean());
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
