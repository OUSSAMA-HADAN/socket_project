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
 * <p>
 * <b>JavaFX vocabulary</b>: the {@code Stage} is the window, the {@code Scene} is its content
 * area, and the scene's "root" is the screen currently shown. We switch screens by changing
 * the root: LoginView first, then ChatView after a successful login.
 */
public class ChatApp extends Application {

    private Stage stage;
    private Scene scene;
    private ChatClient client; // the current connection (null while on the login screen)

    /** Called by JavaFX on the "JavaFX Application Thread" when the app starts. */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        scene = new Scene(new Region(), 1100, 720);   // window size: 1100 x 720 pixels
        scene.getStylesheets().add(Theme.STYLESHEET); // the Telegram-style CSS
        stage.setScene(scene);
        stage.setMinWidth(780);
        stage.setMinHeight(520);
        showLogin();
        stage.show();
    }

    /** Shows the login screen. "this::showChat" = what LoginView calls once connected. */
    private void showLogin() {
        stage.setTitle(Theme.APP_NAME);
        scene.setRoot(new LoginView(this::showChat));
    }

    /** Switches to the chat screen and starts receiving messages. */
    private void showChat(ChatClient connected) {
        client = connected;
        ChatView view = new ChatView(connected, this::logout);
        scene.setRoot(view);
        stage.setTitle(Theme.APP_NAME + " — " + connected.getUsername());
        // ChatView implements MessageListener: from now on, every incoming message goes to it.
        connected.startListening(view);
    }

    /** Log out button: close the connection and go back to the login screen. */
    private void logout() {
        closeConnection();
        showLogin();
    }

    /** Called by JavaFX when the window is closed. */
    @Override
    public void stop() {
        closeConnection();
    }

    private void closeConnection() {
        AudioMessageView.stopActive(); // stop any voice message that is playing
        if (client != null) {
            client.disconnect();
            client = null;
        }
    }
}
