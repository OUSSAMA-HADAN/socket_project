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
 */
public class ChatPane extends StackPane {

    private final String me;
    private final IntegerProperty onlineCount;
    private final BorderPane layout = new BorderPane();
    private final HBox header = new HBox(12);
    private final VBox messagesBox = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(messagesBox);
    private final InputBar inputBar;

    private Conversation current;
    private String lastSender;
    private LocalDate lastDay;

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

    public void show(Conversation conversation) {
        current = conversation;
        AudioMessageView.stopActive();
        inputBar.reset();
        buildHeader(conversation);

        messagesBox.getChildren().clear();
        lastSender = null;
        lastDay = null;
        conversation.getMessages().forEach(this::render);

        inputBar.disableProperty().unbind();
        if (conversation.isGroup()) {
            inputBar.setDisable(false);
        } else {
            inputBar.disableProperty().bind(conversation.onlineProperty().not());
        }

        getChildren().setAll(layout);
        inputBar.focusField();
    }

    public void append(Message message) {
        if (current != null) {
            render(message);
        }
    }

    // ---------------------------------------------------------------- rendering

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
        boolean outgoing = me.equals(message.getSender());
        boolean firstOfRun = !Objects.equals(lastSender, message.getSender());
        Node row = MessageBubble.create(message, outgoing, current.isGroup(), firstOfRun);
        VBox.setMargin(row, new Insets(firstOfRun ? 6 : 0, 0, 0, 0));
        messagesBox.getChildren().add(row);
        lastSender = message.getSender();
    }

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

    private void enableDragAndDrop(ChatActions actions) {
        setOnDragOver(e -> {
            if (current != null && !inputBar.isDisabled() && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
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
