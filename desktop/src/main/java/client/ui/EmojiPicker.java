package client.ui;

import client.emoji.EmojiRepository;
import client.emoji.EmojiRepository.Category;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Telegram-style emoji panel: one scrolling list of sections ("Recently used", "Smileys & People", ...)
 * with a category bar at the bottom to jump between them. Emoji are color images.
 * <p>
 * A {@code Popup} is a small window without borders that floats over the app and closes by itself
 * when you click elsewhere. The "recently used" list is saved with Java {@code Preferences}
 * (stored in the Windows registry), so it survives restarts.
 */
public class EmojiPicker extends Popup {

    private static final double WIDTH = 380;
    private static final double HEIGHT = 400;
    private static final double EMOJI_SIZE = 30;
    private static final int MAX_RECENT = 32;
    private static final String RECENT_KEY = "recentEmoji";
    private static final String DEFAULT_RECENT = "👍 ❤ 😂 😮 😢 🙏 🔥 🎉 😍 👏 😁 🤔";

    private final Consumer<String> onPick;
    private final Preferences prefs = Preferences.userNodeForPackage(EmojiPicker.class);
    private final LinkedList<String> recent = new LinkedList<>();
    private final FlowPane recentGrid = grid();
    private final VBox sections = new VBox();
    private final ScrollPane scroll = new ScrollPane(sections);
    private long lastHidden;

    public EmojiPicker(Consumer<String> onPick) {
        this.onPick = onPick;
        setAutoHide(true);
        setHideOnEscape(true);
        setOnHidden(e -> lastHidden = System.currentTimeMillis());
        setOnShowing(e -> refreshRecent()); // never reshuffle the grid while the user is looking at it

        loadRecent();
        HBox categoryBar = new HBox(2);
        categoryBar.getStyleClass().add("emoji-category-bar");
        categoryBar.setAlignment(Pos.CENTER);

        VBox recentSection = section("Recently used", recentGrid);
        sections.getChildren().add(recentSection);
        categoryBar.getChildren().add(categoryButton("🕘", "Recently used", recentSection));

        for (Category category : EmojiRepository.categories()) {
            FlowPane grid = grid();
            category.emojis().forEach(emoji -> grid.getChildren().add(cell(emoji)));
            VBox section = section(category.name(), grid);
            sections.getChildren().add(section);
            categoryBar.getChildren().add(categoryButton(category.icon(), category.name(), section));
        }

        sections.getStyleClass().add("emoji-sections");
        scroll.getStyleClass().add("emoji-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox root = new VBox(scroll, categoryBar);
        root.getStyleClass().add("emoji-picker");
        root.getStylesheets().add(Theme.STYLESHEET);
        root.setPrefSize(WIDTH, HEIGHT);
        getContent().add(root);
    }

    /** Shows the picker above the given node, or hides it if already open. */
    public void toggle(Node anchor) {
        if (isShowing()) {
            hide();
            return;
        }
        if (System.currentTimeMillis() - lastHidden < 250) {
            return; // the click that auto-hid the popup was on the toggle button itself
        }
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        show(anchor, bounds.getMinX() - 10, bounds.getMinY() - HEIGHT - 14);
    }

    // ---------------------------------------------------------------- building blocks

    private VBox section(String title, FlowPane grid) {
        Label label = new Label(title);
        label.getStyleClass().add("emoji-section-title");
        return new VBox(4, label, grid);
    }

    private static FlowPane grid() {
        FlowPane grid = new FlowPane();
        grid.getStyleClass().add("emoji-grid");
        return grid;
    }

    private Node cell(String emoji) {
        Label cell = new Label(null, EmojiText.icon(emoji, EMOJI_SIZE));
        cell.getStyleClass().add("emoji-cell");
        cell.setOnMouseClicked(e -> pick(emoji));
        return cell;
    }

    private Button categoryButton(String icon, String name, Node target) {
        Button button = new Button(null, EmojiText.icon(icon, 22));
        button.getStyleClass().add("emoji-category");
        button.setTooltip(new Tooltip(name));
        button.setFocusTraversable(false);
        button.setOnAction(e -> scrollTo(target));
        return button;
    }

    /** Scrolls so the section's title is at the top (the scroll position goes from 0 to 1). */
    private void scrollTo(Node section) {
        double content = sections.getHeight();
        double viewport = scroll.getViewportBounds().getHeight();
        if (content <= viewport) {
            return;
        }
        scroll.setVvalue(Math.clamp(section.getBoundsInParent().getMinY() / (content - viewport), 0, 1));
    }

    // ---------------------------------------------------------------- recently used

    /** An emoji was clicked: insert it, move it to the front of "recently used" and save the list. */
    private void pick(String emoji) {
        onPick.accept(emoji);
        recent.remove(emoji);
        recent.addFirst(emoji);
        while (recent.size() > MAX_RECENT) {
            recent.removeLast();
        }
        try {
            prefs.put(RECENT_KEY, String.join(" ", recent));
        } catch (Exception ignored) {
            // preferences are a convenience only
        }
    }

    private void loadRecent() {
        String saved = DEFAULT_RECENT;
        try {
            saved = prefs.get(RECENT_KEY, DEFAULT_RECENT);
        } catch (Exception ignored) {
        }
        Arrays.stream(saved.split("\\s+")).filter(EmojiRepository::exists).forEach(recent::add);
    }

    private void refreshRecent() {
        List<Node> cells = new ArrayList<>();
        recent.forEach(emoji -> cells.add(cell(emoji)));
        recentGrid.getChildren().setAll(cells);
    }
}
