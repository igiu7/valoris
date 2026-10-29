package com.valoris.client.music;

import java.util.concurrent.CompletableFuture;

import com.valoris.valoris2;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Plays one MP3 track as music. It uses the sound event valoris2:music.track only so that the sound engine treats
 * it as a streamed music sound; the audio itself comes from the MP3 (NeoForge lets a sound instance supply its stream).
 */
public final class Mp3SoundInstance extends AbstractSoundInstance {
    public static final ResourceLocation EVENT = ResourceLocation.fromNamespaceAndPath(valoris2.MODID, "music.track");

    private final ResourceLocation file;

    /** An empty stream: ends immediately. */
    private static final AudioStream SILENCE = new AudioStream() {
        @Override
        public javax.sound.sampled.AudioFormat getFormat() {
            return new javax.sound.sampled.AudioFormat(44100, 16, 1, true, false);
        }

        @Override
        public java.nio.ByteBuffer read(int size) {
            return null;
        }

        @Override
        public void close() {
        }
    };

    public Mp3SoundInstance(ResourceLocation file) {
        super(EVENT, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.file = file;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    public ResourceLocation file() {
        return file;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file);
                return (AudioStream) new Mp3AudioStream(resource.open());
            } catch (Exception e) {
                // a broken file: play silence so the track ends at once and the next one starts
                valoris2.LOGGER.warn("Could not play music {}", file, e);
                return SILENCE;
            }
        }, Util.backgroundExecutor());
    }
}
