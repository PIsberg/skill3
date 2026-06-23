package se.deversity.skill3.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpRetryTest {

    private static final HttpRetry FAST = new HttpRetry(3, 0); // no real sleeping in tests

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> resp(int status) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        return r;
    }

    @Test
    void returnsImmediatelyOnSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> out = FAST.execute(() -> {
            calls.incrementAndGet();
            return resp(200);
        });
        assertEquals(200, out.statusCode());
        assertEquals(1, calls.get());
    }

    @Test
    void retriesTransientStatusThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> out = FAST.execute(() -> resp(calls.incrementAndGet() == 1 ? 503 : 200));
        assertEquals(200, out.statusCode());
        assertEquals(2, calls.get());
    }

    @Test
    void retriesIoExceptionThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> out = FAST.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new IOException("connection reset");
            }
            return resp(200);
        });
        assertEquals(200, out.statusCode());
        assertEquals(2, calls.get());
    }

    @Test
    void returnsLastTransientResponseAfterExhaustion() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> out = FAST.execute(() -> {
            calls.incrementAndGet();
            return resp(503);
        });
        assertEquals(503, out.statusCode());
        assertEquals(3, calls.get()); // maxAttempts
    }

    @Test
    void throwsWhenFinalAttemptThrows() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> FAST.execute(() -> {
            calls.incrementAndGet();
            throw new IOException("down");
        }));
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryNonTransientStatus() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpResponse<String> out = FAST.execute(() -> {
            calls.incrementAndGet();
            return resp(404);
        });
        assertEquals(404, out.statusCode());
        assertEquals(1, calls.get()); // 4xx is the caller's to interpret, not retried
    }

    @Test
    void classifiesTransientStatuses() {
        assertTrue(HttpRetry.isTransient(429));
        assertTrue(HttpRetry.isTransient(503));
        assertFalse(HttpRetry.isTransient(404));
        assertFalse(HttpRetry.isTransient(200));
    }

    @Test
    void honorsNumericRetryAfterAndExponentialBackoff() {
        HttpRetry r = new HttpRetry(5, 500);
        assertEquals(500, r.backoffMillis(1, -1));
        assertEquals(1000, r.backoffMillis(2, -1));
        assertEquals(2000, r.backoffMillis(3, -1));
        assertEquals(3000, r.backoffMillis(1, 3000)); // Retry-After wins over exponential
        assertEquals(10_000, r.backoffMillis(1, 999_999)); // capped

        HttpResponse<String> withHeader = mock(HttpResponse.class);
        when(withHeader.headers()).thenReturn(
                HttpHeaders.of(Map.of("retry-after", List.of("2")), (a, b) -> true));
        assertEquals(2000, HttpRetry.retryAfterMillis(withHeader));
    }
}
