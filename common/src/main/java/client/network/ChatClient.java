package client.network;

import common.Message;
import common.MessageType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Connection of one client to the server.
 * <p>
 * Threads:
 * <ul>
 *   <li>a <b>listener</b> thread blocks on the socket and forwards incoming messages;</li>
 *   <li>a single-thread <b>sender</b> executor writes outgoing messages, so sending a big
 *       file never freezes the user interface.</li>
 * </ul>
 */
public class ChatClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String username;
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-sender");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    private ChatClient(String username, Socket socket, ObjectOutputStream out, ObjectInputStream in) {
        this.username = username;
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    /** Opens the connection and performs the JOIN handshake. Blocking: call it off the UI thread. */
    public static ChatClient connect(String host, int port, String username) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject(Message.join(username));
            out.flush();

            if (!(in.readObject() instanceof Message reply)) {
                throw new IOException("Unexpected answer from the server.");
            }
            if (reply.getType() == MessageType.ERROR) {
                throw new IOException(reply.getText());
            }
            if (reply.getType() != MessageType.ACCEPTED) {
                throw new IOException("Unexpected answer from the server.");
            }
            socket.setSoTimeout(0);
            return new ChatClient(username, socket, out, in);
        } catch (ClassNotFoundException e) {
            socket.close();
            throw new IOException("Protocol error.", e);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    public String getUsername() {
        return username;
    }

    public void startListening(MessageListener listener) {
        Thread reader = new Thread(() -> {
            try {
                while (running) {
                    if (in.readObject() instanceof Message message) {
                        listener.onMessage(message);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (running) {
                    running = false;
                    listener.onDisconnected("The connection to the server was lost.");
                }
            }
        }, "chat-listener");
        reader.setDaemon(true);
        reader.start();
    }

    /** Non-blocking: the message is written by the sender thread. */
    public void send(Message message) {
        if (!running) {
            return;
        }
        try {
            sender.execute(() -> write(message));
        } catch (RejectedExecutionException ignored) {
            // already disconnecting
        }
    }

    public void disconnect() {
        boolean wasRunning = running;
        running = false;
        if (wasRunning) {
            try {
                sender.execute(() -> write(Message.leave(username)));
            } catch (RejectedExecutionException ignored) {
            }
        }
        sender.shutdown();
        try {
            sender.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void write(Message message) {
        try {
            out.writeObject(message);
            out.reset();
            out.flush();
        } catch (IOException e) {
            // the listener thread will notice the broken connection
        }
    }
}
