package common;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The single unit of communication between clients and the server.
 * Instances are immutable and travel over the socket with Java serialization.
 * <p>
 * A {@code null} recipient means the message belongs to the public "General" group;
 * otherwise it is a private message for that user.
 * <p>
 * Shared by the server, the desktop client and the Android app: keep it to APIs that exist
 * on Android 8.0+ (no {@code List.of}, no Java 21+ library calls) and never change the fields
 * without bumping {@code serialVersionUID} on every side.
 * <p>
 * <b>Beginner notes</b>
 * <ul>
 *   <li><b>Serializable</b>: Java can turn this object into bytes ({@code ObjectOutputStream})
 *       and rebuild it on the other computer ({@code ObjectInputStream}). That's how a whole
 *       Message, with its text, file bytes and date, crosses the network in one call.</li>
 *   <li><b>Immutable</b>: every field is {@code final} and there are no setters, so once a
 *       Message is created nobody can change it. That makes it safe to share between threads.</li>
 *   <li><b>Factories</b>: the constructor is {@code private}; you create messages with the
 *       readable static methods below, e.g. {@code Message.text("alice", "bob", "Hi")}.</li>
 * </ul>
 */
public final class Message implements Serializable {

    /**
     * Version number of this class's "shape". Both sides must have the same number,
     * otherwise deserialization fails. Change it if you add or remove a field.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    // ---------------------------------------------------------------- fields (all final = immutable)

    private final String id;                 // unique id, e.g. "3f2b..." (used to find a message again)
    private final MessageType type;          // what kind of message this is (TEXT, IMAGE, JOIN...)
    private final String sender;             // who sent it (null for messages from the server itself)
    private final String recipient;          // who it's for; null = the "General" group
    private final String text;               // the text (TEXT, SYSTEM, ERROR), otherwise null
    private final String fileName;           // name of the attached file, e.g. "photo.jpg"
    private final byte[] data;               // the attached file's content (attachments only)
    private final ArrayList<String> users;   // the online users (USER_LIST only)
    private final LocalDateTime timestamp;   // when the message was created

    /** Private: use one of the factory methods below instead. */
    private Message(MessageType type, String sender, String recipient, String text,
                    String fileName, byte[] data, List<String> users) {
        this.id = UUID.randomUUID().toString(); // random, practically unique id
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.text = text;
        this.fileName = fileName;
        this.data = data;
        // Copy the list into an ArrayList: it is serializable everywhere (also on Android),
        // and nobody outside can modify our copy.
        this.users = users == null ? null : new ArrayList<>(users);
        this.timestamp = LocalDateTime.now();
    }

    // ---------------------------------------------------------------- factories
    // One method per kind of message, so the code that sends messages reads like a sentence.

    /** Client -> server: first message after connecting, asks to join with this name. */
    public static Message join(String username) {
        return new Message(MessageType.JOIN, username, null, null, null, null, null);
    }

    /** Server -> client: the JOIN was accepted. */
    public static Message accepted() {
        return new Message(MessageType.ACCEPTED, null, null, null, null, null, null);
    }

    /** Server -> client: something went wrong; the reason is in the text. */
    public static Message error(String reason) {
        return new Message(MessageType.ERROR, null, null, reason, null, null, null);
    }

    /** Client -> server: the user logs out. */
    public static Message leave(String username) {
        return new Message(MessageType.LEAVE, username, null, null, null, null, null);
    }

    /** Server -> clients: an information line such as "alice joined the chat". */
    public static Message system(String text) {
        return new Message(MessageType.SYSTEM, null, null, text, null, null, null);
    }

    /** "sender is writing a reply to recipient" (used by the AI assistant). */
    public static Message typing(String sender, String recipient) {
        return new Message(MessageType.TYPING, sender, recipient, null, null, null, null);
    }

    /** Server -> clients: the list of everyone online. */
    public static Message userList(List<String> users) {
        return new Message(MessageType.USER_LIST, null, null, null, null, null, users);
    }

    /** A text message. recipient = null sends it to the General group. */
    public static Message text(String sender, String recipient, String text) {
        return new Message(MessageType.TEXT, sender, recipient, text, null, null, null);
    }

    /**
     * A message with a file (photo, audio, voice note, document).
     * Throws an exception if {@code type} is not an attachment type (e.g. TEXT), to catch mistakes early.
     */
    public static Message attachment(MessageType type, String sender, String recipient,
                                     String fileName, byte[] data) {
        if (!type.isAttachment()) {
            throw new IllegalArgumentException(type + " is not an attachment type");
        }
        return new Message(type, sender, recipient, null, fileName, data, null);
    }

    // ---------------------------------------------------------------- getters (read-only access)

    public String getId() {
        return id;
    }

    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    /** True for a private message (it has a recipient), false for the General group. */
    public boolean isPrivate() {
        return recipient != null;
    }

    public String getText() {
        return text;
    }

    public String getFileName() {
        return fileName;
    }

    /** Raw attachment bytes (not copied, treat as read-only). */
    public byte[] getData() {
        return data;
    }

    /** Size of the attached file in bytes (0 if there is none). */
    public long getSize() {
        return data == null ? 0 : data.length;
    }

    /**
     * The online users. Returns a read-only copy, so callers can't change the message's list
     * (keeping the "immutable" promise).
     */
    public List<String> getUsers() {
        return users == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(users));
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
