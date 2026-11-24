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
 */
public class ChatServer {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final AiBot aiBot = new AiBot(AiSettings.load(), this::deliverFromBot);
    private volatile ServerSocket serverSocket;

    public ChatServer(int port) {
        this.port = port;
    }

    /** Accepts clients until {@link #stop()} is called. Blocking: runs on the calling thread. */
    public void start() {
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            log("Server listening on port " + port);
            log(aiBot.isConfigured()
                    ? "AI Assistant enabled (model " + aiBot.model() + ", web search when the API plan allows it)"
                    : "AI Assistant has no API key: copy ai.properties.example to ai.properties and add your key");
            while (!socket.isClosed()) {
                Socket client = socket.accept();
                log("New connection from " + client.getRemoteSocketAddress());
                pool.execute(new ClientHandler(client, this));
            }
        } catch (java.net.BindException e) {
            log("Port " + port + " is already in use. Another server is probably still running: stop it first.");
        } catch (IOException e) {
            log("Server stopped" + (serverSocket != null && serverSocket.isClosed() ? "." : ": " + e.getMessage()));
        } finally {
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

    /** Atomically reserves a username. Returns false if the name is already taken. */
    boolean register(String username, ClientHandler handler) {
        return clients.putIfAbsent(username, handler) == null;
    }

    void onJoined(ClientHandler handler) {
        String name = handler.getUsername();
        log(name + " joined (" + clients.size() + " online)");
        broadcastUserList();
        broadcast(Message.system(name + " joined the chat"), name);
    }

    void unregister(ClientHandler handler) {
        String name = handler.getUsername();
        if (name != null && clients.remove(name, handler)) {
            aiBot.forget(name);
            log(name + " left (" + clients.size() + " online)");
            broadcastUserList();
            broadcast(Message.system(name + " left the chat"), null);
        }
    }

    // ---------------------------------------------------------------- routing

    /** Delivers a chat message either to everyone (General) or to one user (private). */
    void route(Message message) {
        if (!message.isPrivate()) {
            broadcast(message, message.getSender());
            log("[General] " + message.getSender() + ": " + describe(message));
            return;
        }
        if (Config.AI_BOT_NAME.equals(message.getRecipient())) {
            log("[" + message.getSender() + " -> AI] " + describe(message));
            aiBot.ask(message);
            return;
        }
        ClientHandler target = clients.get(message.getRecipient());
        if (target != null) {
            target.send(message);
            log("[" + message.getSender() + " -> " + message.getRecipient() + "] " + describe(message));
        } else {
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

    private void broadcast(Message message, String excludedUser) {
        clients.forEach((name, handler) -> {
            if (!name.equals(excludedUser)) {
                handler.send(message);
            }
        });
    }

    private void broadcastUserList() {
        List<String> users = new ArrayList<>(clients.keySet());
        users.sort(String.CASE_INSENSITIVE_ORDER);
        users.add(Config.AI_BOT_NAME); // the assistant is always online
        broadcast(Message.userList(users), null);
    }

    private static String describe(Message m) {
        return switch (m.getType()) {
            case TEXT -> m.getText();
            default -> "<" + m.getType() + " " + m.getFileName() + ", " + m.getSize() + " bytes>";
        };
    }

    static void log(String text) {
        System.out.println("[" + LocalTime.now().format(LOG_TIME) + "] [" + Thread.currentThread().getName() + "] " + text);
    }
}
