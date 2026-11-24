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
 */
public class AudioRecorder {

    private static final AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread worker;
    private volatile boolean recording;

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
        AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(pcm), FORMAT, pcm.length / FORMAT.getFrameSize());
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(stream, AudioFileFormat.Type.WAVE, wav);
        return wav.toByteArray();
    }

    public void cancel() {
        halt();
        buffer = null;
    }

    private void halt() {
        if (line == null) {
            return;
        }
        recording = false;
        line.stop();
        try {
            worker.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        line.close();
        line = null;
    }
}
