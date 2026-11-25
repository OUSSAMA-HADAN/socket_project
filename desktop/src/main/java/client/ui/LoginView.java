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
 * <p>
 * The whole screen is built in Java code (no FXML): we create the controls, put them in boxes
 * (VBox = vertical, HBox = horizontal, StackPane = on top of each other) and give them CSS
 * classes; their look comes from theme.css.
 */
public class LoginView extends StackPane {

    /** What to do once connected: ChatApp passes its showChat method. */
    private final Consumer<ChatClient> onConnected;
    private final TextField username = field("Your name");
    private final TextField host = field("Server host");
    private final TextField port = field("Port");
    private final Button connectButton = new Button("START MESSAGING");
    private final ProgressIndicator spinner = new ProgressIndicator(); // the turning wheel
    private final Label error = new Label();

    public LoginView(Consumer<ChatClient> onConnected) {
        this.onConnected = onConnected;
        getStyleClass().add("login-root"); // the CSS class that styles this screen

        // Logo: a blue circle with a 💬 emoji on top of it.
        Circle logoCircle = new Circle(48);
        logoCircle.getStyleClass().add("login-logo");
        StackPane logo = new StackPane(logoCircle, EmojiText.icon("💬", 54));

        Label title = new Label(Theme.APP_NAME);
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Pick a display name and the server address\nto start messaging.");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setTextAlignment(TextAlignment.CENTER);

        // Host and port side by side; the host field takes the extra width.
        host.setText(Config.DEFAULT_HOST);
        port.setText(String.valueOf(Config.DEFAULT_PORT));
        port.setPrefWidth(110);
        HBox serverRow = new HBox(10, host, port);
        HBox.setHgrow(host, Priority.ALWAYS);

        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setDefaultButton(true); // the Enter key presses it
        connectButton.setOnAction(e -> connect());

        spinner.setMaxSize(28, 28);
        spinner.setVisible(false);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setTextAlignment(TextAlignment.CENTER);

        // The white card in the middle, holding everything from top to bottom.
        VBox card = new VBox(14, logo, title, subtitle, username, serverRow, connectButton, spinner, error);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        getChildren().add(card);
        Platform.runLater(username::requestFocus); // put the cursor in the name field
    }

    /** START MESSAGING: check the fields, then connect in the background. */
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
        // Connecting can take seconds: a JavaFX Task runs call() on a background thread,
        // then runs setOnSucceeded / setOnFailed back on the JavaFX thread.
        Task<ChatClient> task = new Task<>() {
            @Override
            protected ChatClient call() throws Exception {
                return ChatClient.connect(hostName, portNumber, name); // background thread
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

    /** While connecting: show the spinner and lock the form. */
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

    /** Turns a technical exception into a sentence the user understands. */
    private static String describe(Throwable e, String host, int port) {
        if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return "Cannot reach the server at " + host + ":" + port + ". Is it running?";
        }
        if (e instanceof UnknownHostException) {
            return "Unknown host: " + host;
        }
        return e.getMessage() == null ? "Connection failed." : e.getMessage(); // e.g. "name already taken"
    }

    /** A text field with a grey hint ("prompt text") and the app's field style. */
    private static TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("tg-field");
        return field;
    }
}
