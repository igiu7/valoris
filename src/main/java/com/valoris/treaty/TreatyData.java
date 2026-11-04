package com.valoris.treaty;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.valoris.valoris2;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a treaty says: its title (also the item's name), its text, who wrote it, and the hand-drawn signatures of the
 * two parties. A signature is the signer's name and the strokes they drew: points of a CANVAS_W x CANVAS_H canvas, two
 * bytes (x, y) each, a stroke ended by (255, 255), stored as Base64.
 */
public record TreatyData(String title, String content, String author, Optional<Signature> first, Optional<Signature> second) {
    public static final int TITLE_MAX = 48, CONTENT_MAX = 1200, CANVAS_W = 192, CANVAS_H = 80, POINTS_MAX = 3000;

    public record Signature(String name, String strokes) {
        public static final Codec<Signature> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Signature::name),
                Codec.STRING.fieldOf("strokes").forGetter(Signature::strokes)).apply(i, Signature::new));
        public static final StreamCodec<ByteBuf, Signature> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Signature::name,
                ByteBufCodecs.stringUtf8(16384), Signature::strokes,
                Signature::new);
    }

    public static final Codec<TreatyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(TreatyData::title),
            Codec.STRING.fieldOf("content").forGetter(TreatyData::content),
            Codec.STRING.optionalFieldOf("author", "").forGetter(TreatyData::author),
            Signature.CODEC.optionalFieldOf("first").forGetter(TreatyData::first),
            Signature.CODEC.optionalFieldOf("second").forGetter(TreatyData::second)).apply(i, TreatyData::new));
    public static final StreamCodec<ByteBuf, TreatyData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(TITLE_MAX * 4), TreatyData::title,
            ByteBufCodecs.stringUtf8(CONTENT_MAX * 4), TreatyData::content,
            ByteBufCodecs.stringUtf8(64), TreatyData::author,
            ByteBufCodecs.optional(Signature.STREAM_CODEC), TreatyData::first,
            ByteBufCodecs.optional(Signature.STREAM_CODEC), TreatyData::second,
            TreatyData::new);

    public Optional<Signature> signature(int slot) {
        return slot == 0 ? first : second;
    }

    public TreatyData withSignature(int slot, Signature s) {
        return slot == 0 ? new TreatyData(title, content, author, Optional.of(s), second)
                : new TreatyData(title, content, author, first, Optional.of(s));
    }

    public boolean fullySigned() {
        return first.isPresent() && second.isPresent();
    }

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, valoris2.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TreatyData>> COMPONENT =
            COMPONENTS.registerComponentType("treaty", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
