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
 */
public final class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final MessageType type;
    private final String sender;
    private final String recipient;
    private final String text;
    private final String fileName;
    private final byte[] data;
    private final ArrayList<String> users;
    private final LocalDateTime timestamp;

    private Message(MessageType type, String sender, String recipient, String text,
                    String fileName, byte[] data, List<String> users) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.text = text;
        this.fileName = fileName;
        this.data = data;
        this.users = users == null ? null : new ArrayList<>(users);
        this.timestamp = LocalDateTime.now();
    }

    // ---------------------------------------------------------------- factories

    public static Message join(String username) {
        return new Message(MessageType.JOIN, username, null, null, null, null, null);
    }

    public static Message accepted() {
        return new Message(MessageType.ACCEPTED, null, null, null, null, null, null);
    }

    public static Message error(String reason) {
        return new Message(MessageType.ERROR, null, null, reason, null, null, null);
    }

    public static Message leave(String username) {
        return new Message(MessageType.LEAVE, username, null, null, null, null, null);
    }

    public static Message system(String text) {
        return new Message(MessageType.SYSTEM, null, null, text, null, null, null);
    }

    public static Message typing(String sender, String recipient) {
        return new Message(MessageType.TYPING, sender, recipient, null, null, null, null);
    }

    public static Message userList(List<String> users) {
        return new Message(MessageType.USER_LIST, null, null, null, null, null, users);
    }

    public static Message text(String sender, String recipient, String text) {
        return new Message(MessageType.TEXT, sender, recipient, text, null, null, null);
    }

    public static Message attachment(MessageType type, String sender, String recipient,
                                     String fileName, byte[] data) {
        if (!type.isAttachment()) {
            throw new IllegalArgumentException(type + " is not an attachment type");
        }
        return new Message(type, sender, recipient, null, fileName, data, null);
    }

    // ---------------------------------------------------------------- getters

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

    public long getSize() {
        return data == null ? 0 : data.length;
    }

    public List<String> getUsers() {
        return users == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(users));
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
