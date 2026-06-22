package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalLlmClientTest {

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }

    @Test
    void parsesChatCompletionContent() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"content\":\"hello world\"}}]}"))
                .when(http).send(any(), any());

        LocalLlmClient client = new LocalLlmClient("http://localhost:11434", "m", http);
        assertEquals("hello world", client.complete("sys", "user"));
    }

    @Test
    void sendsBearerHeaderWhenKeyProvided() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"))
                .when(http).send(any(), any());

        new LocalLlmClient("https://gateway.example", "m", http, 4096, "secret-key", 0.2)
                .complete("sys", "user");

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        Optional<String> auth = req.getValue().headers().firstValue("Authorization");
        assertTrue(auth.isPresent());
        assertEquals("Bearer secret-key", auth.get());
    }

    @Test
    void omitsAuthorizationForKeylessLocalServer() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}"))
                .when(http).send(any(), any());

        new LocalLlmClient("http://localhost:11434", "m", http).complete("sys", "user");

        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any());
        assertFalse(req.getValue().headers().firstValue("Authorization").isPresent());
    }

    @Test
    void throwsOnNonSuccess() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(500, "boom")).when(http).send(any(), any());

        LocalLlmClient client = new LocalLlmClient("http://localhost:11434", "m", http);
        assertThrows(IOException.class, () -> client.complete("s", "u"));
    }

    @Test
    void throwsWhenContentMissing() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[]}")).when(http).send(any(), any());

        LocalLlmClient client = new LocalLlmClient("http://localhost:11434", "m", http);
        assertThrows(IOException.class, () -> client.complete("s", "u"));
    }
}
