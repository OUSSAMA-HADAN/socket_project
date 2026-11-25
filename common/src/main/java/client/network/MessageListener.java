package client.network;

import common.Message;

/**
 * Receives events from the network thread. Implementations must switch to the
 * UI thread themselves before touching the UI (JavaFX: Platform.runLater,
 * Android: Handler / runOnUiThread).
 * <p>
 * An {@code interface} is a contract: it lists methods without code. A class that
 * "implements MessageListener" promises to provide these two methods. ChatClient then
 * calls them without knowing whether it talks to the desktop app, the Android app or a test.
 */
public interface MessageListener {

    /** Called for every message that arrives from the server (on the "chat-listener" thread). */
    void onMessage(Message message);

    /** Called once when the connection is lost (server stopped, network down...). */
    void onDisconnected(String reason);
}
