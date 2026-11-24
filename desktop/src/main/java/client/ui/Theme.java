package client.ui;

import java.util.Objects;

public final class Theme {

    public static final String APP_NAME = "ChatFX";

    public static final String STYLESHEET = Objects.requireNonNull(
            Theme.class.getResource("/styles/theme.css"), "theme.css not found").toExternalForm();

    private Theme() {
    }
}
