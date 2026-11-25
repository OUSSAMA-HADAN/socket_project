package client.emoji;

import javafx.scene.image.Image;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Color emoji support. JavaFX cannot draw color emoji fonts on Windows, so emoji are
 * shown as small images (Twemoji) stored in {@code resources/emoji/png}.
 * <p>
 * This class knows which emoji have an image, loads/caches the images, reads the
 * picker categories and splits a text into plain-text and emoji segments.
 */
public final class EmojiRepository {

    private static final String PNG_DIR = "/emoji/png/";
    private static final String CATEGORIES_FILE = "/emoji/categories.txt";

    // Caches, so each PNG is looked up and loaded only once (ConcurrentHashMap: safe from any thread).
    private static final Map<String, URL> RESOURCES = new ConcurrentHashMap<>(); // file name -> where the PNG is
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>(); // file names we have no PNG for
    private static final Map<String, Image> IMAGES = new ConcurrentHashMap<>();    // file name -> loaded image
    private static List<Category> categories; // loaded on first use

    /** One tab of the picker, e.g. ("Smileys & People", "😀", [😀, 😃, ...]). */
    public record Category(String name, String icon, List<String> emojis) {
    }

    /** A piece of text: either plain text (emoji = false) or exactly one emoji (emoji = true). */
    public record Segment(String text, boolean emoji) {
    }

    private EmojiRepository() {
    }

    // ---------------------------------------------------------------- images

    /** True if we have a picture for this emoji. */
    public static boolean exists(String emoji) {
        return url(emoji) != null;
    }

    /** Cached image for the emoji, or {@code null} if we have no picture for it. */
    public static Image image(String emoji) {
        URL url = url(emoji);
        if (url == null) {
            return null;
        }
        return IMAGES.computeIfAbsent(fileName(emoji), k -> new Image(url.toExternalForm(), true));
    }

    /** Finds the PNG inside the app's resources, remembering hits and misses. */
    private static URL url(String emoji) {
        String key = fileName(emoji);
        if (key.isEmpty() || MISSING.containsKey(key)) {
            return null;
        }
        URL url = RESOURCES.get(key);
        if (url == null) {
            url = EmojiRepository.class.getResource(PNG_DIR + key + ".png");
            if (url == null) {
                MISSING.put(key, true);
                return null;
            }
            RESOURCES.put(key, url);
        }
        return url;
    }

    /**
     * Twemoji file naming: hex code points joined by '-', without FE0F unless it is a ZWJ sequence.
     * Example: 👍 is the code point U+1F44D -> file "1f44d.png"; 🇫🇷 = two code points -> "1f1eb-1f1f7.png".
     * (ZWJ = "zero width joiner", the invisible glue in combined emoji like 👨‍👩‍👧.)
     */
    static String fileName(String emoji) {
        boolean zwj = emoji.indexOf('‍') >= 0;
        return emoji.codePoints()
                .filter(cp -> zwj || cp != 0xFE0F)
                .mapToObj(Integer::toHexString)
                .collect(Collectors.joining("-"));
    }

    // ---------------------------------------------------------------- picker categories

    /** The picker's categories, read once from categories.txt (synchronized: loaded only once even if two threads ask). */
    public static synchronized List<Category> categories() {
        if (categories == null) {
            categories = loadCategories();
        }
        return categories;
    }

    private static List<Category> loadCategories() {
        List<Category> result = new ArrayList<>();
        try (InputStream in = EmojiRepository.class.getResourceAsStream(CATEGORIES_FILE)) {
            if (in == null) {
                return result;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                // Line format: name|icon|emoji emoji emoji ...
                String[] parts = line.split("\\|", 3);
                List<String> emojis = Arrays.stream(parts[2].trim().split("\\s+"))
                        .filter(EmojiRepository::exists)
                        .toList();
                result.add(new Category(parts[0], parts[1], emojis));
            }
        } catch (IOException e) {
            System.err.println("Could not load emoji categories: " + e.getMessage());
        }
        return result;
    }

    // ---------------------------------------------------------------- text parsing

    /** Splits text into plain parts and emoji parts (only emoji we have an image for). */
    public static List<Segment> split(String text) {
        // Example: "Hi 👋 bob" -> ["Hi ", 👋, " bob"]. We walk through the text: at each position,
        // either an emoji starts (add it as its own segment) or we add one character to the plain run.
        List<Segment> segments = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int end = matchEmoji(text, i);
            if (end > i) {
                if (!plain.isEmpty()) {
                    segments.add(new Segment(plain.toString(), false));
                    plain.setLength(0);
                }
                segments.add(new Segment(text.substring(i, end), true));
                i = end;
            } else {
                int cp = text.codePointAt(i);
                plain.appendCodePoint(cp);
                i += Character.charCount(cp);
            }
        }
        if (!plain.isEmpty()) {
            segments.add(new Segment(plain.toString(), false));
        }
        return segments;
    }

    /** Returns the end index of the longest known emoji starting at {@code start}, or -1. */
    private static int matchEmoji(String text, int start) {
        int first = text.codePointAt(start);
        if (first < 0xA9) {
            return -1; // plain ASCII / Latin-1 text
        }
        int index = start + Character.charCount(first);

        // flags are made of two "regional indicator" letters
        if (isRegionalIndicator(first) && index < text.length() && isRegionalIndicator(text.codePointAt(index))) {
            int end = index + Character.charCount(text.codePointAt(index));
            if (exists(text.substring(start, end))) {
                return end;
            }
        }

        // collect possible ends: variation selector, skin tones, keycap, ZWJ sequences
        List<Integer> ends = new ArrayList<>();
        ends.add(index);
        int end = index;
        while (end < text.length()) {
            int cp = text.codePointAt(end);
            if (cp == 0xFE0F || cp == 0x20E3 || (cp >= 0x1F3FB && cp <= 0x1F3FF)) {
                end += Character.charCount(cp);
            } else if (cp == 0x200D && end + 1 < text.length()) {
                int next = text.codePointAt(end + 1);
                end += 1 + Character.charCount(next);
            } else {
                break;
            }
            ends.add(end);
        }
        for (int k = ends.size() - 1; k >= 0; k--) {
            if (exists(text.substring(start, ends.get(k)))) {
                return ends.get(k);
            }
        }
        return -1;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }
}
