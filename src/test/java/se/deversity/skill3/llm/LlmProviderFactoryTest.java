package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmProviderFactoryTest {

    private static LlmProviderFactory.Config config(String provider, String key) {
        return new LlmProviderFactory.Config(provider, "http://localhost:11434", "m", 4096, key, null, null);
    }

    private static LlmProviderFactory.Config config(String provider, String key, String authToken) {
        return new LlmProviderFactory.Config(provider, "http://localhost:11434", "m", 4096, key, null, authToken);
    }

    @Test
    void localNeedsNoKey() {
        assertInstanceOf(LocalLlmClient.class, LlmProviderFactory.create(config("local", null)));
    }

    @Test
    void openaiUsesLocalClientWithKey() {
        assertInstanceOf(LocalLlmClient.class, LlmProviderFactory.create(config("openai", "sk-test")));
    }

    @Test
    void anthropicWithKeyBuildsAnthropicModel() {
        assertInstanceOf(AnthropicChatModel.class, LlmProviderFactory.create(config("anthropic", "sk-ant-test")));
    }

    @Test
    void anthropicWithSubscriptionTokenBuildsAnthropicModel() {
        assertInstanceOf(AnthropicChatModel.class,
                LlmProviderFactory.create(config("anthropic", null, "oat-subscription-token")));
    }

    @Test
    void anthropicPrefersSubscriptionTokenOverApiKey() {
        // Both supplied: the subscription path wins; either way an AnthropicChatModel is built.
        assertInstanceOf(AnthropicChatModel.class,
                LlmProviderFactory.create(config("anthropic", "sk-ant-test", "oat-subscription-token")));
    }

    @Test
    void anthropicWithNoCredentialsThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LlmProviderFactory.create(config("anthropic", null, null)));
        assertTrue(e.getMessage().contains("ANTHROPIC_AUTH_TOKEN"));
        assertTrue(e.getMessage().contains("ANTHROPIC_API_KEY"));
    }

    @Test
    void providerNameIsCaseInsensitive() {
        assertInstanceOf(LocalLlmClient.class, LlmProviderFactory.create(config("LOCAL", null)));
    }

    @Test
    void unknownProviderThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LlmProviderFactory.create(config("gemini", null)));
        assertTrue(e.getMessage().contains("Unknown"));
    }

    @Test
    void capabilityFlagsHostedProviders() {
        assertTrue(LlmProviderFactory.isCapable("openai"));
        assertTrue(LlmProviderFactory.isCapable("anthropic"));
        assertFalse(LlmProviderFactory.isCapable("local"));
    }
}
