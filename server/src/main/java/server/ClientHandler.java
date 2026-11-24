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
 */
class ClientHandler implements Runnable {

    private static final Message STOP = Message.system("__stop__");

    private final Socket socket;
    private final ChatServer server;
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>();

    private ObjectOutputStream out;
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
        outbox.offer(message);
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            if (!handshake(in)) {
                return;
            }
            Thread.ofPlatform().daemon().name("writer-" + username).start(this::writeLoop);
            server.onJoined(this);
            readLoop(in);
        } catch (EOFException | SocketException e) {
            // client closed the connection
        } catch (IOException | ClassNotFoundException e) {
            ChatServer.log("Error with " + username + ": " + e.getMessage());
        } finally {
            server.unregister(this);
            outbox.offer(STOP);
            close();
        }
    }

    // ---------------------------------------------------------------- protocol

    private boolean handshake(ObjectInputStream in) throws IOException, ClassNotFoundException {
        if (!(in.readObject() instanceof Message join) || join.getType() != MessageType.JOIN) {
            writeNow(Message.error("Protocol error: expected JOIN"));
            return false;
        }
        String name = join.getSender() == null ? "" : join.getSender().trim();
        String problem = validateName(name);
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

    private void readLoop(ObjectInputStream in) throws IOException, ClassNotFoundException {
        while (true) {
            if (!(in.readObject() instanceof Message message)) {
                continue;
            }
            if (message.getType() == MessageType.LEAVE) {
                return;
            }
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

    private void writeLoop() {
        try {
            while (true) {
                Message message = outbox.take();
                if (message == STOP) {
                    return;
                }
                writeNow(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            close();
        }
    }

    private void writeNow(Message message) throws IOException {
        out.writeObject(message);
        out.reset(); // forget already-sent objects, otherwise the stream keeps every attachment in memory
        out.flush();
    }

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
