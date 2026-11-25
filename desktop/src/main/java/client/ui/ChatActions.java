package client.ui;

import common.MessageType;

import java.io.File;

/**
 * What the chat screen can ask to send. Implemented by {@link ChatView}.
 * <p>
 * The input bar (InputBar) and drag-and-drop (ChatPane) only know this small interface,
 * not the whole ChatView: they say "send this", and ChatView decides how (to which
 * conversation, through the network).
 */
public interface ChatActions {

    /** Sends a text message to the conversation that is open. */
    void sendText(String text);

    /** Sends a file (IMAGE, AUDIO or FILE) to the conversation that is open. */
    void sendFile(File file, MessageType type);

    /** Sends a recorded voice note (WAV bytes). */
    void sendVoice(byte[] wav);
}
