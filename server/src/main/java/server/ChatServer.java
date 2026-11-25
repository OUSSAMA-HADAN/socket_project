package server;

import common.Config;
import common.Message;
import common.MessageType;
import server.ai.AiBot;
import server.ai.AiSettings;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accepts client connections and routes messages between them.
 * <p>
 * Every connected client is served by its own {@link ClientHandler} running on a thread
 * from a pool. The registry of online users is a {@link ConcurrentHashMap} because it is
 * read and modified by all those threads at the same time.
 * <p>
 * <b>Big picture</b>
 * <pre>
 *   main thread:        accept() a new connection -> create a ClientHandler -> give it to the pool
 *   pool thread (x N):  ClientHandler reads that client's messages -> calls route() here
 *   route():            puts the message in the right client(s)' outgoing queue (never blocks)
 * </pre>
 */
public class ChatServer {

    /** Format of the time at the start of each log line, e.g. "14:05:09". */
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    /**
     * Who is online: username -> the ClientHandler serving that user.
     * A plain HashMap would break when several threads change it at once; ConcurrentHashMap is
     * built for that (thread-safe without us writing any lock).
     */
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    /** Thread pool for the ClientHandlers: creates a thread when needed, reuses idle ones. */
    private final ExecutorService pool = Executors.newCachedThreadPool();
    /**
     * The AI assistant. "this::deliverFromBot" is a method reference: we give the bot a way to
     * call our deliverFromBot method when it has an answer.
     */
    private final AiBot aiBot = new AiBot(AiSettings.load(), this::deliverFromBot);
    /** Kept so stop() (called from another thread) can close it; volatile so that thread sees it. */
    private volatile ServerSocket serverSocket;

    public ChatServer(int port) {
        this.port = port;
    }

    /** Accepts clients until {@link #stop()} is called. Blocking: runs on the calling thread. */
    public void start() {
        // "try-with-resources": the ServerSocket is closed automatically when the block ends.
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            log("Server listening on port " + port);
            log(aiBot.isConfigured()
                    ? "AI Assistant enabled (model " + aiBot.model() + ", web search when the API plan allows it)"
                    : "AI Assistant has no API key: copy ai.properties.example to ai.properties and add your key");
            // The accept loop: one turn per new client.
            while (!socket.isClosed()) {
                Socket client = socket.accept(); // BLOCKS until someone connects
                log("New connection from " + client.getRemoteSocketAddress());
                // Hand the client to a pool thread, so this loop can immediately accept the next one.
                pool.execute(new ClientHandler(client, this));
            }
        } catch (java.net.BindException e) {
            log("Port " + port + " is already in use. Another server is probably still running: stop it first.");
        } catch (IOException e) {
            // stop() closed the socket (normal), or a real network error happened.
            log("Server stopped" + (serverSocket != null && serverSocket.isClosed() ? "." : ": " + e.getMessage()));
        } finally {
            // Always clean up, whatever happened: disconnect everyone and stop the threads.
            clients.values().forEach(ClientHandler::close);
            pool.shutdownNow();
            aiBot.shutdown();
        }
    }

    /** Closes the listening socket and every client connection (start() then returns). */
    public void stop() {
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close(); // unblocks accept() in start()
            } catch (IOException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- registry
    // These methods are called by the ClientHandler threads (package-private: no "public").

    /** Atomically reserves a username. Returns false if the name is already taken. */
    boolean register(String username, ClientHandler handler) {
        // putIfAbsent checks AND adds in one indivisible step: if two people try the same name
        // at the same moment on two threads, only one of them can win.
        return clients.putIfAbsent(username, handler) == null;
    }

    /** A client finished joining: tell everyone. */
    void onJoined(ClientHandler handler) {
        String name = handler.getUsername();
        log(name + " joined (" + clients.size() + " online)");
        broadcastUserList();                                         // new list for everyone
        broadcast(Message.system(name + " joined the chat"), name);  // notice for everyone except them
    }

    /** A client left or its connection broke: remove it and tell the others. */
    void unregister(ClientHandler handler) {
        String name = handler.getUsername();
        // remove(name, handler) only removes it if it is still THIS handler (safety against races).
        if (name != null && clients.remove(name, handler)) {
            aiBot.forget(name); // drop the AI conversation memory for this user
            log(name + " left (" + clients.size() + " online)");
            broadcastUserList();
            broadcast(Message.system(name + " left the chat"), null);
        }
    }

    // ---------------------------------------------------------------- routing

    /** Delivers a chat message either to everyone (General) or to one user (private). */
    void route(Message message) {
        // 1. No recipient: General group -> everyone except the sender (they already show it).
        if (!message.isPrivate()) {
            broadcast(message, message.getSender());
            log("[General] " + message.getSender() + ": " + describe(message));
            return;
        }
        // 2. Addressed to the AI assistant -> the bot answers later, on its own threads.
        if (Config.AI_BOT_NAME.equals(message.getRecipient())) {
            log("[" + message.getSender() + " -> AI] " + describe(message));
            aiBot.ask(message);
            return;
        }
        // 3. Private message -> only the recipient.
        ClientHandler target = clients.get(message.getRecipient());
        if (target != null) {
            target.send(message); // just puts it in the recipient's queue: never blocks
            log("[" + message.getSender() + " -> " + message.getRecipient() + "] " + describe(message));
        } else {
            // The recipient left meanwhile: tell the sender.
            ClientHandler sender = clients.get(message.getSender());
            if (sender != null) {
                sender.send(Message.error(message.getRecipient() + " is offline, the message was not delivered."));
            }
        }
    }

    /** Called from the AI worker threads: hands the bot's message to the user's writer queue. */
    private void deliverFromBot(Message message) {
        ClientHandler target = clients.get(message.getRecipient());
        if (target != null) {
            target.send(message);
            if (message.getType() == MessageType.TEXT) {
                log("[AI -> " + message.getRecipient() + "] answered (" + message.getText().length() + " chars)");
            }
        }
    }

    /** Sends a message to every online client, except {@code excludedUser} (null = nobody excluded). */
    private void broadcast(Message message, String excludedUser) {
        clients.forEach((name, handler) -> {
            if (!name.equals(excludedUser)) {
                handler.send(message);
            }
        });
    }

    /** Sends everyone the sorted list of online users, plus the always-online assistant. */
    private void broadcastUserList() {
        List<String> users = new ArrayList<>(clients.keySet());
        users.sort(String.CASE_INSENSITIVE_ORDER);
        users.add(Config.AI_BOT_NAME); // the assistant is always online
        broadcast(Message.userList(users), null);
    }

    /** Short text for the log: the text itself, or "<IMAGE photo.jpg, 52000 bytes>". */
    private static String describe(Message m) {
        return switch (m.getType()) {
            case TEXT -> m.getText();
            default -> "<" + m.getType() + " " + m.getFileName() + ", " + m.getSize() + " bytes>";
        };
    }

    /** Prints "[time] [thread name] text": the thread name shows which thread did what. */
    static void log(String text) {
        System.out.println("[" + LocalTime.now().format(LOG_TIME) + "] [" + Thread.currentThread().getName() + "] " + text);
    }
}
