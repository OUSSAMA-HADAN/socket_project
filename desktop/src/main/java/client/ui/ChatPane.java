package client.ui;

import client.model.Conversation;
import client.util.FileKinds;
import client.util.Formats;
import common.Message;
import common.MessageType;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.io.File;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Right side: header of the open chat, the messages and the input bar.
 * <p>
 * Layout: a BorderPane with the header on top, the scrolling list of bubbles in the center
 * and the {@link InputBar} at the bottom. Before any chat is opened it shows a grey hint instead.
 */
public class ChatPane extends StackPane {

    private final String me;
    private final IntegerProperty onlineCount;                    // shared with ChatView
    private final BorderPane layout = new BorderPane();
    private final HBox header = new HBox(12);                     // avatar + name + status
    private final VBox messagesBox = new VBox(4);                 // the bubbles, top to bottom
    private final ScrollPane scroll = new ScrollPane(messagesBox);
    private final InputBar inputBar;

    private Conversation current;  // the chat being shown
    private String lastSender;     // to group consecutive bubbles of the same person
    private LocalDate lastDay;     // to insert a "Today" / "Yesterday" pill when the day changes

    public ChatPane(String me, IntegerProperty onlineCount, ChatActions actions) {
        this.me = me;
        this.onlineCount = onlineCount;
        getStyleClass().add("chat-background");

        header.getStyleClass().add("chat-header");
        header.setAlignment(Pos.CENTER_LEFT);

        messagesBox.getStyleClass().add("messages-box");
        messagesBox.setAlignment(Pos.BOTTOM_CENTER);
        // keep messages anchored at the bottom when there are only a few
        messagesBox.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        // auto-scroll to the newest message
        messagesBox.heightProperty().addListener((obs, old, h) -> scroll.setVvalue(1.0));

        scroll.getStyleClass().add("messages-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        inputBar = new InputBar(actions);

        layout.setTop(header);
        layout.setCenter(scroll);
        layout.setBottom(inputBar);

        getChildren().setAll(MessageBubble.pill("Select a chat to start messaging"));
        enableDragAndDrop(actions);
    }

    /** Shows a conversation: new header, all its messages, and the input bar (locked if the person is offline). */
    public void show(Conversation conversation) {
        current = conversation;
        AudioMessageView.stopActive();
        inputBar.reset();
        buildHeader(conversation);

        messagesBox.getChildren().clear();
        lastSender = null;
        lastDay = null;
        conversation.getMessages().forEach(this::render);

        // "bind": the input bar is disabled exactly while the other person is offline,
        // updated automatically when their online state changes.
        inputBar.disableProperty().unbind();
        if (conversation.isGroup()) {
            inputBar.setDisable(false);
        } else {
            inputBar.disableProperty().bind(conversation.onlineProperty().not());
        }

        getChildren().setAll(layout);
        inputBar.focusField();
    }

    /** Adds one new message at the bottom of the open chat. */
    public void append(Message message) {
        if (current != null) {
            render(message);
        }
    }

    // ---------------------------------------------------------------- rendering

    /** Turns one message into nodes on screen: a date pill if needed, then a bubble or a system pill. */
    private void render(Message message) {
        LocalDate day = message.getTimestamp().toLocalDate();
        if (!day.equals(lastDay)) {
            messagesBox.getChildren().add(MessageBubble.pill(Formats.day(day)));
            lastDay = day;
            lastSender = null;
        }
        if (message.getType() == MessageType.SYSTEM) {
            messagesBox.getChildren().add(MessageBubble.pill(message.getText()));
            lastSender = null;
            return;
        }
        boolean outgoing = me.equals(message.getSender());                     // mine -> right side, green
        boolean firstOfRun = !Objects.equals(lastSender, message.getSender()); // first of a series -> name + gap
        Node row = MessageBubble.create(message, outgoing, current.isGroup(), firstOfRun);
        VBox.setMargin(row, new Insets(firstOfRun ? 6 : 0, 0, 0, 0));
        messagesBox.getChildren().add(row);
        lastSender = message.getSender();
    }

    /**
     * The header: avatar, name and a status line that updates by itself (it is "bound" to the
     * online count, the typing flag or the online flag, depending on the kind of chat).
     */
    private void buildHeader(Conversation conversation) {
        Label title = new Label(conversation.getTitle());
        title.getStyleClass().add("header-title");
        Label subtitle = new Label();
        subtitle.getStyleClass().add("header-subtitle");

        Node avatar = conversation.isAi() ? Avatar.bot(42) : new Avatar(conversation.getTitle(), 42);
        if (conversation.isGroup()) {
            subtitle.textProperty().bind(Bindings.createStringBinding(
                    () -> onlineCount.get() + (onlineCount.get() == 1 ? " member online" : " members online"),
                    onlineCount));
        } else if (conversation.isAi()) {
            subtitle.textProperty().bind(Bindings.when(conversation.typingProperty())
                    .then("typing…").otherwise("AI assistant · always online"));
            subtitle.setTextFill(Color.web("#3390ec"));
        } else {
            subtitle.textProperty().bind(Bindings.when(conversation.onlineProperty())
                    .then("online").otherwise("offline"));
            subtitle.textFillProperty().bind(Bindings.when(conversation.onlineProperty())
                    .then((Paint) Color.web("#3390ec")).otherwise(Color.web("#707579")));
        }
        header.getChildren().setAll(avatar, new VBox(1, title, subtitle));
    }

    /** Lets the user drop files from the Windows explorer onto the chat to send them. */
    private void enableDragAndDrop(ChatActions actions) {
        // While dragging over the chat: accept only files, and only if we can send right now.
        setOnDragOver(e -> {
            if (current != null && !inputBar.isDisabled() && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        // When the files are dropped: send each one, as IMAGE / AUDIO / FILE depending on its extension.
        setOnDragDropped(e -> {
            boolean done = false;
            if (e.getDragboard().hasFiles()) {
                for (File file : e.getDragboard().getFiles()) {
                    if (file.isFile()) {
                        actions.sendFile(file, FileKinds.detect(file.getName()));
                    }
                }
                done = true;
            }
            e.setDropCompleted(done);
            e.consume();
        });
    }
}
