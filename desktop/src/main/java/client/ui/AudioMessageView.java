package client.ui;

import client.util.FileKinds;
import client.util.Formats;
import common.Message;
import common.MessageType;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Player for voice notes and audio files inside a bubble.
 * Only one audio plays at a time; the player is created lazily on first play.
 * <p>
 * JavaFX's MediaPlayer can only play from a file or URL, not from bytes in memory, so the
 * first time you press play the audio is written to a temporary file.
 */
public class AudioMessageView extends HBox {

    /** The view currently playing (static = shared by all audio views), so starting one stops the other. */
    private static AudioMessageView active;

    private final Message message;
    private final Button playButton = new Button();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label timeLabel = new Label();
    private final String idleLabel;
    private Path tempFile;
    private MediaPlayer player;

    public AudioMessageView(Message message) {
        super(10);
        this.message = message;
        getStyleClass().add("audio-view");
        setAlignment(Pos.CENTER_LEFT);

        playButton.getStyleClass().add("audio-button");
        playButton.setOnAction(e -> togglePlay());
        setPlayingIcon(false);

        boolean voice = message.getType() == MessageType.VOICE;
        Label title = new Label(voice ? "Voice message" : message.getFileName());
        title.getStyleClass().add("audio-title");
        title.setMaxWidth(220);

        progress.getStyleClass().add("audio-progress");
        progress.setPrefWidth(200);
        // Click on the bar to jump: x position / bar width = fraction of the track (0..1).
        progress.setOnMouseClicked(e -> seek(e.getX() / progress.getWidth()));

        idleLabel = describeLength(message);
        timeLabel.setText(idleLabel);
        timeLabel.getStyleClass().add("audio-time");

        HBox titleRow = new HBox(6, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (!voice) {
            Button save = new Button();
            save.setGraphic(Icons.of(Icons.DOWNLOAD, 18));
            save.getStyleClass().add("mini-button");
            save.setTooltip(new Tooltip("Save"));
            save.setOnAction(e -> Dialogs.saveFile(getScene().getWindow(), message.getFileName(), message.getData()));
            titleRow.getChildren().add(save);
        }

        getChildren().addAll(playButton, new VBox(4, titleRow, progress, timeLabel));
    }

    /** Stops whatever audio is playing (used when leaving a chat or logging out). */
    public static void stopActive() {
        if (active != null) {
            active.release();
            active = null;
        }
    }

    /** Play / pause button. */
    private void togglePlay() {
        if (player != null && player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
            setPlayingIcon(false);
            return;
        }
        if (active != this) {
            stopActive();
            active = this;
        }
        if (player == null && !createPlayer()) {
            return;
        }
        player.play();
        setPlayingIcon(true);
    }

    private void setPlayingIcon(boolean playing) {
        playButton.setGraphic(Icons.of(playing ? Icons.PAUSE : Icons.PLAY, 24));
    }

    /** Writes the audio to a temp file and creates the MediaPlayer. Returns false if it can't be played. */
    private boolean createPlayer() {
        try {
            if (tempFile == null) {
                String ext = FileKinds.extension(message.getFileName());
                tempFile = Files.createTempFile("chatfx-audio-", "." + (ext.isEmpty() ? "wav" : ext));
                tempFile.toFile().deleteOnExit();
                Files.write(tempFile, message.getData());
            }
            player = new MediaPlayer(new Media(tempFile.toUri().toString()));
        } catch (IOException | MediaException e) {
            showUnsupported();
            return false;
        }
        // While playing, the player's current time changes many times per second: move the bar.
        player.currentTimeProperty().addListener((obs, old, time) -> updateProgress(time));
        player.setOnEndOfMedia(() -> {
            player.stop();
            setPlayingIcon(false);
            progress.setProgress(0);
            timeLabel.setText(idleLabel);
        });
        player.setOnError(this::showUnsupported);
        return true;
    }

    private void updateProgress(Duration time) {
        Duration total = player == null ? null : player.getTotalDuration();
        if (total == null || total.isUnknown() || total.toMillis() <= 0) {
            return;
        }
        progress.setProgress(time.toMillis() / total.toMillis());
        timeLabel.setText(format(time) + " / " + format(total));
    }

    private static String format(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) {
            return "0:00";
        }
        return Formats.duration((long) duration.toSeconds());
    }

    /** Jumps to a position (0 = start, 1 = end). */
    private void seek(double fraction) {
        if (player == null) {
            return;
        }
        Duration total = player.getTotalDuration();
        if (total != null && !total.isUnknown()) {
            player.seek(total.multiply(Math.clamp(fraction, 0, 1)));
        }
    }

    /** Stops and frees the player, and resets the display. */
    private void release() {
        if (player != null) {
            player.dispose();
            player = null;
        }
        setPlayingIcon(false);
        progress.setProgress(0);
        timeLabel.setText(idleLabel);
    }

    private void showUnsupported() {
        release();
        playButton.setDisable(true);
        timeLabel.setText("This audio format can't be played");
    }

    /** Duration for WAV files (readable without playing), otherwise the file size. */
    private static String describeLength(Message message) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(message.getData()))) {
            AudioFormat format = in.getFormat();
            if (in.getFrameLength() > 0 && format.getFrameRate() > 0) {
                return Formats.duration(Math.round(in.getFrameLength() / format.getFrameRate()));
            }
        } catch (Exception ignored) {
            // not a format javax.sound understands (mp3, m4a...)
        }
        return Formats.size(message.getSize());
    }
}
