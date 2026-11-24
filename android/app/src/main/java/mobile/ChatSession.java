package mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import client.network.ChatClient;
import client.network.MessageListener;
import common.Config;
import common.Message;
import common.MessageType;
import mobile.model.Conversation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The phone's one connection to the server and its conversations, shared by every screen
 * and by {@link ChatService} (which keeps the app alive in the background).
 * <p>
 * Threads: the {@link ChatClient} (same class as the desktop) reads on its listener thread
 * and writes on its sender thread. Everything it receives is handed to Android's main thread,
 * the only one allowed to touch views (the JavaFX equivalent is Platform.runLater), so all the
 * state below is only touched on the main thread and needs no locks.
 * <p>
 * Routing is the same as the desktop's ChatView: the server doesn't echo our own messages, so
 * what we send is added to the conversation locally.
 */
public final class ChatSession {

    /** Called on the main thread. Implement only what you need. */
    public interface Listener {
        /** A message was added to a conversation (received or sent by me). */
        default void onMessage(Conversation conversation, Message message) {
        }

        /** Order, unread counts, online or typing state changed. */
        default void onConversationsChanged() {
        }

        default void onServerError(String text) {
        }

        /** @param reason why the connection ended, or null when the user logged out */
        default void onSessionEnded(String reason) {
        }
    }

    private static ChatSession current;

    private final Context app;
    private final ChatClient client;
    private final String me;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Conversation general = Conversation.general();
    private int onlineCount = 1;
    /** The chat the user is looking at (step 4), or null. */
    private Conversation visible;
    private boolean active = true;

    private ChatSession(Context context, ChatClient client) {
        this.app = context.getApplicationContext();
        this.client = client;
        this.me = client.getUsername();
        conversations.put(general.getId(), general);
        Conversation ai = Conversation.aiChat(Config.AI_BOT_NAME); // always present, pinned under General
        conversations.put(ai.getId(), ai);
    }

    /** Main thread: takes over a freshly connected client and starts listening. */
    public static ChatSession start(Context context, ChatClient client) {
        if (current != null) {
            current.logout();
        }
        ChatSession session = new ChatSession(context, client);
        current = session;
        client.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                session.mainThread.post(() -> session.handle(message));
            }

            @Override
            public void onDisconnected(String reason) {
                session.mainThread.post(() -> session.end(reason));
            }
        });
        return session;
    }

    /** The live session, or null when logged out. */
    public static ChatSession current() {
        return current;
    }

    // ---------------------------------------------------------------- queries

    public String username() {
        return me;
    }

    /** Connected people, not counting the AI assistant (same as the desktop header). */
    public int onlineCount() {
        return onlineCount;
    }

    /** All chats in list order (General, AI Assistant, then the most recent). */
    public List<Conversation> conversations() {
        List<Conversation> list = new ArrayList<>(conversations.values());
        list.sort(Conversation.ORDER);
        return list;
    }

    public Conversation conversation(String id) {
        return conversations.get(id);
    }

    public int totalUnread() {
        int total = 0;
        for (Conversation c : conversations.values()) {
            total += c.getUnread();
        }
        return total;
    }

    // ---------------------------------------------------------------- actions

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** The chat screen calls this when it shows a conversation. */
    public void setVisible(Conversation conversation) {
        visible = conversation;
        if (conversation != null && conversation.getUnread() > 0) {
            conversation.markRead();
            changed();
        }
        if (conversation != null) {
            Notifications.cancel(app, conversation);
        }
    }

    /**
     * The chat screen showing {@code conversation} was hidden. Ignored if another chat screen
     * became visible meanwhile (Android starts the new screen before stopping the old one).
     */
    public void leave(Conversation conversation) {
        if (visible == conversation) {
            visible = null;
        }
    }

    public void sendText(Conversation conversation, String text) {
        post(conversation, Message.text(me, conversation.getRecipient(), text));
    }

    public void sendAttachment(Conversation conversation, MessageType type, String fileName, byte[] data) {
        post(conversation, Message.attachment(type, me, conversation.getRecipient(), fileName, data));
    }

    /** User logout. The socket is closed on a background thread (no network on the main thread). */
    public void logout() {
        end(null);
    }

    // ---------------------------------------------------------------- main thread internals

    private void post(Conversation conversation, Message message) {
        client.send(message); // non-blocking: written by the client's sender thread
        deliver(conversation, message);
    }

    private void handle(Message message) {
        if (!active) {
            return;
        }
        switch (message.getType()) {
            case USER_LIST:
                updateUsers(message.getUsers());
                break;
            case SYSTEM:
                deliver(general, message);
                break;
            case TYPING:
                privateChat(message.getSender()).setTyping(true);
                changed();
                break;
            case ERROR:
                for (Listener l : new ArrayList<>(listeners)) {
                    l.onServerError(message.getText());
                }
                break;
            default:
                if (message.getType().isChat()) {
                    deliver(message.isPrivate() ? privateChat(message.getSender()) : general, message);
                }
        }
    }

    private void deliver(Conversation conversation, Message message) {
        conversation.addMessage(message);
        boolean mine = me.equals(message.getSender());
        boolean seen = conversation == visible && ChatApp.isInForeground();
        if (!mine && !seen) {
            conversation.incrementUnread();
        }
        for (Listener l : new ArrayList<>(listeners)) {
            l.onMessage(conversation, message);
        }
        changed();
        if (!mine && message.getType().isChat() && !ChatApp.isInForeground()) {
            Notifications.showMessage(app, this, conversation);
        }
    }

    private void updateUsers(List<String> users) {
        int people = 0;
        for (String user : users) {
            if (!user.equals(Config.AI_BOT_NAME)) {
                people++;
            }
            if (!user.equals(me)) {
                privateChat(user);
            }
        }
        onlineCount = people;
        Set<String> online = new HashSet<>(users);
        for (Conversation c : conversations.values()) {
            if (!c.isGroup()) {
                c.setOnline(online.contains(c.getId()));
            }
        }
        changed();
    }

    private Conversation privateChat(String user) {
        Conversation c = conversations.get(user);
        if (c == null) {
            c = Conversation.privateChat(user);
            conversations.put(user, c);
        }
        return c;
    }

    private void changed() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onConversationsChanged();
        }
    }

    private void end(String reason) {
        if (!active) {
            return;
        }
        active = false;
        if (current == this) {
            current = null;
        }
        List<Listener> toNotify = new ArrayList<>(listeners);
        listeners.clear();
        new Thread(client::disconnect, "chat-disconnect").start();
        Notifications.cancelAllMessages(app);
        for (Listener l : toNotify) {
            l.onSessionEnded(reason);
        }
    }
}
