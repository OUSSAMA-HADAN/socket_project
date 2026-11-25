package client.util;

import common.MessageType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Decides how a file should be sent based on its extension.
 * Shared with the Android app, so it only uses APIs available on Android 8.0+.
 * <p>
 * Example: "holiday.JPG" -> extension "jpg" -> sent as an IMAGE;
 * "song.mp3" -> AUDIO; "report.pdf" -> FILE.
 */
public final class FileKinds {

    /** Picture formats every client can display (the desktop's JavaFX can't show HEIC or WebP). */
    public static final List<String> IMAGE_EXTENSIONS =
            Collections.unmodifiableList(Arrays.asList("png", "jpg", "jpeg", "gif", "bmp"));
    /** Audio formats the clients can play. */
    public static final List<String> AUDIO_EXTENSIONS =
            Collections.unmodifiableList(Arrays.asList("mp3", "wav", "m4a", "aac", "aif", "aiff"));

    /** Only static helpers here: no instances. */
    private FileKinds() {
    }

    /** IMAGE, AUDIO or FILE, depending on the file name's extension. */
    public static MessageType detect(String fileName) {
        String ext = extension(fileName);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return MessageType.IMAGE;
        }
        if (AUDIO_EXTENSIONS.contains(ext)) {
            return MessageType.AUDIO;
        }
        return MessageType.FILE;
    }

    /** "Photo.JPG" -> "jpg" (lower case, without the dot); "" if there is no extension. */
    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.'); // position of the last ".", or -1 if none
        // Locale.ROOT: lower-case the same way on every computer (Turkish "I" is a classic trap).
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** "*.png", "*.jpg", ... for a file chooser filter. */
    public static List<String> patterns(List<String> extensions) {
        List<String> patterns = new ArrayList<>();
        for (String e : extensions) {
            patterns.add("*." + e);
        }
        return patterns;
    }
}
