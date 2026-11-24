package client.ui;

import client.emoji.EmojiRepository;
import client.emoji.EmojiRepository.Segment;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/**
 * Builds text nodes where emoji are drawn as color images.
 * Plain text runs get the style class {@code text-run}.
 */
public final class EmojiText {

    private EmojiText() {
    }

    public static TextFlow flow(String text, double emojiSize) {
        TextFlow flow = new TextFlow();
        for (Segment segment : EmojiRepository.split(text)) {
            if (segment.emoji()) {
                ImageView icon = icon(segment.text(), emojiSize);
                icon.setTranslateY(emojiSize * 0.2); // sit on the text baseline like a glyph
                flow.getChildren().add(icon);
            } else {
                Text run = new Text(segment.text());
                run.getStyleClass().add("text-run");
                flow.getChildren().add(run);
            }
        }
        return flow;
    }

    public static ImageView icon(String emoji, double size) {
        ImageView view = new ImageView(EmojiRepository.image(emoji));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    /** Number of emoji if the text is made only of emoji (and spaces), otherwise 0. */
    public static int emojiOnlyCount(String text) {
        List<Segment> segments = EmojiRepository.split(text);
        int count = 0;
        for (Segment segment : segments) {
            if (segment.emoji()) {
                count++;
            } else if (!segment.text().isBlank()) {
                return 0;
            }
        }
        return count;
    }

    /** Big emoji, Telegram style, for messages that contain only 1-3 emoji. */
    public static Node jumbo(String text, int count) {
        double size = count == 1 ? 72 : 52;
        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        for (Segment segment : EmojiRepository.split(text)) {
            if (segment.emoji()) {
                box.getChildren().add(icon(segment.text(), size));
            }
        }
        return box;
    }
}
