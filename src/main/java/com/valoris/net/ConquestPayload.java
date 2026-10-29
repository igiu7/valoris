package com.valoris.net;

import java.util.List;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: opens the conquest decision menu for the conquering king (or ruler of an independent city).
 * residents: Row(name, uuid, who they are, 1 = the conqueror's own resident / 0 = of the conquered city, 0).
 * kingdom: the conqueror has a kingdom; only then can the city be added to it (appoint a ruler, ask the ruler).
 */
public record ConquestPayload(String city, String attacker, String ruler, boolean awaiting, boolean full,
                              boolean kingdom, String note, List<Row> residents) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ConquestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "conquest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConquestPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.city());
                buf.writeUtf(p.attacker());
                buf.writeUtf(p.ruler());
                buf.writeBoolean(p.awaiting());
                buf.writeBoolean(p.full());
                buf.writeBoolean(p.kingdom());
                buf.writeUtf(p.note());
                buf.writeCollection(p.residents(), (b, r) -> r.write(b));
            },
            buf -> new ConquestPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readList(Row::read)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
