package com.valoris.net;

import java.util.HashMap;
import java.util.Map;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the era weapon damage table, so tooltips match the damage the server applies. */
public record WeaponBalancePayload(double referenceBase, Map<String, Double> materials, Map<String, Double> types,
                                   Map<String, String> itemMaterials)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WeaponBalancePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "weapon_balance"));

    private static void writeMap(RegistryFriendlyByteBuf buf, Map<String, Double> map) {
        buf.writeVarInt(map.size());
        map.forEach((k, v) -> {
            buf.writeUtf(k);
            buf.writeDouble(v);
        });
    }

    private static Map<String, Double> readMap(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Double> map = new HashMap<>();
        for (int i = 0; i < n; i++) map.put(buf.readUtf(), buf.readDouble());
        return map;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponBalancePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.referenceBase());
                writeMap(buf, p.materials());
                writeMap(buf, p.types());
                buf.writeVarInt(p.itemMaterials().size());
                p.itemMaterials().forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeUtf(v);
                });
            },
            buf -> {
                double ref = buf.readDouble();
                Map<String, Double> mats = readMap(buf);
                Map<String, Double> types = readMap(buf);
                int n = buf.readVarInt();
                Map<String, String> perItem = new HashMap<>();
                for (int i = 0; i < n; i++) perItem.put(buf.readUtf(), buf.readUtf());
                return new WeaponBalancePayload(ref, mats, types, perItem);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
