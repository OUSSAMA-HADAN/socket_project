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
        int port = Config.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("\"" + args[0] + "\" is not a port number. Usage: ServerMain [port]");
                return;
            }
        }
        if (port < 1 || port > 65535) {
            System.err.println("The port must be between 1 and 65535 (got " + port + ").");
            return;
        }
        ChatServer server = new ChatServer(port);
        // Ctrl+C / IntelliJ's stop button: close the port and the client connections cleanly.
        // A "shutdown hook" is a thread the JVM runs just before the program exits.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        server.start(); // blocks here, accepting clients, until the server is stopped
    }
}
