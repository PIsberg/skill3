package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpPageFetcherTest {

    // A literal public IP so validate() never does a DNS lookup in tests, while http.send is mocked.
    private static final String PUBLIC = "https://93.184.216.34/";

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    @Test
    void returnsBodyOnSuccess() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "<html>ok</html>")).when(http).send(any(), any());

        HttpPageFetcher fetcher = new HttpPageFetcher(http);
        assertEquals("<html>ok</html>", fetcher.fetch(PUBLIC));
    }

    @Test
    void throwsOnNotFound() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(404, "nope")).when(http).send(any(), any());

        HttpPageFetcher fetcher = new HttpPageFetcher(http);
        assertThrows(IOException.class, () -> fetcher.fetch(PUBLIC + "missing"));
    }

    private static HttpResponse<String> redirect(int status, String location) {
        HttpResponse<String> resp = response(status, "");
        when(resp.headers()).thenReturn(
                HttpHeaders.of(Map.of("Location", List.of(location)), (a, b) -> true));
        return resp;
    }

    @Test
    void refusesRedirectToPrivateAddress() throws Exception {
        // A public URL 30x-ing to the cloud metadata IP is the classic SSRF bypass; every
        // hop must be re-validated, and the private target must never be requested.
        HttpClient http = mock(HttpClient.class);
        doReturn(redirect(302, "http://169.254.169.254/latest/meta-data/")).when(http).send(any(), any());

        HttpPageFetcher fetcher = new HttpPageFetcher(http);
        IOException e = assertThrows(IOException.class, () -> fetcher.fetch(PUBLIC));
        assertTrue(e.getMessage().contains("private/internal"));
        verify(http, times(1)).send(any(), any());
    }

    @Test
    void capsRedirectChainLength() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(redirect(301, PUBLIC)).when(http).send(any(), any()); // redirects forever

        HttpPageFetcher fetcher = new HttpPageFetcher(http);
        IOException e = assertThrows(IOException.class, () -> fetcher.fetch(PUBLIC));
        assertTrue(e.getMessage().contains("Too many redirects"));
    }

    @Test
    void truncatingSubscriberCapsBodyAndCancelsUpstream() throws Exception {
        var sub = new HttpPageFetcher.TruncatingSubscriber(10);
        AtomicBoolean cancelled = new AtomicBoolean();
        sub.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) {
            }
            @Override public void cancel() {
                cancelled.set(true);
            }
        });

        sub.onNext(List.of(ByteBuffer.wrap("hello ".getBytes(StandardCharsets.UTF_8))));
        assertFalse(cancelled.get()); // still under the cap
        sub.onNext(List.of(ByteBuffer.wrap("world and far more".getBytes(StandardCharsets.UTF_8))));
        assertTrue(cancelled.get()); // cap hit mid-stream -> download aborted

        byte[] body = sub.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals("hello worl", new String(body, StandardCharsets.UTF_8)); // exactly 10 bytes

        sub.onComplete(); // late signal after truncation must not change the result
        assertEquals(10, sub.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS).length);
    }

    @Test
    void truncatingSubscriberPassesBodyUnderCapThrough() throws Exception {
        var sub = new HttpPageFetcher.TruncatingSubscriber(1024);
        sub.onSubscribe(new Flow.Subscription() {
            @Override public void request(long n) {
            }
            @Override public void cancel() {
            }
        });
        sub.onNext(List.of(ByteBuffer.wrap("small body".getBytes(StandardCharsets.UTF_8))));
        sub.onComplete();
        byte[] body = sub.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals("small body", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void charsetFollowsContentTypeAndDefaultsToUtf8() {
        assertEquals(StandardCharsets.ISO_8859_1, HttpPageFetcher.charset("text/html; charset=ISO-8859-1"));
        assertEquals(StandardCharsets.UTF_16, HttpPageFetcher.charset("text/html; charset=\"utf-16\"; boundary=x"));
        assertEquals(StandardCharsets.UTF_8, HttpPageFetcher.charset("text/html"));
        assertEquals(StandardCharsets.UTF_8, HttpPageFetcher.charset(""));
        assertEquals(StandardCharsets.UTF_8, HttpPageFetcher.charset("text/html; charset=no-such-charset"));
    }

    @Test
    void validateRejectsNonHttpSchemes() {
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("file:///etc/passwd"));
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("gopher://example.com/"));
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("ftp://example.com/x"));
    }

    @Test
    void validateRejectsLoopbackAndAnyLocal() {
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://127.0.0.1/admin"));
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://[::1]/"));
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://0.0.0.0/"));
    }

    @Test
    void validateRejectsPrivateAndLinkLocalRanges() {
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://10.0.0.1/"));
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://192.168.1.1/"));
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://172.16.0.5/"));
        // Cloud metadata endpoint — link-local, a classic SSRF target.
        assertThrows(IOException.class, () -> HttpPageFetcher.validate("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void validateAllowsPublicAddress() throws Exception {
        assertNotNull(HttpPageFetcher.validate(PUBLIC));
        assertTrue(HttpPageFetcher.validate(PUBLIC).getHost().contains("93.184.216.34"));
    }
}
