package client.media;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Records voice notes from the microphone on a background thread and returns them as WAV bytes.
 * <p>
 * Uses Java's built-in sound API (javax.sound). The microphone delivers raw "PCM" samples
 * (numbers describing the sound wave); at the end we wrap them in a WAV file header so any
 * player, including the Android app, can play them.
 */
public class AudioRecorder {

    /**
     * 16,000 samples per second, 16 bits per sample, 1 channel (mono), signed, little-endian.
     * Good quality for speech and small files (about 32 KB per second). Android records the same format.
     */
    private static final AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

    private TargetDataLine line;          // the microphone input
    private ByteArrayOutputStream buffer; // the recorded samples pile up here
    private Thread worker;                // the "audio-recorder" thread
    private volatile boolean recording;   // volatile: written by the UI thread, read by the worker

    /** Opens the microphone and starts recording on a background thread. */
    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No microphone was found.");
        }
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();
        buffer = new ByteArrayOutputStream();
        recording = true;

        // line.read() blocks until sound arrives, so it must not run on the UI thread.
        worker = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (recording) {
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) {
                    buffer.write(chunk, 0, read);
                }
            }
        }, "audio-recorder");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isRecording() {
        return recording;
    }

    /** Stops recording and returns the voice note as a WAV file. */
    public byte[] stop() throws IOException {
        halt();
        byte[] pcm = buffer.toByteArray();
        // Wrap the raw samples in an AudioInputStream, then let AudioSystem write a WAV file.
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize());
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav);
        return wav.toByteArray();
    }

    /** Stops recording and throws the sound away. */
    public void cancel() {
        halt();
        buffer = null;
    }

    /** Stops the worker thread and closes the microphone. */
    private void halt() {
        if (line == null) {
            return; // not recording
        }
        recording = false; // the worker's while loop ends
        line.stop();
        try {
            worker.join(1000); // wait (max 1 s) for the worker thread to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        line.close();
        line = null;
    }
}
