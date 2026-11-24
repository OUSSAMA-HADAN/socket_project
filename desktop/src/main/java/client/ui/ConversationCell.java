package client.ui;

import client.model.Conversation;
import client.util.Formats;
import common.Message;
import javafx.beans.property.ObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextFlow;

import java.util.function.Consumer;

/**
 * One row of the sidebar: avatar, name, last message, time and unread badge.
 */
public class ConversationCell extends ListCell<Conversation> {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double PREVIEW_HEIGHT = 21;

    private final String me;
    private final ObjectProperty<Conversation> active;

    public ConversationCell(String me, ObjectProperty<Conversation> active, Consumer<Conversation> onOpen) {
        this.me = me;
        this.active = active;
        getStyleClass().add("conversation-cell");
        setPrefWidth(0); // never wider than the list -> no horizontal scrollbar
        setOnMouseClicked(e -> {
            if (getItem() != null) {
                onOpen.accept(getItem());
            }
        });
    }

    @Override
    protected void updateItem(Conversation conversation, boolean empty) {
        super.updateItem(conversation, empty);
        setText(null);
        if (empty || conversation == null) {
            setGraphic(null);
            pseudoClassStateChanged(ACTIVE, false);
            return;
        }
        pseudoClassStateChanged(ACTIVE, conversation == active.get());
        setGraphic(build(conversation));
    }

    private Node build(Conversation c) {
        Node avatar = c.isAi() ? Avatar.bot(50) : new Avatar(c.getTitle(), 50);
        if (!c.isGroup() && !c.isAi() && c.isOnline()) {
            Circle dot = new Circle(7);
            dot.getStyleClass().add("online-dot");
            StackPane withDot = new StackPane(avatar, dot);
            StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
            avatar = withDot;
        }

        Message last = c.getLastMessage();

        Label title = new Label(c.getTitle());
        title.getStyleClass().add("chat-title");
        Label time = new Label(last == null ? "" : Formats.time(last.getTimestamp()));
        time.getStyleClass().add("chat-time");
        HBox top = new HBox(6, title, spacer(), time);
        top.setAlignment(Pos.CENTER_LEFT);

        String previewText;
        if (c.isTyping()) {
            previewText = "typing…";
        } else if (last != null) {
            previewText = Formats.preview(last, me, c.isGroup());
        } else if (c.isAi()) {
            previewText = "Ask me anything 🤖";
        } else {
            previewText = c.isGroup() ? "Group chat with everyone online" : "Say hi 👋";
        }
        Node preview = previewLine(previewText);
        HBox bottom = new HBox(6, preview);
        bottom.setAlignment(Pos.CENTER_LEFT);
        if (c.getUnread() > 0) {
            Label badge = new Label(String.valueOf(c.getUnread()));
            badge.getStyleClass().add("unread-badge");
            bottom.getChildren().add(badge);
        }

        VBox texts = new VBox(4, top, bottom);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);

        HBox row = new HBox(12, avatar, texts);
        row.getStyleClass().add("chat-item");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** One line of text with color emoji, cut off when too long. */
    private static Node previewLine(String text) {
        String shortText = text.codePointCount(0, text.length()) > 60
                ? text.substring(0, text.offsetByCodePoints(0, 60)) + "…" : text;
        TextFlow flow = EmojiText.flow(shortText, 17);
        flow.getStyleClass().add("chat-preview");

        StackPane line = new StackPane(flow);
        line.setAlignment(Pos.TOP_LEFT);
        line.setMinSize(0, PREVIEW_HEIGHT);
        line.setPrefSize(0, PREVIEW_HEIGHT);
        line.setMaxSize(Double.MAX_VALUE, PREVIEW_HEIGHT);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(line.widthProperty());
        clip.heightProperty().bind(line.heightProperty());
        line.setClip(clip);
        HBox.setHgrow(line, Priority.ALWAYS);
        return line;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
