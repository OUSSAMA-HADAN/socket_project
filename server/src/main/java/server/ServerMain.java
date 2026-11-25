package server;

import common.Config;

/**
 * Starts the chat server. Optional argument: the port (default {@link Config#DEFAULT_PORT}).
 * <p>
 * This is the class you run in IntelliJ ("Server" configuration). Its only job is to read
 * the port, create the {@link ChatServer} and start it.
 */
public class ServerMain {

    public static void main(String[] args) {
        // args holds the command-line arguments: "java server.ServerMain 6000" -> args[0] = "6000".
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Config.DEFAULT_PORT;
        ChatServer server = new ChatServer(port);
        // Ctrl+C / IntelliJ's stop button: close the port and the client connections cleanly.
        // A "shutdown hook" is a thread the JVM runs just before the program exits.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        server.start(); // blocks here, accepting clients, until the server is stopped
    }
}
