package client.ui;

import client.media.AudioRecorder;
import client.util.FileKinds;
import client.util.Formats;
import common.MessageType;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.sound.sampled.LineUnavailableException;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Bottom bar: emoji, text field, attachments, and a round button that is a
 * microphone when the field is empty and a send arrow otherwise.
 * <p>
 * Two "modes" share the same place: the compose box (emoji, text, attach) and, while a voice
 * note is recorded, the record box (red dot, timer, CANCEL). Only one is visible at a time.
 */
public class InputBar extends HBox {

    /** Shorter recordings are treated as an accidental tap and not sent. */
    private static final long MIN_VOICE_MILLIS = 700;
    /** A WAV file with no sound is just its 44-byte header. */
    private static final int WAV_HEADER_BYTES = 44;

    private final ChatActions actions;
    private final TextField field = new TextField();
    private final Button actionButton = new Button();
    private final HBox composeBox;
    private final HBox recordBox;
    private final Label recordTime = new Label("0:00");
    private final Timeline recordTimer;
    private final AudioRecorder recorder = new AudioRecorder();
    private EmojiPicker emojiPicker; // created on first use (it loads ~1200 images)
    private long recordStartedAt;

    public InputBar(ChatActions actions) {
        super(10);
        this.actions = actions;
        getStyleClass().add("input-bar");
        setAlignment(Pos.CENTER);

        // --- compose mode
        Button emoji = iconButton(Icons.EMOJI, "Emoji");
        emoji.setOnAction(e -> {
            if (emojiPicker == null) {
                emojiPicker = new EmojiPicker(this::insertEmoji);
            }
            emojiPicker.toggle(emoji);
        });

        field.setPromptText("Message");
        field.getStyleClass().add("message-field");
        field.setOnAction(e -> sendText());
        StackPane fieldWithEmoji = EmojiFieldOverlay.wrap(field); // color emoji while typing
        HBox.setHgrow(fieldWithEmoji, Priority.ALWAYS);

        Button attach = iconButton(Icons.ATTACH, "Attach");
        ContextMenu attachMenu = new ContextMenu(
                menuItem(Icons.PHOTO, "Photo", () -> pickFiles(MessageType.IMAGE)),
                menuItem(Icons.MUSIC, "Audio", () -> pickFiles(MessageType.AUDIO)),
                menuItem(Icons.FILE, "File", () -> pickFiles(MessageType.FILE)));
        attach.setOnAction(e -> attachMenu.show(attach, Side.TOP, 0, -8));

        composeBox = new HBox(2, emoji, fieldWithEmoji, attach);
        composeBox.getStyleClass().add("input-box");
        composeBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(composeBox, Priority.ALWAYS);

        // --- recording mode
        Circle dot = new Circle(6);
        dot.getStyleClass().add("recording-dot");
        FadeTransition blink = new FadeTransition(Duration.millis(600), dot);
        blink.setFromValue(1);
        blink.setToValue(0.2);
        blink.setAutoReverse(true);
        blink.setCycleCount(Animation.INDEFINITE);
        blink.play();
        recordTime.getStyleClass().add("record-time");
        Label hint = new Label("Recording voice message…");
        hint.getStyleClass().add("record-hint");
        Button cancel = new Button("CANCEL");
        cancel.getStyleClass().add("link-button");
        cancel.setOnAction(e -> cancelRecording());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        recordBox = new HBox(12, dot, recordTime, hint, spacer, cancel);
        recordBox.getStyleClass().add("input-box");
        recordBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(recordBox, Priority.ALWAYS);
        showRecordBox(false);

        // A Timeline runs code on the JavaFX thread at fixed times: here every 250 ms, to update "0:07".
        recordTimer = new Timeline(new KeyFrame(Duration.millis(250), e -> recordTime.setText(
                Formats.duration((System.currentTimeMillis() - recordStartedAt) / 1000))));
        recordTimer.setCycleCount(Animation.INDEFINITE);

        // --- round action button
        actionButton.getStyleClass().add("send-button");
        actionButton.setOnAction(e -> onAction());
        // Each time the text changes, switch the button between mic and send arrow.
        field.textProperty().addListener((obs, old, text) -> updateActionButton());
        updateActionButton();

        getChildren().addAll(composeBox, recordBox, actionButton);
    }

    /** Called when the user switches to another chat. */
    public void reset() {
        if (recorder.isRecording()) {
            cancelRecording();
        }
        if (emojiPicker != null) {
            emojiPicker.hide();
        }
        field.clear();
    }

    public void focusField() {
        Platform.runLater(field::requestFocus);
    }

    // ---------------------------------------------------------------- actions

    /** The round button: finish a recording, send the text, or start recording (in that order of priority). */
    private void onAction() {
        if (recorder.isRecording()) {
            finishRecording();
        } else if (!field.getText().isBlank()) {
            sendText();
        } else {
            startRecording();
        }
    }

    private void sendText() {
        String text = field.getText().strip();
        if (!text.isEmpty()) {
            actions.sendText(text);
            field.clear();
        }
    }

    /** Called by the emoji picker: inserts the emoji where the cursor is. */
    private void insertEmoji(String emoji) {
        field.insertText(field.getCaretPosition(), emoji);
    }

    /** Opens Windows' file dialog (filtered by type) and sends every file chosen. */
    private void pickFiles(MessageType type) {
        FileChooser chooser = new FileChooser();
        switch (type) {
            case IMAGE -> {
                chooser.setTitle("Send photos");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        "Images", FileKinds.patterns(FileKinds.IMAGE_EXTENSIONS)));
            }
            case AUDIO -> {
                chooser.setTitle("Send audio");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        "Audio", FileKinds.patterns(FileKinds.AUDIO_EXTENSIONS)));
            }
            default -> chooser.setTitle("Send files");
        }
        List<File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files != null) {
            files.forEach(file -> actions.sendFile(file, type));
        }
    }

    // ---------------------------------------------------------------- voice notes

    private void startRecording() {
        try {
            recorder.start();
        } catch (LineUnavailableException | SecurityException e) {
            Dialogs.error(getScene().getWindow(), "Microphone unavailable", e.getMessage());
            return;
        }
        recordStartedAt = System.currentTimeMillis();
        recordTime.setText("0:00");
        recordTimer.play();
        showRecordBox(true);
    }

    private void finishRecording() {
        stopRecordingUi();
        if (System.currentTimeMillis() - recordStartedAt < MIN_VOICE_MILLIS) {
            recorder.cancel(); // accidental tap: don't send an empty voice note
            return;
        }
        try {
            byte[] wav = recorder.stop();
            if (wav.length <= WAV_HEADER_BYTES) {
                Dialogs.error(getScene().getWindow(), "Nothing was recorded", "Check that your microphone works.");
                return;
            }
            actions.sendVoice(wav);
        } catch (IOException e) {
            Dialogs.error(getScene().getWindow(), "Could not record", e.getMessage());
        }
    }

    private void cancelRecording() {
        stopRecordingUi();
        recorder.cancel();
    }

    private void stopRecordingUi() {
        recordTimer.stop();
        showRecordBox(false);
    }

    /** Swaps the compose box and the record box ("managed = false" also frees the space it took). */
    private void showRecordBox(boolean recording) {
        recordBox.setVisible(recording);
        recordBox.setManaged(recording);
        composeBox.setVisible(!recording);
        composeBox.setManaged(!recording);
        updateActionButton(recording);
    }

    private void updateActionButton() {
        updateActionButton(recorder.isRecording());
    }

    private void updateActionButton(boolean recording) {
        boolean send = recording || !field.getText().isBlank();
        actionButton.setGraphic(Icons.of(send ? Icons.SEND : Icons.MIC, 26));
    }

    // ---------------------------------------------------------------- helpers

    /** A flat round button showing an SVG icon, with a hint on mouse-over. */
    private static Button iconButton(String iconPath, String tooltip) {
        Button button = new Button();
        button.setGraphic(Icons.of(iconPath, 24));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private static MenuItem menuItem(String iconPath, String text, Runnable action) {
        MenuItem item = new MenuItem(text, Icons.of(iconPath, 20));
        item.setOnAction(e -> action.run());
        return item;
    }
}
