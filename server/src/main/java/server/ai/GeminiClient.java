package server.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for the Gemini Interactions API with Google Search grounding.
 * <p>
 * Conversation memory is kept by Google: each answer has an interaction id, and the next
 * question passes it as {@code previous_interaction_id}.
 * <p>
 * <b>How it works</b>: build a JSON request with Gson ({@code JsonObject}), send it with Java's
 * built-in {@code HttpClient} (POST, API key in a header), then read the JSON answer and pull
 * out the text and the web sources. Any non-200 answer becomes an {@link ApiException}.
 */
public class GeminiClient {

    private static final URI ENDPOINT = URI.create("https://generativelanguage.googleapis.com/v1beta/interactions");
    private static final String API_REVISION = "2026-05-20";

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Source(String title, String url) {
    }

    public record Answer(String id, String text, List<Source> sources) {
    }

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /** Blocking HTTP call: always run it off the socket threads. */
    public Answer ask(String model, String question, String systemInstruction, String previousId, boolean webSearch)
            throws IOException, InterruptedException {
        // 1. Build the request body: {"model": ..., "input": ..., "system_instruction": ..., ...}
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", question);
        body.addProperty("system_instruction", systemInstruction);
        if (webSearch) {
            JsonArray tools = new JsonArray();
            JsonObject search = new JsonObject();
            search.addProperty("type", "google_search");
            tools.add(search);
            body.add("tools", tools);
        }
        if (previousId != null) {
            body.addProperty("previous_interaction_id", previousId);
        }

        // 2. Send it (waits up to 45 s for the answer).
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .header("Api-Revision", API_REVISION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(response.statusCode(), errorMessage(response.body()));
        }
        // 3. Read the answer's JSON and extract the text.
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        Answer answer = parse(json);
        String status = string(json, "status");
        if (answer.text().isEmpty() || "failed".equals(status)) {
            // HTTP 200 but no usable answer, e.g. status "failed" with errors[{code:"malformed_tool_call"}]
            StringBuilder codes = new StringBuilder();
            for (JsonObject error : objects(json.get("errors"))) {
                codes.append(string(error, "code")).append(' ');
            }
            throw new EmptyAnswerException(model, status, codes.toString().strip(), withoutSignatures(response.body()));
        }
        return answer;
    }

    /**
     * Reads the answer text and the web sources (url_citation annotations) from the response.
     * Tolerant on purpose: {@code content} may be a list or a single object, and the text may
     * sit directly on the step.
     */
    private static Answer parse(JsonObject json) {
        String id = json.has("id") ? json.get("id").getAsString() : null;
        StringBuilder text = new StringBuilder();
        Map<String, Source> sources = new LinkedHashMap<>();

        for (JsonObject step : objects(json.get("steps"))) {
            if (!"model_output".equals(string(step, "type"))) {
                continue; // thoughts, search calls, search results...
            }
            text.append(string(step, "text"));
            for (JsonObject content : objects(step.get("content"))) {
                String type = string(content, "type");
                if (!type.isEmpty() && !"text".equals(type)) {
                    continue; // images or other parts
                }
                text.append(string(content, "text"));
                for (JsonObject a : objects(content.get("annotations"))) {
                    String url = string(a, "url");
                    if ("url_citation".equals(string(a, "type")) && !url.isEmpty()) {
                        sources.putIfAbsent(url, new Source(string(a, "title"), url));
                    }
                }
            }
        }
        return new Answer(id, text.toString().strip(), new ArrayList<>(sources.values()));
    }

    /** A JSON value that is a list of objects or a single object, as a list. */
    private static List<JsonObject> objects(JsonElement element) {
        List<JsonObject> result = new ArrayList<>();
        if (element == null) {
            return result;
        }
        if (element.isJsonObject()) {
            result.add(element.getAsJsonObject());
        } else if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    result.add(e.getAsJsonObject());
                }
            }
        }
        return result;
    }

    /** Thought signatures are long opaque blobs: drop them before logging a response. */
    private static String withoutSignatures(String body) {
        String compact = body.replaceAll("\"signature\"\\s*:\\s*\"[^\"]*\",?", "");
        return compact.length() > 1500 ? compact.substring(0, 1500) + "…" : compact;
    }

    private static String errorMessage(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error") && json.get("error").isJsonObject()) {
                return string(json.getAsJsonObject("error"), "message");
            }
        } catch (RuntimeException ignored) {
            // not JSON
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    private static String string(JsonObject json, String name) {
        JsonElement e = json.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    /** HTTP 200, but the response held no answer text (worth retrying / trying another model). */
    public static class EmptyAnswerException extends IOException {
        private final String errorCodes;
        private final String rawResponse;

        EmptyAnswerException(String model, String status, String errorCodes, String rawResponse) {
            super(model + " returned no answer (status: " + (status.isEmpty() ? "unknown" : status)
                    + (errorCodes.isEmpty() ? "" : ", errors: " + errorCodes) + ")");
            this.errorCodes = errorCodes;
            this.rawResponse = rawResponse;
        }

        /** The model broke while calling a tool (e.g. "malformed_tool_call"): retry without tools. */
        public boolean isToolCallFailure() {
            return errorCodes.contains("tool");
        }

        public String rawResponse() {
            return rawResponse;
        }
    }

    /** Non-2xx answer from the API. */
    public static class ApiException extends IOException {
        private final int status;
        private final String apiMessage;

        ApiException(int status, String message) {
            super("HTTP " + status + ": " + message);
            this.status = status;
            this.apiMessage = message;
        }

        public int status() {
            return status;
        }

        /** Google's own explanation, e.g. "You exceeded your current quota...". */
        public String apiMessage() {
            return apiMessage;
        }

        /** The model is busy or down for a moment: worth trying another model. */
        public boolean isTemporary() {
            return status == 500 || status == 503 || status == 504;
        }

        /** The model can't be used with this key at all (retired / not offered to this account). */
        public boolean isModelUnavailable() {
            return status == 404 && apiMessage.contains("model");
        }
    }
}
