package client.util;

import common.Message;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Text formatting helpers for the user interface (desktop and Android).
 * No UI toolkit types here, so both clients can use it.
 */
public final class Formats {

    /**
     * The app's texts are English, so numbers and month names are too, whatever the PC or
     * phone language: "1.5 MB" and "November 21" everywhere (not "1,5 MB" / "novembre 21").
     */
    private static final Locale LOCALE = Locale.ENGLISH;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d", LOCALE);
    private static final DateTimeFormatter DAY_WITH_YEAR = DateTimeFormatter.ofPattern("MMMM d, yyyy", LOCALE);

    private Formats() {
    }

    public static String time(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    public static String day(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return day.format(day.getYear() == today.getYear() ? DAY : DAY_WITH_YEAR);
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(LOCALE, "%.1f KB", bytes / 1024.0);
        }
        return String.format(LOCALE, "%.1f MB", bytes / (1024.0 * 1024));
    }

    public static String duration(long totalSeconds) {
        return String.format(LOCALE, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** One-line summary of a message for the sidebar. */
    public static String preview(Message message, String me, boolean group) {
        String body = switch (message.getType()) {
            case TEXT -> message.getText().replace('\n', ' ');
            case IMAGE -> "🖼 Photo";
            case VOICE -> "🎤 Voice message";
            case AUDIO -> "🎵 " + message.getFileName();
            case FILE -> "📄 " + message.getFileName();
            default -> message.getText() == null ? "" : message.getText();
        };
        if (!message.getType().isChat()) {
            return body;
        }
        if (me.equals(message.getSender())) {
            return "You: " + body;
        }
        return group ? message.getSender() + ": " + body : body;
    }
}
