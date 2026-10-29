package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the keys of a player driving a Trotting Wagons wagon (see compat.WagonDrive). */
public record WagonInputPayload(int vehicle, float forward, float strafe) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WagonInputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "wagon_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WagonInputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WagonInputPayload::vehicle,
            ByteBufCodecs.FLOAT, WagonInputPayload::forward,
            ByteBufCodecs.FLOAT, WagonInputPayload::strafe,
            WagonInputPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
