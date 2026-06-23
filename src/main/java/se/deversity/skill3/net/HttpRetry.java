package se.deversity.skill3.net;

import java.io.IOException;
import java.net.http.HttpResponse;

/**
 * Retries an HTTP send on transient failures — connection errors and the retryable status
 * codes 429/500/502/503/504 — with exponential backoff, honoring a numeric {@code Retry-After}
 * header when present. A single run makes many independent network calls (search, page fetches,
 * model calls); without this one transient blip aborts the whole pipeline.
 *
 * <p>Non-transient responses (any other status, including 2xx and 4xx) are returned immediately
 * for the caller to interpret; only the final attempt's outcome — success, transient response,
 * or thrown {@link IOException} — is surfaced. The backoff base is injectable so tests run fast.
 */
public final class HttpRetry {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_BASE_MILLIS = 500;
    private static final long MAX_BACKOFF_MILLIS = 10_000;

    /** A single HTTP send returning a string-bodied response. */
    @FunctionalInterface
    public interface Send {
        HttpResponse<String> send() throws IOException, InterruptedException;
    }

    private final int maxAttempts;
    private final long baseMillis;

    public HttpRetry() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_MILLIS);
    }

    HttpRetry(int maxAttempts, long baseMillis) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseMillis = Math.max(0, baseMillis);
    }

    /**
     * Executes {@code send}, retrying transient failures up to {@code maxAttempts - 1} times.
     *
     * @return the response of the first non-transient attempt, or the final attempt's response
     * @throws IOException if the final attempt fails (earlier transient {@link IOException}s are
     *                     swallowed and retried)
     */
    public HttpResponse<String> execute(Send send) throws IOException, InterruptedException {
        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            try {
                HttpResponse<String> resp = send.send();
                if (!isTransient(resp.statusCode())) {
                    return resp;
                }
                pause(backoffMillis(attempt, retryAfterMillis(resp)));
            } catch (IOException retryable) {
                pause(backoffMillis(attempt, -1)); // connection-level blip; back off and retry
            }
        }
        return send.send(); // final attempt: its result or exception is the caller's to handle
    }

    static boolean isTransient(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    long backoffMillis(int attempt, long retryAfterMillis) {
        if (retryAfterMillis >= 0) {
            return Math.min(retryAfterMillis, MAX_BACKOFF_MILLIS);
        }
        long exponential = baseMillis * (1L << (attempt - 1)); // base, 2x, 4x, ...
        return Math.min(exponential, MAX_BACKOFF_MILLIS);
    }

    static long retryAfterMillis(HttpResponse<String> resp) {
        return resp.headers().firstValue("retry-after").map(HttpRetry::parseRetryAfter).orElse(-1L);
    }

    /** Parses the numeric delta-seconds form of {@code Retry-After}; HTTP-date form is ignored. */
    private static long parseRetryAfter(String value) {
        try {
            return Long.parseLong(value.trim()) * 1000L;
        } catch (NumberFormatException notSeconds) {
            return -1L;
        }
    }

    private void pause(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }
}
