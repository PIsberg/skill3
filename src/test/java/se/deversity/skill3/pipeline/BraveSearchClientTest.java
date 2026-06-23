package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BraveSearchClientTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    @Test
    void parsesResultUrls() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200,
                "{\"web\":{\"results\":[{\"url\":\"https://a\"},{\"url\":\"https://b\"}]}}"))
                .when(http).send(any(), any());

        BraveSearchClient client = new BraveSearchClient("key", http);
        assertEquals(List.of("https://a", "https://b"), client.search("mcp", 10));
    }

    @Test
    void emptyResultsYieldEmptyList() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"web\":{\"results\":[]}}")).when(http).send(any(), any());

        BraveSearchClient client = new BraveSearchClient("key", http);
        assertEquals(List.of(), client.search("mcp", 10));
    }

    @Test
    void appendsFreshnessRangeWhenSet() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"web\":{\"results\":[]}}")).when(http).send(any(), any());

        new BraveSearchClient("key", "2026-01-01to2026-06-22", http).search("mcp", 10);

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        assertTrue(req.getValue().uri().toString().contains("freshness=2026-01-01to2026-06-22"));
    }

    @Test
    void omitsFreshnessWhenNull() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"web\":{\"results\":[]}}")).when(http).send(any(), any());

        new BraveSearchClient("key", http).search("mcp", 10);

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        assertFalse(req.getValue().uri().toString().contains("freshness="));
    }

    @Test
    void throwsOnNonSuccess() throws Exception {
        HttpClient http = mock(HttpClient.class);
        // 401 is non-transient, so it surfaces immediately (transient 429/5xx retry is in HttpRetryTest).
        doReturn(response(401, "unauthorized")).when(http).send(any(), any());

        BraveSearchClient client = new BraveSearchClient("key", http);
        assertThrows(IOException.class, () -> client.search("mcp", 10));
    }
}
