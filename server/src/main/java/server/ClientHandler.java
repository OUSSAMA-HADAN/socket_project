package server;

import common.Config;
import common.Message;
import common.MessageType;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Serves one connected client.
 * <p>
 * Two threads per client:
 * <ul>
 *   <li>the <b>reader</b> (this Runnable) receives messages and hands them to the server;</li>
 *   <li>the <b>writer</b> drains an outgoing queue to the socket.</li>
 * </ul>
 * Because other threads only put messages in the queue, a slow client never blocks
 * the threads of the other clients.
 * <p>
 * <b>Life of a ClientHandler</b>: handshake (JOIN -> ACCEPTED or ERROR) -> start the writer
 * thread -> read messages until the client leaves -> unregister and close.
 */
class ClientHandler implements Runnable {

    /**
     * A special "poison pill" message. When the writer thread takes it from the queue, it stops.
     * Compared with == (same object), so no real message can ever be mistaken for it.
     */
    private static final Message STOP = Message.system("__stop__");

    private final Socket socket;
    private final ChatServer server;
    /**
     * The outgoing messages waiting to be written to this client.
     * A BlockingQueue is thread-safe: many threads can offer() into it while the writer thread
     * take()s from it; take() waits (sleeps) while the queue is empty.
     */
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>();

    private ObjectOutputStream out;
    /** Set after a successful handshake; volatile because the server's threads read it too. */
    private volatile String username;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    String getUsername() {
        return username;
    }

    /** Thread-safe: queues a message for this client. */
    void send(Message message) {
        outbox.offer(message); // offer() adds and returns immediately: the caller never waits
    }

    /** The reader thread: runs on a pool thread from ChatServer. */
    @Override
    public void run() {
        try {
            // Output stream first, then input (see ChatClient.connect for why the order matters).
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            if (!handshake(in)) {
                return; // name refused: the finally block below closes the connection
            }
            // Start this client's writer thread ("writer-alice"). Daemon: it won't keep the JVM alive.
            Thread.ofPlatform().daemon().name("writer-" + username).start(this::writeLoop);
            server.onJoined(this);
            readLoop(in); // returns when the client sends LEAVE; throws when the connection breaks
        } catch (EOFException | SocketException e) {
            // client closed the connection
        } catch (IOException | ClassNotFoundException e) {
            ChatServer.log("Error with " + username + ": " + e.getMessage());
        } finally {
            // Whatever happened: remove the user, stop the writer thread, close the socket.
            server.unregister(this);
            outbox.offer(STOP);
            close();
        }
    }

    // ---------------------------------------------------------------- protocol

    /** First exchange: the client must send JOIN with a valid, free name. Returns true if accepted. */
    private boolean handshake(ObjectInputStream in) throws IOException, ClassNotFoundException {
        if (!(in.readObject() instanceof Message join) || join.getType() != MessageType.JOIN) {
            writeNow(Message.error("Protocol error: expected JOIN"));
            return false;
        }
        String name = join.getSender() == null ? "" : join.getSender().trim();
        String problem = validateName(name);              // null = the name is valid
        if (problem == null && !server.register(name, this)) {
            problem = "The name \"" + name + "\" is already taken.";
        }
        if (problem != null) {
            writeNow(Message.error(problem));
            return false;
        }
        username = name;
        writeNow(Message.accepted()); // written before the writer thread starts, so it is always first
        return true;
    }

    /** Reads messages from this client forever and passes the valid ones to the server. */
    private void readLoop(ObjectInputStream in) throws IOException, ClassNotFoundException {
        while (true) {
            // BLOCKS until the client sends something.
            if (!(in.readObject() instanceof Message message)) {
                continue;
            }
            if (message.getType() == MessageType.LEAVE) {
                return; // the client logs out
            }
            // Security: only chat messages, and only in the client's own name
            // (nobody may send a message pretending to be someone else).
            if (!message.getType().isChat() || !username.equals(message.getSender())) {
                continue; // ignore unknown messages and spoofed senders
            }
            if (message.getSize() > Config.MAX_ATTACHMENT_BYTES) {
                send(Message.error("Attachment is too large."));
                continue;
            }
            server.route(message);
        }
    }

    /** The writer thread: takes messages from the queue and writes them, one at a time. */
    private void writeLoop() {
        try {
            while (true) {
                Message message = outbox.take(); // sleeps until there is something to send
                if (message == STOP) {
                    return; // poison pill: the client is gone, end the thread
                }
                writeNow(message); // if this client's network is slow, only THIS thread waits
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            close(); // writing failed: close, and the reader thread will clean up
        }
    }

    /** Writes one message to the socket immediately. */
    private void writeNow(Message message) throws IOException {
        out.writeObject(message);
        out.reset(); // forget already-sent objects, otherwise the stream keeps every attachment in memory
        out.flush();
    }

    /** Returns why a name is refused, or null if it is fine. */
    private static String validateName(String name) {
        if (name.isEmpty()) {
            return "The name cannot be empty.";
        }
        if (name.length() > Config.MAX_USERNAME_LENGTH) {
            return "The name is too long (max " + Config.MAX_USERNAME_LENGTH + " characters).";
        }
        if (name.contains("#")) {
            return "The name cannot contain '#'.";
        }
        if (name.equalsIgnoreCase(Config.AI_BOT_NAME)) {
            return "\"" + Config.AI_BOT_NAME + "\" is reserved for the chatbot.";
        }
        return null;
    }

    /** Closes the connection; the reader thread then ends and unregisters the user. */
    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
