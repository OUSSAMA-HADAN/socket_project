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
 * Used exactly the same way by the desktop app, the Android app and the tests:
 * <pre>
 *   ChatClient client = ChatClient.connect("localhost", 5050, "alice"); // 1. connect + join
 *   client.startListening(myListener);                                  // 2. receive
 *   client.send(Message.text("alice", null, "Hello!"));                 // 3. send
 *   client.disconnect();                                                // 4. leave
 * </pre>
 */
public class ChatClient {

    /** How long to wait for the server before giving up (5 seconds). */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String username;
    private final Socket socket;              // the TCP connection to the server
    private final ObjectOutputStream out;     // turns Message objects into bytes on the socket
    private final ObjectInputStream in;       // turns bytes from the socket back into Messages
    /**
     * A pool with exactly ONE thread, named "chat-sender". Every send() puts a small task in its
     * queue; the thread runs them one by one, in order. The caller (the UI) never waits.
     */
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-sender");
        t.setDaemon(true); // a daemon thread doesn't keep the program alive when the window closes
        return t;
    });
    /**
     * volatile: written by one thread, read by others. Without it a thread might keep
     * an old cached value and never see that we stopped.
     */
    private volatile boolean running = true;

    /** Private: use {@link #connect} to create a connected client. */
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
            // 1. Open the TCP connection (fails after 5 s if nobody answers).
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS); // reads also give up after 5 s during the handshake

            // 2. Create the object streams. Order matters: create the OUTPUT stream first and
            //    flush it, because the input stream's constructor waits for the other side's
            //    stream header. If both sides created the input first, they would wait forever.
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // 3. Say who we are.
            out.writeObject(Message.join(username));
            out.flush();

            // 4. Wait for the server's answer: ACCEPTED, or ERROR with a reason.
            //    "instanceof Message reply" checks the type AND names the variable (Java 16+).
            if (!(in.readObject() instanceof Message reply)) {
                throw new IOException("Unexpected answer from the server.");
            }
            if (reply.getType() == MessageType.ERROR) {
                throw new IOException(reply.getText()); // e.g. 'The name "alice" is already taken.'
            }
            if (reply.getType() != MessageType.ACCEPTED) {
                throw new IOException("Unexpected answer from the server.");
            }
            socket.setSoTimeout(0); // 0 = no timeout: from now on the listener may wait forever for messages
            return new ChatClient(username, socket, out, in);
        } catch (ClassNotFoundException e) {
            socket.close();
            throw new IOException("Protocol error.", e);
        } catch (IOException e) {
            socket.close(); // never leave a half-open connection behind
            throw e;
        }
    }

    public String getUsername() {
        return username;
    }

    /**
     * Starts the "chat-listener" thread, which reads messages forever and hands each one to the
     * listener. It runs until the connection closes.
     */
    public void startListening(MessageListener listener) {
        Thread reader = new Thread(() -> {
            try {
                while (running) {
                    // readObject() BLOCKS: the thread sleeps here until a message arrives.
                    if (in.readObject() instanceof Message message) {
                        listener.onMessage(message);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                // The socket was closed. If we didn't close it ourselves (running is still true),
                // the connection was lost: tell the listener once.
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
            sender.execute(() -> write(message)); // queue the work and return immediately
        } catch (RejectedExecutionException ignored) {
            // already disconnecting
        }
    }

    /** Says goodbye to the server (LEAVE), waits up to 2 s for pending sends, then closes the socket. */
    public void disconnect() {
        boolean wasRunning = running;
        running = false; // the listener thread will now stop quietly
        if (wasRunning) {
            try {
                sender.execute(() -> write(Message.leave(username)));
            } catch (RejectedExecutionException ignored) {
            }
        }
        sender.shutdown(); // accept no new tasks, finish the queued ones
        try {
            sender.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // keep the "interrupted" flag for the caller
        }
        try {
            socket.close(); // also wakes up the listener thread blocked in readObject()
        } catch (IOException ignored) {
        }
    }

    /** Runs on the "chat-sender" thread only, so two writes never mix their bytes. */
    private void write(Message message) {
        try {
            out.writeObject(message);
            // reset(): forget the objects already sent. Otherwise the stream keeps a reference to
            // every message (and its attachment) to avoid resending duplicates: a memory leak.
            out.reset();
            out.flush(); // push the bytes onto the network now
        } catch (IOException e) {
            // the listener thread will notice the broken connection
        }
    }
}
