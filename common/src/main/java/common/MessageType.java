package common;

/**
 * The list of every kind of {@link Message} that can travel between a client and the server.
 * <p>
 * An {@code enum} is a fixed list of named constants: a variable of type {@code MessageType}
 * can only hold one of the values below. Every Message carries one of them in its
 * {@code type} field, and the receiver uses a {@code switch} on it to decide what to do
 * (see ChatView.handle on the desktop and ChatSession.handle on Android).
 */
public enum MessageType {

    // --- connection / control: these are never shown as chat bubbles ---
    JOIN,       // client -> server : "I want to join with this name"
    ACCEPTED,   // server -> client : join succeeded
    ERROR,      // server -> client : something went wrong
    LEAVE,      // client -> server : "I'm leaving"
    USER_LIST,  // server -> client : who is online now
    SYSTEM,     // server -> client : informational text ("Ali joined")
    TYPING,     // server -> client : the sender (e.g. the AI assistant) is writing a reply

    // --- chat content: these become bubbles in a conversation ---
    TEXT,       // a normal text message
    IMAGE,      // a picture (png, jpg, gif, bmp)
    AUDIO,      // an audio file (mp3, wav, ...)
    VOICE,      // a voice note recorded in the app
    FILE;       // any other file (pdf, zip, ...)

    /**
     * True for the types that carry a file in {@link Message#getData()}.
     * Example: {@code MessageType.IMAGE.isAttachment()} is true, {@code TEXT.isAttachment()} is false.
     */
    public boolean isAttachment() {
        return this == IMAGE || this == AUDIO || this == VOICE || this == FILE;
    }

    /**
     * True for everything a user writes or sends in a chat (text or an attachment).
     * The server only routes "chat" messages from clients; it ignores anything else.
     */
    public boolean isChat() {
        return this == TEXT || isAttachment();
    }
}
