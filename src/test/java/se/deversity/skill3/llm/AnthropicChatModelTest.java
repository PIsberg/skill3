package se.deversity.skill3.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnthropicChatModelTest {

    private static AnthropicClient clientReturning(Message response) {
        AnthropicClient client = mock(AnthropicClient.class);
        MessageService messages = mock(MessageService.class);
        when(client.messages()).thenReturn(messages);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(response);
        return client;
    }

    @Test
    void concatenatesTextBlocks() throws Exception {
        TextBlock text = mock(TextBlock.class);
        when(text.text()).thenReturn("# Skill\n\nbody");
        ContentBlock block = mock(ContentBlock.class);
        when(block.text()).thenReturn(Optional.of(text));
        Message response = mock(Message.class);
        when(response.content()).thenReturn(List.of(block));

        AnthropicChatModel model = new AnthropicChatModel(clientReturning(response), "claude-opus-4-8", 8192);
        assertEquals("# Skill\n\nbody", model.complete("system", "user"));
    }

    @Test
    void responseWithoutTextThrowsInsteadOfReturningEmptyDraft() {
        // A refusal / non-text response must fail loudly, not flow into the
        // post-processor as an empty draft that silently becomes a stub skill.
        Message response = mock(Message.class);
        when(response.content()).thenReturn(List.of());

        AnthropicChatModel model = new AnthropicChatModel(clientReturning(response), "claude-opus-4-8", 8192);
        IOException e = assertThrows(IOException.class, () -> model.complete("system", "user"));
        assertTrue(e.getMessage().contains("no text"));
    }

    @Test
    void sdkExceptionWithNullMessageStillProducesDiagnosableError() {
        AnthropicClient client = mock(AnthropicClient.class);
        MessageService messages = mock(MessageService.class);
        when(client.messages()).thenReturn(messages);
        when(messages.create(any(MessageCreateParams.class))).thenThrow(new IllegalStateException());

        AnthropicChatModel model = new AnthropicChatModel(client, "claude-opus-4-8", 8192);
        IOException e = assertThrows(IOException.class, () -> model.complete("system", "user"));
        assertTrue(e.getMessage().contains("IllegalStateException"));
    }
}
