package com.valoris.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.valoris.armory.ArmoryEntry;
import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the whole armory catalog (sent on login and after a reload). */
public record ArmoryCatalogPayload(Map<String, String> eraNames, List<ArmoryEntry> entries,
                                   Map<String, double[]> armorBalance) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArmoryCatalogPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "armory_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryCatalogPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.eraNames().size());
                p.eraNames().forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeUtf(v);
                });
                buf.writeVarInt(p.entries().size());
                for (ArmoryEntry e : p.entries()) e.write(buf);
                buf.writeVarInt(p.armorBalance().size());
                p.armorBalance().forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeVarInt(v.length);
                    for (double d : v) buf.writeDouble(d);
                });
            },
            buf -> {
                int n = buf.readVarInt();
                Map<String, String> eras = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) eras.put(buf.readUtf(), buf.readUtf());
                int m = buf.readVarInt();
                List<ArmoryEntry> list = new ArrayList<>(m);
                for (int i = 0; i < m; i++) list.add(ArmoryEntry.read(buf));
                int a = buf.readVarInt();
                Map<String, double[]> armor = new LinkedHashMap<>();
                for (int i = 0; i < a; i++) {
                    String key = buf.readUtf();
                    double[] v = new double[buf.readVarInt()];
                    for (int j = 0; j < v.length; j++) v[j] = buf.readDouble();
                    armor.put(key, v);
                }
                return new ArmoryCatalogPayload(eras, list, armor);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
