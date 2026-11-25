package client;

import javafx.application.Application;

/**
 * Plain main class so the client can be started directly from the IDE
 * (a main class that extends Application fails on the classpath).
 * <p>
 * This is the class the IntelliJ "Client" configuration runs. It only asks JavaFX to start
 * {@link ChatApp}; JavaFX then creates the window and calls ChatApp.start().
 */
public class ClientLauncher {

    public static void main(String[] args) {
        Application.launch(ChatApp.class, args);
    }
}
