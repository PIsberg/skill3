package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifierTest {

    private ContextBundle bundle() {
        Source s = new Source("https://modelcontextprotocol.io/spec");
        s.published = LocalDate.of(2026, 5, 1);
        s.excerpts.add("MCP became stateless in the latest revision.");
        s.codeBlocks.add("client.call(\"tools/list\");");
        return new ContextBundle("mcp", "claude-opus-4-8",
                new Cutoff(YearMonth.of(2026, 1), "test"), List.of(s));
    }

    @Test
    void passesDraftAndSourceEvidenceToModel() throws Exception {
        AtomicReference<String> seenUser = new AtomicReference<>();
        ChatModel recorder = (system, user) -> {
            seenUser.set(user);
            return "corrected draft";
        };

        String out = new Verifier(recorder).verify(
                "# draft\n\nMCP shipped X on 2026-07-28.", bundle(), LocalDate.of(2026, 6, 22));

        assertEquals("corrected draft", out);
        String user = seenUser.get();
        assertTrue(user.contains("Today: 2026-06-22"));
        assertTrue(user.contains("MCP shipped X on 2026-07-28."));               // the draft
        assertTrue(user.contains("https://modelcontextprotocol.io/spec"));        // the source
        assertTrue(user.contains("MCP became stateless in the latest revision.")); // the evidence
        assertTrue(user.contains("client.call(\"tools/list\");"));                  // code is evidence too
    }
}
