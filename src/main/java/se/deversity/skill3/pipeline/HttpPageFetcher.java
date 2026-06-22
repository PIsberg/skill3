package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** {@link PageFetcher} over {@link HttpClient}, following redirects. */
public class HttpPageFetcher implements PageFetcher {

    private static final String UA =
            "Mozilla/5.0 (compatible; Skill3/0.1; +https://github.com/)";

    private final HttpClient http;

    public HttpPageFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public HttpPageFetcher(HttpClient http) {
        this.http = http;
    }

    @Override
    public String fetch(String url) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Fetch of " + url + " returned HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Fetch interrupted: " + url, e);
        }
    }
}
