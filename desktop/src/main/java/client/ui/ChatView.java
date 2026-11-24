package client.ui;

import client.model.Conversation;
import client.network.ChatClient;
import client.network.MessageListener;
import common.Config;
import common.Message;
import common.MessageType;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main screen after login. Owns the conversations, receives network events
 * and sends what the user types.
 */
public class ChatView extends BorderPane implements MessageListener, ChatActions {

    private final ChatClient client;
    private final String me;
    private final Runnable onLogout;

    private final Map<String, Conversation> conversations = new HashMap<>();
    /** The extractor makes the sidebar re-sort/refresh when activity, unread or online state change. */
    private final ObservableList<Conversation> conversationList =
            FXCollections.observableArrayList(Conversation::observables);
    private final IntegerProperty onlineCount = new SimpleIntegerProperty(1);
    private final Conversation general = Conversation.general();

    private final Sidebar sidebar;
    private final ChatPane chatPane;
    private Conversation current;

    public ChatView(ChatClient client, Runnable onLogout) {
        this.client = client;
        this.me = client.getUsername();
        this.onLogout = onLogout;

        conversations.put(general.getId(), general);
        conversationList.add(general);
        Conversation ai = Conversation.aiChat(Config.AI_BOT_NAME); // always present, pinned under General
        conversations.put(ai.getId(), ai);
        conversationList.add(ai);

        sidebar = new Sidebar(me, conversationList, this::open, onLogout);
        chatPane = new ChatPane(me, onlineCount, this);
        setLeft(sidebar);
        setCenter(chatPane);
        open(general);
    }

    private void open(Conversation conversation) {
        if (conversation == current) {
            return;
        }
        current = conversation;
        conversation.markRead();
        sidebar.setActive(conversation);
        chatPane.show(conversation);
    }

    // ---------------------------------------------------------------- network events (listener thread)

    @Override
    public void onMessage(Message message) {
        Platform.runLater(() -> handle(message));
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            Dialogs.error(window(), "Disconnected", reason);
            onLogout.run();
        });
    }

    private void handle(Message message) {
        switch (message.getType()) {
            case USER_LIST -> updateUsers(message.getUsers());
            case SYSTEM -> deliver(general, message);
            case TYPING -> privateChat(message.getSender()).setTyping(true);
            case ERROR -> Dialogs.error(window(), "Server", message.getText());
            default -> {
                if (message.getType().isChat()) {
                    deliver(message.isPrivate() ? privateChat(message.getSender()) : general, message);
                }
            }
        }
    }

    private void deliver(Conversation conversation, Message message) {
        conversation.addMessage(message);
        if (conversation == current) {
            chatPane.append(message);
        } else {
            conversation.incrementUnread();
        }
    }

    private void updateUsers(List<String> users) {
        onlineCount.set((int) users.stream().filter(u -> !u.equals(Config.AI_BOT_NAME)).count());
        Set<String> online = new HashSet<>(users);
        for (String user : users) {
            if (!user.equals(me)) {
                privateChat(user);
            }
        }
        for (Conversation c : conversations.values()) {
            if (!c.isGroup()) {
                c.setOnline(online.contains(c.getId()));
            }
        }
    }

    private Conversation privateChat(String user) {
        return conversations.computeIfAbsent(user, name -> {
            Conversation c = Conversation.privateChat(name);
            conversationList.add(c);
            return c;
        });
    }

    // ---------------------------------------------------------------- user actions

    @Override
    public void sendText(String text) {
        post(Message.text(me, current.getRecipient(), text));
    }

    @Override
    public void sendVoice(byte[] wav) {
        String name = "voice-" + System.currentTimeMillis() + ".wav";
        post(Message.attachment(MessageType.VOICE, me, current.getRecipient(), name, wav));
    }

    @Override
    public void sendFile(File file, MessageType requestedType) {
        if (file.length() > Config.MAX_ATTACHMENT_BYTES) {
            Dialogs.error(window(), "File too large",
                    file.getName() + " is bigger than " + Config.MAX_ATTACHMENT_BYTES / (1024 * 1024) + " MB.");
            return;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            MessageType type = requestedType;
            if (type == MessageType.IMAGE && new Image(new ByteArrayInputStream(data)).isError()) {
                type = MessageType.FILE; // not a readable image: send it as a document
            }
            post(Message.attachment(type, me, current.getRecipient(), file.getName(), data));
        } catch (IOException e) {
            Dialogs.error(window(), "Could not read " + file.getName(), e.getMessage());
        }
    }

    private void post(Message message) {
        client.send(message);
        deliver(current, message);
    }

    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
