package client.network;

import common.Message;

/**
 * Receives events from the network thread. Implementations must switch to the
 * UI thread themselves before touching the UI (JavaFX: Platform.runLater,
 * Android: Handler / runOnUiThread).
 */
public interface MessageListener {

    void onMessage(Message message);

    void onDisconnected(String reason);
}
