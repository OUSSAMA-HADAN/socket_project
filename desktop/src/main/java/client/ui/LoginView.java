package client.ui;

import client.network.ChatClient;
import common.Config;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.function.Consumer;

/**
 * First screen: choose a display name and the server to connect to.
 */
public class LoginView extends StackPane {

    private final Consumer<ChatClient> onConnected;
    private final TextField username = field("Your name");
    private final TextField host = field("Server host");
    private final TextField port = field("Port");
    private final Button connectButton = new Button("START MESSAGING");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label error = new Label();

    public LoginView(Consumer<ChatClient> onConnected) {
        this.onConnected = onConnected;
        getStyleClass().add("login-root");

        Circle logoCircle = new Circle(48);
        logoCircle.getStyleClass().add("login-logo");
        StackPane logo = new StackPane(logoCircle, EmojiText.icon("💬", 54));

        Label title = new Label(Theme.APP_NAME);
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Pick a display name and the server address\nto start messaging.");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setTextAlignment(TextAlignment.CENTER);

        host.setText(Config.DEFAULT_HOST);
        port.setText(String.valueOf(Config.DEFAULT_PORT));
        port.setPrefWidth(110);
        HBox serverRow = new HBox(10, host, port);
        HBox.setHgrow(host, Priority.ALWAYS);

        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setDefaultButton(true);
        connectButton.setOnAction(e -> connect());

        spinner.setMaxSize(28, 28);
        spinner.setVisible(false);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setTextAlignment(TextAlignment.CENTER);

        VBox card = new VBox(14, logo, title, subtitle, username, serverRow, connectButton, spinner, error);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        getChildren().add(card);
        Platform.runLater(username::requestFocus);
    }

    private void connect() {
        String name = username.getText().trim();
        String hostName = host.getText().trim();
        int portNumber;
        if (name.isEmpty()) {
            showError("Please enter your name.");
            return;
        }
        try {
            portNumber = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException e) {
            showError("The port must be a number.");
            return;
        }

        setBusy(true);
        Task<ChatClient> task = new Task<>() {
            @Override
            protected ChatClient call() throws Exception {
                return ChatClient.connect(hostName, portNumber, name);
            }
        };
        task.setOnSucceeded(e -> onConnected.accept(task.getValue()));
        task.setOnFailed(e -> {
            setBusy(false);
            showError(describe(task.getException(), hostName, portNumber));
        });
        Thread thread = new Thread(task, "connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        spinner.setVisible(busy);
        connectButton.setDisable(busy);
        username.setDisable(busy);
        host.setDisable(busy);
        port.setDisable(busy);
        if (busy) {
            error.setText("");
        }
    }

    private void showError(String text) {
        error.setText(text);
    }

    private static String describe(Throwable e, String host, int port) {
        if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return "Cannot reach the server at " + host + ":" + port + ". Is it running?";
        }
        if (e instanceof UnknownHostException) {
            return "Unknown host: " + host;
        }
        return e.getMessage() == null ? "Connection failed." : e.getMessage();
    }

    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("tg-field");
        return field;
    }
}
