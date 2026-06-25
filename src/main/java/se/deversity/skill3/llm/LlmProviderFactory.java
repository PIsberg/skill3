package se.deversity.skill3.llm;

import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AISecure;

import java.util.Locale;

/**
 * Builds a {@link ChatModel} for a named provider, centralizing the provider switch, the
 * per-provider key resolution (flag, then environment variable), and validation. Adding a
 * provider is one {@code case} here instead of another branch wired into the CLI.
 */
@AISecure(aspect = "LLM provider credential resolution and model selection")
public final class LlmProviderFactory {

    private LlmProviderFactory() {
    }

    /**
     * Inputs for {@link #create}; {@code key}, {@code temperature}, and {@code authToken} are
     * optional. {@code authToken} is a Claude subscription (Pro/Max) OAuth access token; when set
     * for the {@code anthropic} provider it is used in preference to {@code key}.
     */
    public record Config(String provider, String endpoint, String model, int maxTokens,
                         @Nullable String key, @Nullable Double temperature,
                         @Nullable String authToken) {
    }

    /** {@return whether {@code provider} is a capable hosted provider} Used to default verification on. */
    public static boolean isCapable(String provider) {
        return "openai".equals(provider) || "anthropic".equals(provider);
    }

    /**
     * @return a {@link ChatModel} for the configured provider
     * @throws IllegalArgumentException with a user-facing message on an unknown provider or a
     *         hosted provider that is missing its key
     */
    public static ChatModel create(Config cfg) {
        return switch (cfg.provider().toLowerCase(Locale.ROOT)) {
            case "anthropic" -> {
                // Prefer a Claude subscription token (Authorization: Bearer + oauth beta header)
                // when supplied; otherwise fall back to a standard API key (x-api-key). Both remain
                // valid ways to authenticate the anthropic provider.
                String token = resolveKey(cfg.authToken(), "ANTHROPIC_AUTH_TOKEN");
                if (token != null) {
                    yield AnthropicChatModel.withSubscription(token, cfg.model(), cfg.maxTokens());
                }
                String key = resolveKey(cfg.key(), "ANTHROPIC_API_KEY");
                if (key == null) {
                    throw new IllegalArgumentException(
                            "anthropic provider needs credentials: pass --llm-auth-token or set "
                                    + "ANTHROPIC_AUTH_TOKEN (Claude subscription), or pass --llm-key "
                                    + "or set ANTHROPIC_API_KEY.");
                }
                yield AnthropicChatModel.withApiKey(key, cfg.model(), cfg.maxTokens());
            }
            case "openai" -> {
                String key = resolveKey(cfg.key(), "LLM_API_KEY");
                if (key == null) {
                    throw new IllegalArgumentException(
                            "openai provider needs a key: pass --llm-key or set LLM_API_KEY.");
                }
                yield new LocalLlmClient(cfg.endpoint(), cfg.model(), cfg.maxTokens(), key, cfg.temperature());
            }
            case "local" ->
                    new LocalLlmClient(cfg.endpoint(), cfg.model(), cfg.maxTokens(), cfg.key(), cfg.temperature());
            default -> throw new IllegalArgumentException(
                    "Unknown --llm-provider '" + cfg.provider() + "' (use local | openai | anthropic).");
        };
    }

    private static @Nullable String resolveKey(@Nullable String explicit, String envVar) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String env = System.getenv(envVar);
        return env != null && !env.isBlank() ? env : null;
    }
}
