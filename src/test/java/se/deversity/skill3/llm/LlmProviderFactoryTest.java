package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmProviderFactoryTest {

    private static LlmProviderFactory.Config config(String provider, String key) {
        return new LlmProviderFactory.Config(provider, "http://localhost:11434", "m", 4096, key, null);
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
