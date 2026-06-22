package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameSanitizerTest {

    @Test
    void lowercasesAndHyphenates() {
        assertEquals("jdk-26", NameSanitizer.sanitize("JDK 26!"));
    }

    @Test
    void stripsReservedClaude() {
        assertEquals("mcp-for", NameSanitizer.sanitize("MCP for Claude"));
    }

    @Test
    void stripsReservedAnthropic() {
        assertEquals("sdk", NameSanitizer.sanitize("anthropic-sdk"));
    }

    @Test
    void fallbackWhenNothingValidRemains() {
        assertEquals("learned-skill", NameSanitizer.sanitize("claude"));
    }

    @Test
    void enforcesMaxLength() {
        String name = NameSanitizer.sanitize("a".repeat(100));
        assertTrue(name.length() <= 64);
    }

    @Test
    void neverContainsReservedWords() {
        String name = NameSanitizer.sanitize("Claude and Anthropic guide");
        assertFalse(name.contains("claude"));
        assertFalse(name.contains("anthropic"));
    }
}
