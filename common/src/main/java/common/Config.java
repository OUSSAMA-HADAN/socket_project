package common;

/**
 * Settings shared by the server and the clients.
 * <p>
 * {@code static final} fields are constants: one value for the whole program, which never changes.
 * Because this class is in the common module, the server, the desktop app and the Android app
 * all read the same values, e.g. the same port number.
 */
public final class Config {

    /** Server address used by the desktop login screen by default ("this computer"). */
    public static final String DEFAULT_HOST = "localhost";
    /** Not 5555: the Android emulator uses host ports 5554/5555 for itself (console and adb). */
    public static final int DEFAULT_PORT = 5050;
    /** How the Android emulator reaches the PC it runs on ("localhost" there is the phone itself). */
    public static final String EMULATOR_HOST = "10.0.2.2";

    /** Largest attachment (image, audio, file) a client may send. */
    public static final long MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024; // 25 MB ("L" = long number)

    /** Longest allowed username, checked by the server. */
    public static final int MAX_USERNAME_LENGTH = 24;

    /** Name of the built-in AI chatbot contact (reserved, no user can take it). */
    public static final String AI_BOT_NAME = "AI Assistant";

    /** Private constructor: this class only holds constants, nobody should create an instance. */
    private Config() {
    }
}
