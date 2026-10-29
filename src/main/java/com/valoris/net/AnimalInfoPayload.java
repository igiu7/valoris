package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: breeding state of the animal the player looks at (state: see mobs.Breeding; stewSeconds: -1 unless a grown mooshroom). */
public record AnimalInfoPayload(int entityId, int state, int seconds, int stewSeconds) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnimalInfoPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "animal_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnimalInfoPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnimalInfoPayload::entityId,
            ByteBufCodecs.VAR_INT, AnimalInfoPayload::state,
            ByteBufCodecs.VAR_INT, AnimalInfoPayload::seconds,
            ByteBufCodecs.INT, AnimalInfoPayload::stewSeconds,
            AnimalInfoPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static String time(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + String.format("%02d", (seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + String.format("%02d", seconds % 60) + "s";
        return Math.max(0, seconds) + "s";
    }
}
