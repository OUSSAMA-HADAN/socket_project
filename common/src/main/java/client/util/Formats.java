package client.util;

import common.Message;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Text formatting helpers for the user interface (desktop and Android).
 * No UI toolkit types here, so both clients can use it.
 * <p>
 * All methods are {@code static}: you call them on the class, e.g. {@code Formats.size(2048)}
 * returns "2.0 KB". There is no object to create.
 */
public final class Formats {

    /**
     * The app's texts are English, so numbers and month names are too, whatever the PC or
     * phone language: "1.5 MB" and "November 21" everywhere (not "1,5 MB" / "novembre 21").
     */
    private static final Locale LOCALE = Locale.ENGLISH;
    // Date patterns: HH = hours 00-23, mm = minutes, MMMM = full month name, d = day, yyyy = year.
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d", LOCALE);
    private static final DateTimeFormatter DAY_WITH_YEAR = DateTimeFormatter.ofPattern("MMMM d, yyyy", LOCALE);

    /** Only static helpers here: no instances. */
    private Formats() {
    }

    /** "14:05" — the time shown under each bubble. */
    public static String time(LocalDateTime time) {
        return time == null ? "" : time.format(TIME);
    }

    /** "Today", "Yesterday", "November 21" or "November 21, 2025" — the date separators in a chat. */
    public static String day(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        // Only show the year when it isn't the current year.
        return day.format(day.getYear() == today.getYear() ? DAY : DAY_WITH_YEAR);
    }

    /** A file size people can read: 512 -> "512 B", 2048 -> "2.0 KB", 3145728 -> "3.0 MB". */
    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(LOCALE, "%.1f KB", bytes / 1024.0); // %.1f = 1 digit after the dot
        }
        return String.format(LOCALE, "%.1f MB", bytes / (1024.0 * 1024));
    }

    /** Seconds as minutes:seconds, e.g. 75 -> "1:15" (%02d pads with a zero: 5 -> "05"). */
    public static String duration(long totalSeconds) {
        return String.format(LOCALE, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /**
     * One-line summary of a message for the sidebar.
     *
     * @param me    the current user's name ("You: ..." for my own messages)
     * @param group true in the General group, where the sender's name is shown ("bob: hi")
     */
    public static String preview(Message message, String me, boolean group) {
        // A "switch expression" (Java 14+): each case gives the value for one message type.
        String body = switch (message.getType()) {
            case TEXT -> message.getText().replace('\n', ' '); // keep it on one line
            case IMAGE -> "🖼 Photo";
            case VOICE -> "🎤 Voice message";
            case AUDIO -> "🎵 " + message.getFileName();
            case FILE -> "📄 " + message.getFileName();
            default -> message.getText() == null ? "" : message.getText(); // SYSTEM etc.
        };
        if (!message.getType().isChat()) {
            return body; // e.g. "alice joined the chat": no "You:" or name in front
        }
        if (me.equals(message.getSender())) {
            return "You: " + body;
        }
        return group ? message.getSender() + ": " + body : body;
    }
}
