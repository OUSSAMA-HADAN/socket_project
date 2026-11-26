package mobile.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import chatfx.mobile.R;
import client.network.ChatClient;
import common.Config;
import mobile.ChatService;
import mobile.ChatSession;
import mobile.Notifications;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * First screen: choose a display name and the server to connect to (same as the desktop LoginView).
 * <p>
 * <b>Android basics</b>
 * <ul>
 *   <li>An <b>Activity</b> is one screen. Android creates it and calls its lifecycle methods:
 *       onCreate (build the screen) → onStart (visible) → onStop (hidden) → onDestroy (gone).</li>
 *   <li>The layout is written in XML (res/layout/activity_login.xml). {@code setContentView} shows it,
 *       and {@code findViewById(R.id.username)} gets the view whose XML has {@code android:id="@+id/username"}.
 *       {@code R} is a class Android generates with a number for every resource.</li>
 *   <li>The <b>main thread</b> (UI thread) draws the screen. Network calls are forbidden on it,
 *       so connecting runs on a background thread, and the result comes back with runOnUiThread.</li>
 * </ul>
 */
public class LoginActivity extends AppCompatActivity {

    private static final String PREFS = "login";
    private static final String TAG = "ChatFX";

    /** Android 17 (API 37) is the first version that blocks local network access by default. */
    private static final int LOCAL_NETWORK_PERMISSION_SDK = 37;

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    /** Shows the system "Allow access to devices on your local network?" dialog. */
    private final ActivityResultLauncher<String> localNetworkPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    connect();
                } else {
                    showError("ChatFX needs the \"local network\" permission to reach the server on your PC. "
                            + "Allow it in Settings → Apps → ChatFX → Permissions.");
                }
            });
    private EditText username;
    private EditText host;
    private EditText port;
    private MaterialButton connectButton;
    private ProgressBar spinner;
    private TextView error;

    /** Builds the screen: show the XML layout, find the views, fill in the last values used. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ChatSession.current() != null) { // already logged in (app reopened)
            openChats();
            return;
        }
        setContentView(R.layout.activity_login);

        username = findViewById(R.id.username);
        host = findViewById(R.id.host);
        port = findViewById(R.id.port);
        connectButton = findViewById(R.id.connect);
        spinner = findViewById(R.id.spinner);
        error = findViewById(R.id.error);

        // SharedPreferences: a small key-value store saved on the phone (here: the last login used).
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        username.setText(prefs.getString("username", ""));
        host.setText(prefs.getString("host", defaultHost()));
        port.setText(String.valueOf(prefs.getInt("port", Config.DEFAULT_PORT)));
        if (!isEmulator()) {
            ((TextInputLayout) findViewById(R.id.hostLayout)).setHelperText(getString(R.string.host_help_device));
        }

        connectButton.setOnClickListener(v -> connect());
        port.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connect();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        background.shutdown();
    }

    /** START MESSAGING: check the fields, ask for the local-network permission if needed, then connect. */
    private void connect() {
        String name = username.getText().toString().trim();
        String hostName = host.getText().toString().trim();
        int portNumber;
        if (name.isEmpty()) {
            showError("Please enter your name.");
            return;
        }
        if (hostName.isEmpty()) {
            showError("Please enter the server address.");
            return;
        }
        try {
            portNumber = Integer.parseInt(port.getText().toString().trim());
        } catch (NumberFormatException e) {
            showError("The port must be a number.");
            return;
        }
        if (portNumber < 1 || portNumber > 65535) { // valid TCP ports
            showError("The port must be between 1 and 65535.");
            return;
        }
        if (needsLocalNetworkPermission()) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK); // calls connect() again
            return;
        }

        setBusy(true);
        // Networking is forbidden on Android's main thread: connect in the background.
        background.execute(() -> {
            try {
                ChatClient client = ChatClient.connect(hostName, portNumber, name);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        new Thread(client::disconnect).start();
                        return;
                    }
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString("username", name)
                            .putString("host", hostName)
                            .putInt("port", portNumber)
                            .apply();
                    ChatSession.start(this, client);
                    ChatService.start(this); // keeps the connection alive in the background
                    Notifications.cancelDisconnected(this);
                    openChats();
                });
            } catch (IOException e) {
                Log.w(TAG, "Connection to " + hostName + ":" + portNumber + " failed", e);
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        setBusy(false);
                        showError(describe(e, hostName, portNumber));
                    }
                });
            }
        });
    }

    private boolean needsLocalNetworkPermission() {
        return Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Opens the chat list. An Intent is a request to Android: "start this screen".
     * finish() closes the login screen, so the Back button doesn't return to it.
     */
    private void openChats() {
        startActivity(new Intent(this, ChatsActivity.class));
        finish();
    }

    private void setBusy(boolean busy) {
        spinner.setVisibility(busy ? View.VISIBLE : View.GONE);
        connectButton.setEnabled(!busy);
        username.setEnabled(!busy);
        host.setEnabled(!busy);
        port.setEnabled(!busy);
        if (busy) {
            error.setText("");
        }
    }

    private void showError(String text) {
        error.setText(text);
    }

    private static String describe(IOException e, String host, int port) {
        if (e instanceof ConnectException) {
            return "No server is answering at " + host + ":" + port
                    + ". Start the server on the PC (and allow the port in its firewall), then try again.";
        }
        if (e instanceof SocketTimeoutException) {
            return "The server at " + host + ":" + port + " didn't answer in time. Check the address "
                    + "and that the phone and the PC are on the same network.";
        }
        if (e instanceof UnknownHostException) {
            return "Unknown host: " + host;
        }
        return e.getMessage() == null ? "Connection failed (" + e.getClass().getSimpleName() + ")." : e.getMessage();
    }

    /** The emulator reaches the PC at 10.0.2.2; a real phone needs the PC's Wi-Fi address. */
    private static String defaultHost() {
        return isEmulator() ? Config.EMULATOR_HOST : "";
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk");
    }
}
