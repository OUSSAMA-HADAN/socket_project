package client.ui;

import client.emoji.EmojiRepository;
import client.emoji.EmojiRepository.Segment;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows color emoji inside a normal {@link TextField}.
 * <p>
 * A TextField can only draw text, so emoji come out black and white. This transparent layer sits
 * on top of the field and, for every emoji, asks the field's internal {@link Text} node where the
 * glyph was drawn ({@link Text#rangeShape}) and covers exactly that spot with the color image.
 * Editing (caret, selection, copy/paste, undo, IME) stays 100% native TextField behavior.
 */
public final class EmojiFieldOverlay extends Pane {

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SELECTION = Color.web("#3390ec");

    private final TextField field;
    private final Rectangle clip = new Rectangle();
    private final InvalidationListener refreshLater = obs -> scheduleRefresh();
    private Text textNode;
    private boolean refreshPending;

    private EmojiFieldOverlay(TextField field) {
        this.field = field;
        setMouseTransparent(true); // clicks go through to the real field
        setClip(clip);

        field.textProperty().addListener(refreshLater);
        field.selectionProperty().addListener(refreshLater);
        field.caretPositionProperty().addListener(refreshLater);
        field.widthProperty().addListener(refreshLater);
        field.heightProperty().addListener(refreshLater);
        field.fontProperty().addListener(refreshLater);
        field.skinProperty().addListener(obs -> {
            textNode = null;
            scheduleRefresh();
        });
    }

    /** Returns the field wrapped together with its emoji layer; put the wrapper in the layout. */
    public static StackPane wrap(TextField field) {
        field.setMaxWidth(Double.MAX_VALUE);
        StackPane stack = new StackPane(field, new EmojiFieldOverlay(field));
        stack.setMinWidth(0);
        return stack;
    }

    private void scheduleRefresh() {
        if (!refreshPending) {
            refreshPending = true;
            Platform.runLater(() -> {
                refreshPending = false;
                refresh();
            });
        }
    }

    private void refresh() {
        Text text = textNode();
        if (text == null || getScene() == null) {
            getChildren().clear();
            return;
        }
        field.layout(); // make sure the skin has positioned/scrolled its text for the current state
        updateClip();

        String value = text.getText() == null ? "" : text.getText();
        IndexRange selection = field.getSelection();
        List<Node> nodes = new ArrayList<>();
        int index = 0;
        for (Segment segment : EmojiRepository.split(value)) {
            int start = index;
            int end = index + segment.text().length();
            index = end;
            if (!segment.emoji()) {
                continue;
            }
            Bounds glyph = rangeBounds(text, start, end);
            if (glyph == null) {
                continue;
            }
            Bounds local = sceneToLocal(text.localToScene(glyph));
            boolean selected = selection.getLength() > 0 && start >= selection.getStart() && end <= selection.getEnd();

            // hide the black & white glyph (keep 1px free on each side for the caret)
            Rectangle cover = new Rectangle(local.getMinX() + 1, local.getMinY(),
                    Math.max(0, local.getWidth() - 2), local.getHeight());
            cover.setFill(selected ? SELECTION : BACKGROUND);

            double size = Math.min(local.getHeight() * 0.95, Math.max(local.getWidth(), 16));
            ImageView image = EmojiText.icon(segment.text(), size);
            image.setLayoutX(local.getMinX() + (local.getWidth() - size) / 2);
            image.setLayoutY(local.getMinY() + (local.getHeight() - size) / 2);

            nodes.add(cover);
            nodes.add(image);
        }
        getChildren().setAll(nodes);
    }

    /** Only draw inside the field's text area (the text scrolls under the padding). */
    private void updateClip() {
        Insets padding = field.getPadding();
        Bounds fieldBounds = sceneToLocal(field.localToScene(field.getLayoutBounds()));
        clip.setX(fieldBounds.getMinX() + padding.getLeft());
        clip.setY(fieldBounds.getMinY());
        clip.setWidth(Math.max(0, fieldBounds.getWidth() - padding.getLeft() - padding.getRight()));
        clip.setHeight(fieldBounds.getHeight());
    }

    private static Bounds rangeBounds(Text text, int start, int end) {
        PathElement[] elements = text.rangeShape(start, end);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (PathElement element : elements) {
            double x, y;
            if (element instanceof MoveTo m) {
                x = m.getX();
                y = m.getY();
            } else if (element instanceof LineTo l) {
                x = l.getX();
                y = l.getY();
            } else {
                continue;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return maxX > minX ? new BoundingBox(minX, minY, maxX - minX, maxY - minY) : null;
    }

    /** The Text node the TextField skin uses to draw the typed text. */
    private Text textNode() {
        if (textNode == null) {
            textNode = findText(field);
        }
        return textNode;
    }

    private Text findText(Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            // the prompt text is also a Text node: pick the one that shows the field's real content
            if (child instanceof Text t && field.getText().equals(t.getText())) {
                return t;
            }
            if (child instanceof Parent p) {
                Text found = findText(p);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
