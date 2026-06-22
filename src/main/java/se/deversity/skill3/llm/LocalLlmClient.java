package se.deversity.skill3.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * {@link ChatModel} backed by a local, OpenAI-compatible chat-completions
 * endpoint (e.g. Ollama at {@code http://localhost:11434}). Non-streaming for
 * simplicity; the long read timeout accommodates slow local generation.
 */
public class LocalLlmClient implements ChatModel {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final String endpoint;
    private final String model;
    private final HttpClient http;
    private final int maxTokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public LocalLlmClient(String endpoint, String model) {
        this(endpoint, model, defaultClient(), DEFAULT_MAX_TOKENS);
    }

    public LocalLlmClient(String endpoint, String model, HttpClient http) {
        this(endpoint, model, http, DEFAULT_MAX_TOKENS);
    }

    public LocalLlmClient(String endpoint, String model, HttpClient http, int maxTokens) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.model = model;
        this.http = http;
        this.maxTokens = maxTokens;
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public String complete(String system, String user) throws IOException {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        String json = mapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "/v1/chat/completions"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Local LLM returned HTTP " + resp.statusCode()
                        + ": " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IOException("Local LLM response had no message content");
            }
            return content.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Local LLM request interrupted", e);
        }
    }
}
