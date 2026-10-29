package com.valoris.net;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.valoris.valoris2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open the armory screen for the table at pos. */
public record ArmoryOpenPayload(BlockPos pos, List<String> knownTechs, Map<String, String> techNames)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArmoryOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "armory_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryOpenPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeCollection(p.knownTechs(), (b, s) -> b.writeUtf(s));
                buf.writeVarInt(p.techNames().size());
                p.techNames().forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeUtf(v);
                });
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                List<String> known = buf.readList(b -> b.readUtf());
                int n = buf.readVarInt();
                Map<String, String> names = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) names.put(buf.readUtf(), buf.readUtf());
                return new ArmoryOpenPayload(pos, known, names);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
