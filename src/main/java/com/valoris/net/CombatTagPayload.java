package com.valoris.net;

import com.valoris.valoris2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the player may not log out for this many more milliseconds (0 = free to leave). */
public record CombatTagPayload(long remainingMillis) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CombatTagPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "combat_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatTagPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, CombatTagPayload::remainingMillis, CombatTagPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
