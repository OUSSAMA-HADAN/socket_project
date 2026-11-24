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
 */
public final class FileKinds {

    public static final List<String> IMAGE_EXTENSIONS =
            Collections.unmodifiableList(Arrays.asList("png", "jpg", "jpeg", "gif", "bmp"));
    public static final List<String> AUDIO_EXTENSIONS =
            Collections.unmodifiableList(Arrays.asList("mp3", "wav", "m4a", "aac", "aif", "aiff"));

    private FileKinds() {
    }

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

    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
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
