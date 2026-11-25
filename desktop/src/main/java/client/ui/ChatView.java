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
 * <p>
 * <b>The desktop's "brain"</b>:
 * <ul>
 *   <li>Layout: the {@link Sidebar} on the left (list of chats), the {@link ChatPane} in the
 *       center (the open chat). BorderPane is a layout with left/center/right/top/bottom areas.</li>
 *   <li>Receiving: implements {@link MessageListener}, so ChatClient's "chat-listener" thread
 *       calls {@link #onMessage}; we jump to the JavaFX thread and file the message in the right
 *       conversation ({@link #handle}).</li>
 *   <li>Sending: implements {@link ChatActions}, so the input bar calls sendText/sendFile/sendVoice.</li>
 * </ul>
 */
public class ChatView extends BorderPane implements MessageListener, ChatActions {

    private final ChatClient client;
    private final String me;          // my username
    private final Runnable onLogout;  // what to do when I log out (ChatApp goes back to login)

    /** All conversations by id ("#general", "AI Assistant", "bob"...). */
    private final Map<String, Conversation> conversations = new HashMap<>();
    /** The extractor makes the sidebar re-sort/refresh when activity, unread or online state change. */
    private final ObservableList<Conversation> conversationList =
            FXCollections.observableArrayList(Conversation::observables);
    /** How many people are online (shown in the General header). */
    private final IntegerProperty onlineCount = new SimpleIntegerProperty(1);
    private final Conversation general = Conversation.general();

    private final Sidebar sidebar;
    private final ChatPane chatPane;
    private Conversation current; // the conversation currently open

    public ChatView(ChatClient client, Runnable onLogout) {
        this.client = client;
        this.me = client.getUsername();
        this.onLogout = onLogout;

        // General and the AI assistant always exist; private chats are added as people come online.
        conversations.put(general.getId(), general);
        conversationList.add(general);
        Conversation ai = Conversation.aiChat(Config.AI_BOT_NAME); // always present, pinned under General
        conversations.put(ai.getId(), ai);
        conversationList.add(ai);

        sidebar = new Sidebar(me, conversationList, this::open, onLogout);
        chatPane = new ChatPane(me, onlineCount, this);
        setLeft(sidebar);
        setCenter(chatPane);
        open(general); // start in the General group
    }

    /** Opens a conversation in the center pane and marks it as read. */
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

    /**
     * Called on the "chat-listener" thread. JavaFX only allows the UI to be changed on its own
     * thread, so Platform.runLater hands the work over to the JavaFX thread.
     */
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

    /** Runs on the JavaFX thread: decides what to do with each kind of message. */
    private void handle(Message message) {
        switch (message.getType()) {
            case USER_LIST -> updateUsers(message.getUsers());
            case SYSTEM -> deliver(general, message);                         // "x joined" goes to General
            case TYPING -> privateChat(message.getSender()).setTyping(true);  // the AI is writing
            case ERROR -> Dialogs.error(window(), "Server", message.getText());
            default -> {
                if (message.getType().isChat()) {
                    // A private message is filed under its SENDER's chat; a group message under General.
                    deliver(message.isPrivate() ? privateChat(message.getSender()) : general, message);
                }
            }
        }
    }

    /** Adds a message to a conversation: shows it if that chat is open, otherwise counts it as unread. */
    private void deliver(Conversation conversation, Message message) {
        conversation.addMessage(message);
        if (conversation == current) {
            chatPane.append(message);
        } else {
            conversation.incrementUnread();
        }
    }

    /** New list of online users: update the counter, create missing chats, update online dots. */
    private void updateUsers(List<String> users) {
        // Count people, not the assistant (a "stream" processes the list in one expression).
        onlineCount.set((int) users.stream().filter(u -> !u.equals(Config.AI_BOT_NAME)).count());
        Set<String> online = new HashSet<>(users);
        for (String user : users) {
            if (!user.equals(me)) {
                privateChat(user); // makes sure a chat exists for everyone online
            }
        }
        for (Conversation c : conversations.values()) {
            if (!c.isGroup()) {
                c.setOnline(online.contains(c.getId()));
            }
        }
    }

    /** The private chat with this user, created the first time it's needed. */
    private Conversation privateChat(String user) {
        // computeIfAbsent: return the existing value, or create, store and return a new one.
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
        String name = "voice-" + System.currentTimeMillis() + ".wav"; // unique-ish name from the clock
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

    /**
     * Sends a message and shows it right away in the open chat.
     * The server doesn't send our own messages back to us, so we add them ourselves.
     */
    private void post(Message message) {
        client.send(message);    // non-blocking: written by the "chat-sender" thread
        deliver(current, message);
    }

    /** The window this view is in (for pop-ups), or null before it is shown. */
    private Window window() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
