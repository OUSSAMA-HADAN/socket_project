package common;

/**
 * Settings shared by the server and the clients.
 */
public final class Config {

    public static final String DEFAULT_HOST = "localhost";
    /** Not 5555: the Android emulator uses host ports 5554/5555 for itself (console and adb). */
    public static final int DEFAULT_PORT = 5050;
    /** How the Android emulator reaches the PC it runs on ("localhost" there is the phone itself). */
    public static final String EMULATOR_HOST = "10.0.2.2";

    /** Largest attachment (image, audio, file) a client may send. */
    public static final long MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024;

    public static final int MAX_USERNAME_LENGTH = 24;

    /** Name of the built-in AI chatbot contact (reserved, no user can take it). */
    public static final String AI_BOT_NAME = "AI Assistant";

    private Config() {
    }
}
