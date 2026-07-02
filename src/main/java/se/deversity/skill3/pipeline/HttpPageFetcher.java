package se.deversity.skill3.pipeline;

import se.deversity.skill3.net.HttpRetry;
import se.deversity.vibetags.annotations.AISecure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * {@link PageFetcher} over {@link HttpClient}. URLs come from search results or a user
 * input file, i.e. partly untrusted, so each request is guarded against SSRF: only
 * {@code http}/{@code https} is allowed and the resolved host must be a public address —
 * loopback, link-local (incl. the cloud metadata IP {@code 169.254.169.254}), and private
 * ranges are refused. Redirects are followed manually so every hop is re-validated (a
 * public URL can 30x to an internal one), and the response body is size-capped.
 *
 * <p>Note: DNS rebinding (resolving to a public IP at the check, a private one at connect)
 * is not fully prevented — this is defence-in-depth against obviously-internal targets.
 */
@AISecure(aspect = "outbound page fetch egress for partly-untrusted URLs; SSRF guard must not be weakened")
public class HttpPageFetcher implements PageFetcher {

    private static final String UA =
            "Mozilla/5.0 (compatible; Skill3/0.1; +https://github.com/)";
    private static final int MAX_REDIRECTS = 5;
    /**
     * Cap on the downloaded body, enforced while the response streams in — a hostile server
     * must not be able to buffer an unbounded body in memory before a post-hoc cap applies.
     */
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final HttpRetry RETRY = new HttpRetry();

    private final HttpClient http;

    public HttpPageFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER) // follow manually to re-validate each hop
                .build());
    }

    public HttpPageFetcher(HttpClient http) {
        this.http = http;
    }

    @Override
    public String fetch(String url) throws IOException {
        URI uri = validate(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<String> resp = send(uri);
            int code = resp.statusCode();
            if (code / 100 == 2) {
                return resp.body(); // already size-capped while streaming by boundedBody()
            }
            if (code / 100 != 3) {
                throw new IOException("Fetch of " + uri + " returned HTTP " + code);
            }
            String location = resp.headers().firstValue("location").orElse(null);
            if (location == null) {
                throw new IOException("Redirect with no Location header: " + uri);
            }
            uri = validate(uri.resolve(location).toString()); // re-validate every hop (SSRF)
        }
        throw new IOException("Too many redirects fetching " + url);
    }

    private HttpResponse<String> send(URI uri) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        try {
            return RETRY.execute(() -> http.send(req, boundedBody()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Fetch interrupted: " + uri, e);
        }
    }

    /**
     * Body handler that enforces {@link #MAX_BYTES} during the download (see the constant's
     * javadoc) and decodes with the charset declared in {@code Content-Type} instead of
     * assuming UTF-8, so e.g. ISO-8859-1 documentation pages aren't silently mangled.
     */
    private static HttpResponse.BodyHandler<String> boundedBody() {
        return info -> {
            Charset cs = charset(info.headers().firstValue("content-type").orElse(""));
            return HttpResponse.BodySubscribers.mapping(
                    new TruncatingSubscriber(MAX_BYTES), bytes -> new String(bytes, cs));
        };
    }

    /** {@return the charset declared in {@code contentType}, or UTF-8 if absent or unknown} */
    static Charset charset(String contentType) {
        int at = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (at < 0) {
            return StandardCharsets.UTF_8;
        }
        String name = contentType.substring(at + "charset=".length());
        int semi = name.indexOf(';');
        if (semi >= 0) {
            name = name.substring(0, semi);
        }
        try {
            return Charset.forName(name.replace("\"", "").strip());
        } catch (IllegalArgumentException unknown) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * Collects the response body up to a byte cap, then cancels the upstream subscription and
     * completes with the truncated prefix — so an oversized response never occupies more than
     * {@code cap} bytes of heap. Reactive-stream signals are serialized by contract, so the
     * mutable state needs no synchronization; the result future carries the cross-thread
     * hand-off to {@code getBody()} readers.
     */
    static final class TruncatingSubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int cap;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        TruncatingSubscriber(int cap) {
            this.cap = cap;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            // Minimal view: callers must not be able to complete the internal future.
            return result.minimalCompletionStage();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return; // already truncated; late chunks are dropped
            }
            for (ByteBuffer b : items) {
                int take = Math.min(b.remaining(), cap - buf.size());
                byte[] chunk = new byte[take];
                b.get(chunk);
                buf.write(chunk, 0, take);
                if (buf.size() >= cap) {
                    subscription.cancel();
                    result.complete(buf.toByteArray());
                    return;
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            result.completeExceptionally(t); // no-op if already completed by truncation
        }

        @Override
        public void onComplete() {
            result.complete(buf.toByteArray()); // no-op if already completed by truncation
        }
    }

    /**
     * Parses {@code url} and rejects anything unsafe to fetch: non-http(s) schemes and hosts
     * that resolve to a loopback, any-local, link-local or private address.
     *
     * @return the validated URI
     * @throws IOException if the URL is malformed, non-http(s), or resolves to a non-public host
     */
    static URI validate(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed URL: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("Refusing non-http(s) URL: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("URL has no host: " + url);
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                        || isUniqueLocalV6(addr)) {
                    throw new IOException("Refusing to fetch private/internal address: "
                            + host.toLowerCase(Locale.ROOT) + " -> " + addr.getHostAddress());
                }
            }
        } catch (UnknownHostException e) {
            throw new IOException("Cannot resolve host: " + host, e);
        }
        return uri;
    }

    /** IPv6 unique-local block fc00::/7, which {@link InetAddress} does not flag as site-local. */
    private static boolean isUniqueLocalV6(InetAddress addr) {
        byte[] b = addr.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }
}
