package mobile.model;

import common.Message;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A chat in the list: the public "General" group, the AI assistant, or a private chat with one user.
 * Same model as the desktop's client.model.Conversation, without JavaFX properties:
 * screens are told about changes through {@link mobile.ChatSession.Listener}.
 */
public class Conversation {

    public static final String GENERAL_ID = "#general";

    /** Kinds in list order: General is pinned first, the AI assistant second. */
    public enum Kind { GROUP, AI, PRIVATE }

    /** Pinned chats first, then most recent activity first, then alphabetical. */
    public static final Comparator<Conversation> ORDER = Comparator
            .comparing((Conversation c) -> c.getKind())
            .thenComparing(Conversation::getLastActivity, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Conversation::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final String id;
    private final String title;
    private final Kind kind;
    private final List<Message> messages = new ArrayList<>();
    private LocalDateTime lastActivity;
    private int unread;
    private boolean online = true;
    private boolean typing;

    private Conversation(String id, String title, Kind kind) {
        this.id = id;
        this.title = title;
        this.kind = kind;
    }

    public static Conversation general() {
        return new Conversation(GENERAL_ID, "General", Kind.GROUP);
    }

    public static Conversation aiChat(String botName) {
        return new Conversation(botName, botName, Kind.AI);
    }

    public static Conversation privateChat(String username) {
        return new Conversation(username, username, Kind.PRIVATE);
    }

    public void addMessage(Message message) {
        messages.add(message);
        lastActivity = message.getTimestamp();
        typing = false; // a message arrived, so whoever was typing is done
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /** Recipient to put on outgoing messages ({@code null} = General). */
    public String getRecipient() {
        return isGroup() ? null : id;
    }

    public void incrementUnread() {
        unread++;
    }

    public void markRead() {
        unread = 0;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isGroup() {
        return kind == Kind.GROUP;
    }

    public boolean isAi() {
        return kind == Kind.AI;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public int getUnread() {
        return unread;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }
}
