package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
