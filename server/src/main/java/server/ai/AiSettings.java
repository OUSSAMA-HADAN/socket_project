package server.ai;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/**
 * Where the AI API key comes from (first match wins):
 * <ol>
 *   <li>environment variable {@code GEMINI_API_KEY}</li>
 *   <li>file {@code ai.properties} in the project root (or the working directory),
 *       key {@code gemini.apiKey}. Copy {@code ai.properties.example} to create it.</li>
 * </ol>
 */
public record AiSettings(String apiKey, String model, boolean webSearch) {

    public static final String FILE_NAME = "ai.properties";
    private static final String DEFAULT_MODEL = "gemini-3.6-flash";

    /** Tried in order after the configured model when it is overloaded or not available. */
    public static final List<String> FALLBACK_MODELS = List.of("gemini-3.6-flash", "gemini-3.5-flash-lite");

    /** Configured model first, then the fallbacks (no duplicates). */
    public List<String> modelsToTry() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(model);
        models.addAll(FALLBACK_MODELS);
        return List.copyOf(models);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("PASTE_");
    }

    public static AiSettings load() {
        Properties props = new Properties();
        Path file = findFile();
        if (file != null) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("Could not read " + file.toAbsolutePath() + ": " + e.getMessage());
            }
        }
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            key = props.getProperty("gemini.apiKey", "").trim();
        }
        String model = props.getProperty("gemini.model", DEFAULT_MODEL).trim();
        boolean webSearch = !"false".equalsIgnoreCase(props.getProperty("gemini.webSearch", "true").trim());
        return new AiSettings(key, model.isEmpty() ? DEFAULT_MODEL : model, webSearch);
    }

    /**
     * The file lives in the project root. The server may be started from the root or from
     * the server/ module folder (IntelliJ, mvn -pl server), so look in both.
     */
    private static Path findFile() {
        for (Path candidate : List.of(Path.of(FILE_NAME), Path.of("..", FILE_NAME))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }
}
