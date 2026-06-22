package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpPageFetcherTest {

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
        assertEquals("<html>ok</html>", fetcher.fetch("https://example.com"));
    }

    @Test
    void throwsOnNotFound() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(404, "nope")).when(http).send(any(), any());

        HttpPageFetcher fetcher = new HttpPageFetcher(http);
        assertThrows(IOException.class, () -> fetcher.fetch("https://example.com/missing"));
    }
}
