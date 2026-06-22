package se.deversity.skill3.llm;

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
