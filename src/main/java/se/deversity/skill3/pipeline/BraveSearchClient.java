package se.deversity.skill3.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import se.deversity.skill3.net.HttpRetry;
import se.deversity.vibetags.annotations.AIPrivacy;
import se.deversity.vibetags.annotations.AISecure;

/** {@link SearchClient} backed by the Brave Search API. */
@AISecure(aspect = "external-API credential handling and the only network egress with a secret token")
public class BraveSearchClient implements SearchClient {

    private static final String ENDPOINT = "https://api.search.brave.com/res/v1/web/search";
    private static final HttpRetry RETRY = new HttpRetry();

    @AIPrivacy(reason = "Brave Search subscription token — never log, echo, or include in errors/fixtures")
    private final String apiKey;
    /**
     * Brave {@code freshness} range ({@code yyyy-MM-DDtoyyyy-MM-DD}), or null for no filter.
     * Anchored at the model's knowledge cutoff so discovery favours material the model is
     * unlikely to already know (e.g. recent MCP changes) over content it learned pre-cutoff.
     */
    private final String freshness;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public BraveSearchClient(String apiKey) {
        this(apiKey, (String) null);
    }

    public BraveSearchClient(String apiKey, String freshness) {
        this(apiKey, freshness, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    public BraveSearchClient(String apiKey, HttpClient http) {
        this(apiKey, null, http);
    }

    public BraveSearchClient(String apiKey, String freshness, HttpClient http) {
        this.apiKey = apiKey;
        this.freshness = freshness;
        this.http = http;
    }

    @Override
    public List<String> search(String query, int count) throws IOException {
        String uri = ENDPOINT + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&count=" + count;
        if (freshness != null && !freshness.isBlank()) {
            uri += "&freshness=" + URLEncoder.encode(freshness, StandardCharsets.UTF_8);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .GET()
                .build();
        try {
            HttpResponse<String> resp = RETRY.execute(
                    () -> http.send(req, HttpResponse.BodyHandlers.ofString()));
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Brave Search returned HTTP " + resp.statusCode());
            }
            return parse(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Brave Search interrupted", e);
        }
    }

    private List<String> parse(String body) throws IOException {
        List<String> urls = new ArrayList<>();
        JsonNode root = mapper.readTree(body);
        JsonNode results = root.path("web").path("results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                String url = r.path("url").asText(null);
                if (url != null && !url.isBlank()) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }
}
