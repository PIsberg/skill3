package se.deversity.skill3.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import se.deversity.vibetags.annotations.AISecure;

import java.io.IOException;

/**
 * {@link ChatModel} backed by the hosted Anthropic Messages API via the official
 * {@code anthropic-java} SDK — the supported path for Claude synthesis (Opus 4.8 by
 * default). Opus 4.8 rejects {@code temperature}/{@code top_p}/{@code budget_tokens},
 * so none are sent; quality is steered by the model itself and the synthesizer prompt.
 *
 * <p>Non-streaming: a {@code SKILL.md} fits comfortably under the {@code maxTokens} cap.
 */
@AISecure(aspect = "Anthropic API credential handling and hosted-provider network egress")
public class AnthropicChatModel implements ChatModel {

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    public AnthropicChatModel(String apiKey, String model, int maxTokens) {
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).build(), model, maxTokens);
    }

    public AnthropicChatModel(AnthropicClient client, String model, int maxTokens) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String complete(String system, String user) throws IOException {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(user)
                .build();
        try {
            Message response = client.messages().create(params);
            StringBuilder out = new StringBuilder();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(text -> out.append(text.text()));
            }
            return out.toString();
        } catch (RuntimeException e) {
            // SDK surfaces HTTP/transport failures as unchecked exceptions; the pipeline
            // treats synthesis failures as IOExceptions.
            throw new IOException("Anthropic request failed: " + e.getMessage(), e);
        }
    }
}
