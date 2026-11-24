package mobile.ui;

import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.icu.text.BreakIterator;
import android.os.Build;

/**
 * Emoji helpers. Android draws color emoji by itself; we only need to know when a message is
 * made of nothing but 1-3 emoji, to show them big without a bubble (like the desktop and Telegram).
 */
final class EmojiUtil {

    private EmojiUtil() {
    }

    /** Number of emoji if the text is only emoji (and spaces), otherwise 0. */
    static int emojiOnlyCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        // A "character" as the user sees it: 👨‍👩‍👧 or 🇫🇷 are several code points but one emoji.
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int count = 0;
        int start = graphemes.first();
        for (int end = graphemes.next(); end != BreakIterator.DONE; start = end, end = graphemes.next()) {
            String cluster = text.substring(start, end);
            if (cluster.trim().isEmpty()) {
                continue;
            }
            if (!isEmoji(cluster)) {
                return 0;
            }
            count++;
        }
        return count;
    }

    private static boolean isEmoji(String cluster) {
        int first = cluster.codePointAt(0);
        boolean emojiPresentation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? UCharacter.hasBinaryProperty(first, UProperty.EMOJI_PRESENTATION)
                : first >= 0x1F000 && Character.getType(first) == Character.OTHER_SYMBOL; // Android 8
        return emojiPresentation
                || (first >= 0x1F1E6 && first <= 0x1F1FF)   // flags
                || cluster.indexOf('️') >= 0           // ❤️ ☺️ (text symbol + "emoji style")
                || cluster.indexOf('⃣') >= 0;          // keycaps 1️⃣
    }
}
