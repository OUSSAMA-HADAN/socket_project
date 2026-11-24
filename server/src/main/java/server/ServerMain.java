package server;

import common.Config;

/**
 * Starts the chat server. Optional argument: the port (default {@link Config#DEFAULT_PORT}).
 */
public class ServerMain {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Config.DEFAULT_PORT;
        ChatServer server = new ChatServer(port);
        // Ctrl+C / IntelliJ's stop button: close the port and the client connections cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "shutdown"));
        server.start();
    }
}
