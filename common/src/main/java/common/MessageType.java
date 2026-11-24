package common;

public enum MessageType {

    // --- connection / control ---
    JOIN,       // client -> server : "I want to join with this name"
    ACCEPTED,   // server -> client : join succeeded
    ERROR,      // server -> client : something went wrong
    LEAVE,      // client -> server : "I'm leaving"
    USER_LIST,  // server -> client : who is online now
    SYSTEM,     // server -> client : informational text ("Ali joined")
    TYPING,     // server -> client : the sender (e.g. the AI assistant) is writing a reply

    // --- chat content ---
    TEXT,
    IMAGE,
    AUDIO,      // an audio file (mp3, wav, ...)
    VOICE,      // a voice note recorded in the app
    FILE;

    public boolean isAttachment() {
        return this == IMAGE || this == AUDIO || this == VOICE || this == FILE;
    }

    public boolean isChat() {
        return this == TEXT || isAttachment();
    }
}
