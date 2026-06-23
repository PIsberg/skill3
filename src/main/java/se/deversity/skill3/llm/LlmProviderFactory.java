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

    /** Inputs for {@link #create}; {@code key} and {@code temperature} are optional. */
    public record Config(String provider, String endpoint, String model, int maxTokens,
                         @Nullable String key, @Nullable Double temperature) {
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
                String key = resolveKey(cfg.key(), "ANTHROPIC_API_KEY");
                if (key == null) {
                    throw new IllegalArgumentException(
                            "anthropic provider needs a key: pass --llm-key or set ANTHROPIC_API_KEY.");
                }
                yield new AnthropicChatModel(key, cfg.model(), cfg.maxTokens());
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
