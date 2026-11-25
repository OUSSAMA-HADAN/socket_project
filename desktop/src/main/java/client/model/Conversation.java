package client.model;

import common.Message;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A chat shown in the sidebar: either the public "General" group or a private chat with one user.
 * <p>
 * <b>JavaFX properties</b>: fields like {@code unread} are "properties", values that others can
 * watch. When the unread count changes, the sidebar notices automatically and redraws the badge,
 * without us calling any refresh method.
 */
public class Conversation {

    /** Id of the General group. "#" is forbidden in usernames, so no user can clash with it. */
    public static final String GENERAL_ID = "#general";

    /** Kinds in sidebar order: General is pinned first, the AI assistant second. */
    public enum Kind { GROUP, AI, PRIVATE }

    /** Pinned chats first, then most recent activity first, then alphabetical. */
    public static final Comparator<Conversation> ORDER = Comparator
            .comparing((Conversation c) -> c.getKind())   // 1. GROUP, then AI, then PRIVATE
            .thenComparing(Conversation::getLastActivity, Comparator.nullsLast(Comparator.reverseOrder())) // 2. newest first
            .thenComparing(Conversation::getTitle, String.CASE_INSENSITIVE_ORDER); // 3. A-Z

    private final String id;     // "#general", "AI Assistant", or the other user's name
    private final String title;  // what the sidebar shows
    private final Kind kind;
    private final List<Message> messages = new ArrayList<>(); // the history, oldest first

    // Observable values: the sidebar re-sorts and redraws when one of them changes.
    private final ObjectProperty<LocalDateTime> lastActivity = new SimpleObjectProperty<>();
    private final IntegerProperty unread = new SimpleIntegerProperty();
    private final BooleanProperty online = new SimpleBooleanProperty(true);
    private final BooleanProperty typing = new SimpleBooleanProperty(false);

    /** Private: use the factories below. */
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

    /** Adds a message to the history and updates "last activity" (which re-sorts the sidebar). */
    public void addMessage(Message message) {
        messages.add(message);
        lastActivity.set(message.getTimestamp());
        typing.set(false); // a message arrived, so whoever was typing is done
    }

    /** Read-only view of the history (callers can't add or remove messages directly). */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    /** Recipient to put on outgoing messages ({@code null} = General). */
    public String getRecipient() {
        return isGroup() ? null : id;
    }

    public void incrementUnread() {
        unread.set(unread.get() + 1);
    }

    public void markRead() {
        unread.set(0);
    }

    /** Properties that should refresh the sidebar when they change. */
    public Observable[] observables() {
        return new Observable[]{lastActivity, unread, online, typing};
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

    public boolean isTyping() {
        return typing.get();
    }

    public void setTyping(boolean value) {
        typing.set(value);
    }

    /** The property itself, so a label can "bind" to it and update by itself. */
    public BooleanProperty typingProperty() {
        return typing;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity.get();
    }

    public int getUnread() {
        return unread.get();
    }

    public boolean isOnline() {
        return online.get();
    }

    public void setOnline(boolean value) {
        online.set(value);
    }

    public BooleanProperty onlineProperty() {
        return online;
    }
}
