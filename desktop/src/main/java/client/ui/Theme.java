package client.ui;

import java.util.Objects;

/**
 * App-wide look settings: the app's name and the CSS file that gives it the Telegram look.
 * To change colors, edit desktop/src/main/resources/styles/theme.css.
 */
public final class Theme {

    /** Shown in the window title and on the login screen. */
    public static final String APP_NAME = "ChatFX";

    /**
     * The stylesheet's address inside the app (it lives in src/main/resources/styles/).
     * requireNonNull stops the app with a clear message if the file is missing.
     */
    public static final String STYLESHEET = Objects.requireNonNull(
            Theme.class.getResource("/styles/theme.css"), "theme.css not found").toExternalForm();

    /** Only constants here: no instances. */
    private Theme() {
    }
}
