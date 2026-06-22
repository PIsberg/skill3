package se.deversity.skill3.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ChatModel} backed by an OpenAI-compatible chat-completions endpoint —
 * a local Ollama at {@code http://localhost:11434} (no key) or a hosted gateway
 * such as OpenRouter/Together/Groq/OpenAI (Bearer key via {@code apiKey}).
 * Non-streaming for simplicity; the long read timeout accommodates slow generation.
 */
public class LocalLlmClient implements ChatModel {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final String endpoint;
    private final String model;
    private final HttpClient http;
    private final int maxTokens;
    /** Bearer token for hosted endpoints, or null for keyless local servers. */
    private final @Nullable String apiKey;
    /** Sampling temperature, or null to use the server default. */
    private final @Nullable Double temperature;
    private final ObjectMapper mapper = new ObjectMapper();

    public LocalLlmClient(String endpoint, String model) {
        this(endpoint, model, defaultClient(), DEFAULT_MAX_TOKENS);
    }

    public LocalLlmClient(String endpoint, String model, HttpClient http) {
        this(endpoint, model, http, DEFAULT_MAX_TOKENS);
    }

    public LocalLlmClient(String endpoint, String model, HttpClient http, int maxTokens) {
        this(endpoint, model, http, maxTokens, null, null);
    }

    public LocalLlmClient(String endpoint, String model, int maxTokens,
                          @Nullable String apiKey, @Nullable Double temperature) {
        this(endpoint, model, defaultClient(), maxTokens, apiKey, temperature);
    }

    public LocalLlmClient(String endpoint, String model, HttpClient http, int maxTokens,
                          @Nullable String apiKey, @Nullable Double temperature) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.model = model;
        this.http = http;
        this.maxTokens = maxTokens;
        this.apiKey = apiKey;
        this.temperature = temperature;
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public String complete(String system, String user) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("max_tokens", maxTokens);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        String json = mapper.writeValueAsString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + "/v1/chat/completions"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
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
