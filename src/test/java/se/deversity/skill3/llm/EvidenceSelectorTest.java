package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceSelectorTest {

    @Test
    void returnsAllInOrderWhenUnderBudget() {
        List<String> items = List.of("a", "b", "c");
        assertEquals(items, EvidenceSelector.topByRelevance(items, "mcp", 8));
    }

    @Test
    void keepsMostTopicRelevantInDocumentOrder() {
        List<String> items = List.of(
                "intro paragraph about nothing in particular",     // 0 tokens
                "the mcp tools list endpoint changed",             // mcp, tools
                "completely unrelated filler text here",           // 0
                "tools call now requires mcp version",             // tools, mcp
                "more filler that mentions neither term");          // 0

        List<String> top = EvidenceSelector.topByRelevance(items, "mcp tools", 2);

        // The two highest-scoring excerpts, returned in their original order.
        assertEquals(List.of(
                "the mcp tools list endpoint changed",
                "tools call now requires mcp version"), top);
    }

    @Test
    void fallsBackToDocumentOrderWhenTopicHasNoUsableTokens() {
        List<String> items = List.of("first", "second", "third");
        // "x" is too short to be a token, so there is no relevance signal.
        assertEquals(List.of("first", "second"), EvidenceSelector.topByRelevance(items, "x", 2));
    }

    @Test
    void emptyBudgetSelectsNothing() {
        assertTrue(EvidenceSelector.topByRelevance(List.of("a", "b"), "mcp", 0).isEmpty());
    }
}
