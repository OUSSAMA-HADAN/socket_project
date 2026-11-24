package client;

import client.network.ChatClient;
import client.ui.AudioMessageView;
import client.ui.ChatView;
import client.ui.LoginView;
import client.ui.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * JavaFX entry point of the client: shows the login screen, then the chat screen.
 * Run several instances to simulate several users.
 */
public class ChatApp extends Application {

    private Stage stage;
    private Scene scene;
    private ChatClient client;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        scene = new Scene(new Region(), 1100, 720);
        scene.getStylesheets().add(Theme.STYLESHEET);
        stage.setScene(scene);
        stage.setMinWidth(780);
        stage.setMinHeight(520);
        showLogin();
        stage.show();
    }

    private void showLogin() {
        stage.setTitle(Theme.APP_NAME);
        scene.setRoot(new LoginView(this::showChat));
    }

    private void showChat(ChatClient connected) {
        client = connected;
        ChatView view = new ChatView(connected, this::logout);
        scene.setRoot(view);
        stage.setTitle(Theme.APP_NAME + " — " + connected.getUsername());
        connected.startListening(view);
    }

    private void logout() {
        closeConnection();
        showLogin();
    }

    @Override
    public void stop() {
        closeConnection();
    }

    private void closeConnection() {
        AudioMessageView.stopActive();
        if (client != null) {
            client.disconnect();
            client = null;
        }
    }
}
