package server.ai;

import common.Config;
import common.Message;
import common.MessageType;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The "AI Assistant" contact. Lives on the server so the API key never leaves it.
 * <p>
 * Concurrency:
 * <ul>
 *   <li>API calls run on a small dedicated thread pool, so a slow answer never blocks
 *       the socket reader/writer threads of any client;</li>
 *   <li>questions from different users are answered in parallel, but each user's questions
 *       are answered one at a time (per-user lock) so the conversation stays in order.</li>
 * </ul>
 * Resilience: if web search is not included in the account's plan, the bot keeps answering
 * without it; if a model is overloaded or not available, the next model is tried.
 */
public class AiBot {

    private static final int WORKER_THREADS = 4;
    private static final int MAX_SOURCES = 3;
    /**
     * How many times to go through the model list. Kept at 1: free keys can have as little as
     * 20 requests per day per model, and every retry spends one.
     */
    private static final int ROUNDS = 1;
    private static final long ROUND_PAUSE_MILLIS = 2000;
    private static final String NO_SEARCH_NOTE =
            "\n\n(Answered without web search: Google Search isn't included in this API key's free plan.)";

    private final AiSettings settings;
    private final GeminiClient gemini;
    private final Consumer<Message> deliver;
    private final ExecutorService workers = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
        Thread t = new Thread(r, "ai-worker");
        t.setDaemon(true);
        return t;
    });
    /** Per user: id of the last answer, so Gemini remembers the conversation. */
    private final Map<String, String> lastInteraction = new ConcurrentHashMap<>();
    private final Map<String, Object> userLocks = new ConcurrentHashMap<>();
    /** Users already told that answers come without web search (told once per session). */
    private final Set<String> toldNoSearch = ConcurrentHashMap.newKeySet();
    /** Starts from gemini.webSearch; turns false (for this server run) if Google says search isn't in the plan. */
    private volatile boolean webSearchAvailable;

    /** @param deliver sends a message to the user named in its recipient */
    public AiBot(AiSettings settings, Consumer<Message> deliver) {
        this.settings = settings;
        this.gemini = new GeminiClient(settings.apiKey());
        this.deliver = deliver;
        this.webSearchAvailable = settings.webSearch();
    }

    public boolean isConfigured() {
        return settings.isConfigured();
    }

    public String model() {
        return settings.model();
    }

    /** Called by a client's reader thread; returns immediately. */
    public void ask(Message question) {
        String user = question.getSender();
        if (question.getType() != MessageType.TEXT) {
            reply(user, "For now I can only read text messages 🙂");
            return;
        }
        if (!settings.isConfigured()) {
            reply(user, "I'm not configured yet. Put a Gemini API key in the server's "
                    + AiSettings.FILE_NAME + " file (see ai.properties.example) and restart the server.");
            return;
        }
        deliver.accept(Message.typing(Config.AI_BOT_NAME, user));
        workers.execute(() -> answer(user, question.getText()));
    }

    /** Forget a user's conversation (when they disconnect). */
    public void forget(String user) {
        lastInteraction.remove(user);
        userLocks.remove(user);
        toldNoSearch.remove(user);
    }

    public void shutdown() {
        workers.shutdownNow();
    }

    // ---------------------------------------------------------------- answering (worker threads)

    private void answer(String user, String question) {
        synchronized (userLocks.computeIfAbsent(user, u -> new Object())) {
            try {
                GeminiClient.Answer answer = askAnyModel(user, question);
                if (answer.id() != null) {
                    lastInteraction.put(user, answer.id());
                }
                String text = format(answer);
                if (settings.webSearch() && !webSearchAvailable && toldNoSearch.add(user)) {
                    text += NO_SEARCH_NOTE;
                }
                reply(user, text);
            } catch (GeminiClient.ApiException e) {
                log("Gemini error for " + user + ": " + e.getMessage());
                reply(user, describe(e));
            } catch (GeminiClient.EmptyAnswerException e) {
                reply(user, "Sorry, none of the AI models gave me an answer to that. Could you rephrase it?");
            } catch (HttpTimeoutException e) {
                reply(user, "The AI models are too slow to answer right now. Try again in a minute.");
            } catch (IOException e) {
                reply(user, "I couldn't reach the AI service (" + e.getMessage() + "). Try again in a moment.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Tries the configured model, then the fallbacks, while a model is overloaded,
     * unavailable, or answers with no text.
     */
    private GeminiClient.Answer askAnyModel(String user, String question) throws IOException, InterruptedException {
        IOException lastError = null;
        for (int round = 1; round <= ROUNDS; round++) {
            if (round > 1) {
                log("All models busy, waiting before round " + round);
                Thread.sleep(ROUND_PAUSE_MILLIS);
            }
            for (String model : settings.modelsToTry()) {
                try {
                    return askModel(model, user, question);
                } catch (GeminiClient.EmptyAnswerException e) {
                    log(e.getMessage() + ", trying the next model. Raw response: " + e.rawResponse());
                    lastError = e;
                } catch (HttpTimeoutException e) {
                    log(model + " timed out, trying the next model");
                    lastError = e;
                } catch (GeminiClient.ApiException e) {
                    if (!e.isTemporary() && !e.isModelUnavailable() && e.status() != 429) {
                        throw e;
                    }
                    // 429 here = this model's daily quota is used up; each model has its own quota
                    log(model + " unavailable (" + e.status() + "), trying the next model");
                    lastError = e;
                }
            }
        }
        throw lastError;
    }

    /** One model: with web search if possible, keeping the conversation if possible. */
    private GeminiClient.Answer askModel(String model, String user, String question)
            throws IOException, InterruptedException {
        String previous = lastInteraction.get(user);
        boolean search = webSearchAvailable;
        try {
            return gemini.ask(model, question, systemInstruction(user, search), previous, search);
        } catch (GeminiClient.EmptyAnswerException e) {
            if (search && e.isToolCallFailure()) {
                // The model broke while calling the search tool: same question, no tools.
                log(model + " failed calling web search (" + e.getMessage() + "), retrying without it");
                return gemini.ask(model, question, systemInstruction(user, false), previous, false);
            }
            throw e;
        } catch (GeminiClient.ApiException e) {
            if (search && e.status() == 429) {
                // Most often: Google Search grounding is not in this key's free plan. Retry without it.
                GeminiClient.Answer answer = gemini.ask(model, question, systemInstruction(user, false), previous, false);
                webSearchAvailable = false;
                log("Web search is not available with this API key (" + e.apiMessage().strip()
                        + "). Answering without it from now on. Tip: set gemini.webSearch=false in "
                        + AiSettings.FILE_NAME + " to skip this check.");
                return answer;
            }
            if (previous != null && e.status() == 400) {
                lastInteraction.remove(user); // stored conversation expired or belongs to another model
                return gemini.ask(model, question, systemInstruction(user, search), null, search);
            }
            throw e;
        }
    }

    // ---------------------------------------------------------------- text helpers

    /**
     * Mentions web search only when the tool is really sent: telling a model about a tool it
     * doesn't have makes it try to call it anyway ("malformed_tool_call").
     */
    private static String systemInstruction(String user, boolean webSearch) {
        return "You are " + Config.AI_BOT_NAME + ", a helpful assistant inside ChatFX, a desktop chat app. "
                + "You are talking with " + user + ". Today is " + LocalDate.now() + ". "
                + "Reply in the user's language, concisely, in plain text (no markdown tables or headings). "
                + (webSearch
                    ? "Use Google Search whenever the question needs recent or factual information."
                    : "You have no tools and no internet access: answer from your own knowledge, "
                    + "and say so when something may have changed recently.");
    }

    private static String format(GeminiClient.Answer answer) {
        StringBuilder sb = new StringBuilder(cleanMarkdown(answer.text()));
        if (!answer.sources().isEmpty()) {
            sb.append("\n\nSources:");
            answer.sources().stream().limit(MAX_SOURCES).forEach(s ->
                    sb.append("\n• ").append(s.title().isBlank() ? s.url() : s.title() + " — " + s.url()));
        }
        return sb.toString();
    }

    /** The chat shows plain text, so drop the most common markdown symbols. */
    private static String cleanMarkdown(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "• ")
                .replaceAll("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])", "$1"); // *italic*
    }

    /** Shows Google's own reason instead of guessing. */
    private static String describe(GeminiClient.ApiException e) {
        String reason = e.apiMessage().strip();
        return switch (e.status()) {
            case 400, 401, 403 -> "The AI service rejected the request. Check the API key and model in "
                    + AiSettings.FILE_NAME + ".\nGoogle says: " + reason;
            case 429 -> "The free daily quota of every AI model is used up. Try again tomorrow, or enable "
                    + "billing on the Google project behind the API key.\nGoogle says: " + reason;
            case 500, 503, 504 -> "All AI models are busy right now. Try again in a minute.\nGoogle says: " + reason;
            default -> "The AI service had a problem.\nGoogle says: " + reason;
        };
    }

    private void reply(String user, String text) {
        deliver.accept(Message.text(Config.AI_BOT_NAME, user, text));
    }

    private static void log(String text) {
        System.out.println("[AI] [" + Thread.currentThread().getName() + "] " + text);
    }
}
