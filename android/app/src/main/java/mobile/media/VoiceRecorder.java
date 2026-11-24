package mobile.media;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.annotation.RequiresPermission;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Records a voice message from the microphone as a WAV file: 16 kHz, 16-bit, mono, exactly like
 * the desktop's AudioRecorder, so phones and PCs can play each other's voice notes.
 * <p>
 * Threads: {@link AudioRecord#read} blocks, so a dedicated "voice-recorder" thread copies the
 * microphone samples into memory while the UI thread stays free.
 */
public final class VoiceRecorder {

    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 1;
    private static final int BITS = 16;

    private AudioRecord record;
    private Thread thread;
    private ByteArrayOutputStream pcm;
    private volatile boolean recording;
    private volatile int peak;
    private long startedAt;

    /** Below this loudness (out of 32767) the microphone heard nothing but electronic noise. */
    public static final int SILENCE_PEAK = 200;

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void start() throws IOException {
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            throw new IOException("This device can't record 16 kHz audio.");
        }
        AudioRecord r = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, 8192) * 2);
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            throw new IOException("The microphone is not available.");
        }
        record = r;
        pcm = new ByteArrayOutputStream();
        r.startRecording();
        recording = true;
        peak = 0;
        startedAt = System.currentTimeMillis();
        ByteArrayOutputStream out = pcm;
        thread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (recording) {
                int n = r.read(buffer, 0, buffer.length);
                if (n > 0) {
                    synchronized (out) {
                        out.write(buffer, 0, n);
                    }
                    trackPeak(buffer, n);
                }
            }
        }, "voice-recorder");
        thread.start();
    }

    public boolean isRecording() {
        return recording;
    }

    public long elapsedMillis() {
        return recording ? System.currentTimeMillis() - startedAt : 0;
    }

    /** Loudest sample of the current/last recording (0..32767). */
    public int peak() {
        return peak;
    }

    /** Samples are 16-bit little-endian. Only the recorder thread writes this. */
    private void trackPeak(byte[] buffer, int length) {
        int max = peak;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = Math.abs((short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8)));
            if (sample > max) {
                max = sample;
            }
        }
        peak = max;
    }

    /** Stops and returns the recording as a complete WAV file. */
    public byte[] stop() {
        byte[] samples = finish();
        return wav(samples);
    }

    /** Stops and throws the recording away. */
    public void cancel() {
        finish();
    }

    private byte[] finish() {
        if (record == null) {
            return new byte[0];
        }
        recording = false;
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            record.stop();
        } catch (IllegalStateException ignored) {
            // never really started
        }
        record.release();
        record = null;
        synchronized (pcm) {
            return pcm.toByteArray();
        }
    }

    /** 44-byte RIFF/WAVE header + the raw samples (little-endian). */
    private static byte[] wav(byte[] samples) {
        int byteRate = SAMPLE_RATE * CHANNELS * BITS / 8;
        byte[] out = new byte[44 + samples.length];
        writeString(out, 0, "RIFF");
        writeInt(out, 4, 36 + samples.length);
        writeString(out, 8, "WAVE");
        writeString(out, 12, "fmt ");
        writeInt(out, 16, 16);                         // fmt chunk size
        writeShort(out, 20, 1);                        // PCM
        writeShort(out, 22, CHANNELS);
        writeInt(out, 24, SAMPLE_RATE);
        writeInt(out, 28, byteRate);
        writeShort(out, 32, CHANNELS * BITS / 8);      // block align
        writeShort(out, 34, BITS);
        writeString(out, 36, "data");
        writeInt(out, 40, samples.length);
        System.arraycopy(samples, 0, out, 44, samples.length);
        return out;
    }

    private static void writeString(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[at + i] = (byte) s.charAt(i);
        }
    }

    private static void writeInt(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    private static void writeShort(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }
}
