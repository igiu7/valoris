package com.valoris.client.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;

/**
 * Decodes an MP3 on the fly (JLayer) into 16-bit PCM for Minecraft's streaming sound channel, so a track is never
 * held in memory as a whole.
 */
public final class Mp3AudioStream implements AudioStream {
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final AudioFormat format;
    /** Samples decoded from the first frame, waiting to be handed out. */
    private short[] pending;
    private int pendingLength;
    private boolean ended;

    public Mp3AudioStream(InputStream in) throws IOException {
        this.bitstream = new Bitstream(new BufferedInputStream(in, 64 * 1024));
        // decode the first frame to learn sample rate and channels
        if (!decodeFrame()) throw new IOException("Not an MP3 file or empty");
        this.format = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
    }

    /** Decodes the next frame into 'pending'. Returns false at the end of the file. */
    private boolean decodeFrame() throws IOException {
        try {
            while (true) {
                Header header = bitstream.readFrame();
                if (header == null) return false;
                try {
                    SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    pending = out.getBuffer();
                    pendingLength = out.getBufferLength();
                    return true;
                } catch (DecoderException | ArrayIndexOutOfBoundsException bad) {
                    // a damaged frame: skip it
                } finally {
                    bitstream.closeFrame();
                }
            }
        } catch (BitstreamException e) {
            throw new IOException("MP3 read error", e);
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (ended) return null;
        int samples = Math.max(2304, size / 2);
        ByteBuffer buffer = ByteBuffer.allocateDirect(samples * 2 + 4608 * 2).order(ByteOrder.nativeOrder());
        int written = 0;
        while (written < samples) {
            if (pending == null && !decodeFrame()) {
                ended = true;
                break;
            }
            for (int i = 0; i < pendingLength; i++) buffer.putShort(pending[i]);
            written += pendingLength;
            pending = null;
        }
        buffer.flip();
        return buffer.hasRemaining() ? buffer : null;
    }

    @Override
    public void close() throws IOException {
        try {
            bitstream.close();
        } catch (BitstreamException e) {
            throw new IOException(e);
        }
    }
}
