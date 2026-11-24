package client.ui;

import common.MessageType;

import java.io.File;

/**
 * What the chat screen can ask to send. Implemented by {@link ChatView}.
 */
public interface ChatActions {

    void sendText(String text);

    void sendFile(File file, MessageType type);

    void sendVoice(byte[] wav);
}
