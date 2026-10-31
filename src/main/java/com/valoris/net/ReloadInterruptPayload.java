package com.valoris.net;

import com.valoris.valoris2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the player's gun reload was interrupted (compat.GunReloadInterrupt), stop its animation. */
public record ReloadInterruptPayload() implements CustomPacketPayload {
    public static final ReloadInterruptPayload INSTANCE = new ReloadInterruptPayload();
    public static final CustomPacketPayload.Type<ReloadInterruptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "reload_interrupt"));

    public static final StreamCodec<ByteBuf, ReloadInterruptPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
