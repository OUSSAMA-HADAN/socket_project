package client;

import javafx.application.Application;

/**
 * Plain main class so the client can be started directly from the IDE
 * (a main class that extends Application fails on the classpath).
 */
public class ClientLauncher {

    public static void main(String[] args) {
        Application.launch(ChatApp.class, args);
    }
}
