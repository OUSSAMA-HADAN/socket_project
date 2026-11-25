package client.ui;

import client.util.Formats;
import common.Message;
import common.MessageType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;

import java.io.ByteArrayInputStream;

/**
 * Builds the visual row for one chat message (text, photo, audio or file).
 * <p>
 * Structure of a row: [avatar (group chats only)] [bubble: sender name / content / time].
 * The bubble's colors and rounded corners come from the CSS classes "bubble-in" (white, left)
 * and "bubble-out" (green, right) in theme.css.
 */
public final class MessageBubble {

    // Size limits in pixels.
    private static final double MAX_BUBBLE_WIDTH = 480;
    private static final double MAX_IMAGE_WIDTH = 320;
    private static final double MAX_IMAGE_HEIGHT = 360;
    private static final double AVATAR_SIZE = 34;

    private MessageBubble() {
    }

    /**
     * @param outgoing   sent by me (right side, green)
     * @param group      shown in the group chat (adds sender name + avatar)
     * @param firstOfRun first of consecutive messages from the same sender
     */
    public static Node create(Message message, boolean outgoing, boolean group, boolean firstOfRun) {
        VBox bubble = new VBox(3);
        bubble.getStyleClass().addAll("bubble", outgoing ? "bubble-out" : "bubble-in");
        bubble.setMaxWidth(MAX_BUBBLE_WIDTH);

        boolean showSender = group && !outgoing && firstOfRun;
        if (showSender) {
            Label name = new Label(message.getSender());
            name.getStyleClass().add("sender-name");
            name.setTextFill(Avatar.colorFor(message.getSender()));
            bubble.getChildren().add(name);
        }

        // A text made of only 1 to 3 emoji is shown big, without a bubble (like Telegram).
        int jumboCount = message.getType() == MessageType.TEXT ? EmojiText.emojiOnlyCount(message.getText()) : 0;
        boolean jumbo = jumboCount >= 1 && jumboCount <= 3;

        // The content depends on the message type.
        Node content = switch (message.getType()) {
            case TEXT -> jumbo ? EmojiText.jumbo(message.getText(), jumboCount) : textContent(message);
            case IMAGE -> imageContent(message);
            case AUDIO, VOICE -> new AudioMessageView(message);
            default -> fileContent(message);
        };
        if (jumbo) {
            bubble.getStyleClass().add("jumbo-bubble");
        } else if (content instanceof ImageView) {
            bubble.getStyleClass().add("media-bubble");
        }

        // The small time under the content ("14:05  ✓✓" for my messages).
        Label meta = new Label(Formats.time(message.getTimestamp()) + (outgoing ? "  ✓✓" : ""));
        meta.getStyleClass().add("message-meta");
        HBox metaRow = new HBox(meta);
        metaRow.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().addAll(content, metaRow);

        // The full-width row: pushes my bubbles to the right, others' to the left.
        HBox row = new HBox(8);
        row.setAlignment(outgoing ? Pos.TOP_RIGHT : Pos.TOP_LEFT);
        if (group && !outgoing) {
            // Avatar only on the first bubble of a series; an empty gap keeps the others aligned.
            row.getChildren().add(firstOfRun ? new Avatar(message.getSender(), AVATAR_SIZE) : gap(AVATAR_SIZE));
        }
        row.getChildren().add(bubble);
        return row;
    }

    /** Small centered label used for system messages and date separators. */
    public static Node pill(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("system-pill");
        label.setWrapText(true);
        HBox row = new HBox(label);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }

    // ---------------------------------------------------------------- content types

    /** Text with color emoji, plus a right-click "Copy text" menu. */
    private static Node textContent(Message message) {
        TextFlow text = EmojiText.flow(message.getText(), 21);
        text.getStyleClass().add("message-text");
        text.setMinHeight(Region.USE_PREF_SIZE);

        MenuItem copy = new MenuItem("Copy text");
        copy.setOnAction(e -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(message.getText());
            Clipboard.getSystemClipboard().setContent(clip);
        });
        ContextMenu menu = new ContextMenu(copy);
        text.setOnContextMenuRequested(e -> menu.show(text, e.getScreenX(), e.getScreenY()));
        return text;
    }

    /** The photo, shrunk to fit, with rounded corners; a click opens it full size. */
    private static Node imageContent(Message message) {
        Image image = new Image(new ByteArrayInputStream(message.getData())); // decode the bytes
        if (image.isError() || image.getWidth() <= 0) {
            return fileContent(message); // unreadable picture: show it as a file instead
        }
        // Scale factor so the picture fits in 320 x 360, never enlarged (max 1).
        double scale = Math.min(1, Math.min(MAX_IMAGE_WIDTH / image.getWidth(), MAX_IMAGE_HEIGHT / image.getHeight()));
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(image.getWidth() * scale);
        view.setFitHeight(image.getHeight() * scale);

        // A "clip" hides everything outside a shape: here a rounded rectangle -> rounded corners.
        Rectangle clip = new Rectangle(view.getFitWidth(), view.getFitHeight());
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        view.setClip(clip);

        view.setCursor(Cursor.HAND);
        view.setOnMouseClicked(e ->
                ImageViewer.show(view.getScene().getWindow(), image, message.getFileName(), message.getData()));
        return view;
    }

    /** A file: round icon, name and size; a click asks where to save it. */
    private static Node fileContent(Message message) {
        Circle circle = new Circle(24);
        circle.getStyleClass().add("file-icon");
        Node glyph = Icons.of(Icons.FILE, 24);
        StackPane icon = new StackPane(circle, glyph);
        icon.getStyleClass().add("file-badge");

        Label name = new Label(message.getFileName());
        name.getStyleClass().add("file-name");
        name.setMaxWidth(260);
        Label size = new Label(Formats.size(message.getSize()) + " · Click to save");
        size.getStyleClass().add("file-size");

        HBox box = new HBox(12, icon, new VBox(3, name, size));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setCursor(Cursor.HAND);
        Tooltip.install(box, new Tooltip("Save " + message.getFileName()));
        box.setOnMouseClicked(e ->
                Dialogs.saveFile(box.getScene().getWindow(), message.getFileName(), message.getData()));
        return box;
    }

    /** An invisible box of a fixed width (keeps bubbles aligned under the avatar). */
    private static Region gap(double width) {
        Region region = new Region();
        region.setMinWidth(width);
        region.setPrefWidth(width);
        return region;
    }
}
