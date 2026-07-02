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
 * <p>Two credential modes are supported, both selected by {@link se.deversity.skill3.llm.LlmProviderFactory}:
 * an <strong>API key</strong> ({@link #withApiKey}, sent as {@code x-api-key}) and a
 * <strong>Claude subscription</strong> OAuth bearer token ({@link #withSubscription}, sent as
 * {@code Authorization: Bearer} with the required {@code anthropic-beta: oauth-2025-04-20}
 * header). The two are mutually exclusive on a single client — sending both is rejected by the API.
 *
 * <p>Non-streaming: a {@code SKILL.md} fits comfortably under the {@code maxTokens} cap.
 */
@AISecure(aspect = "Anthropic API credential handling and hosted-provider network egress")
public class AnthropicChatModel implements ChatModel {

    /**
     * Beta header that opts a request into Claude-subscription OAuth auth. {@code /v1/messages}
     * rejects a subscription bearer token without it, so it is sent on every subscription client.
     */
    static final String OAUTH_BETA_HEADER = "oauth-2025-04-20";

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    /**
     * @return a model that authenticates with an Anthropic API key ({@code sk-ant-api...}), sent as
     *         the {@code x-api-key} header. This is the original, fully supported path.
     */
    public static AnthropicChatModel withApiKey(String apiKey, String model, int maxTokens) {
        return new AnthropicChatModel(apiKey, model, maxTokens);
    }

    /**
     * @return a model that authenticates with a Claude subscription (Pro/Max) OAuth access token,
     *         sent as {@code Authorization: Bearer} alongside the {@code anthropic-beta:
     *         oauth-2025-04-20} header the Messages API requires for subscription auth. The caller
     *         supplies a current access token; this class does not run the OAuth login or refresh.
     */
    public static AnthropicChatModel withSubscription(String authToken, String model, int maxTokens) {
        // The SDK retries transient failures (429/5xx) with backoff and honors Retry-After.
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .authToken(authToken)
                .putHeader("anthropic-beta", OAUTH_BETA_HEADER)
                .maxRetries(3)
                .build();
        return new AnthropicChatModel(client, model, maxTokens);
    }

    public AnthropicChatModel(String apiKey, String model, int maxTokens) {
        // The SDK retries transient failures (429/5xx) with backoff and honors Retry-After.
        this(AnthropicOkHttpClient.builder().apiKey(apiKey).maxRetries(3).build(), model, maxTokens);
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
            if (out.toString().isBlank()) {
                // A refusal or a response with no text blocks must not flow into the
                // post-processor as an "empty draft" that silently becomes a stub skill.
                throw new IOException("Anthropic response contained no text (stop_reason="
                        + response.stopReason() + ")");
            }
            return out.toString();
        } catch (RuntimeException e) {
            // SDK surfaces HTTP/transport failures as unchecked exceptions; the pipeline
            // treats synthesis failures as IOExceptions. getMessage() can be null, so
            // include the exception class for a diagnosable message either way.
            throw new IOException("Anthropic request failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()), e);
        }
    }
}
