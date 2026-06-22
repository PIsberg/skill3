package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SynthesizerTest {

    private ContextBundle bundle() {
        Source s = new Source("https://modelcontextprotocol.io/spec");
        s.authority = 1.0;
        s.postCutoff = true;
        s.excerpts.add("Every request carries protocol version in _meta.");
        s.codeBlocks.add("{\"jsonrpc\":\"2.0\"}");
        return new ContextBundle("mcp", "claude-opus-4-8",
                new Cutoff(YearMonth.of(2026, 1), "test"), List.of(s));
    }

    @Test
    void userPromptContainsDelimitedSources() {
        String prompt = Synthesizer.buildUserPrompt(bundle());
        assertTrue(prompt.contains("BEGIN SOURCES"));
        assertTrue(prompt.contains("https://modelcontextprotocol.io/spec"));
        assertTrue(prompt.contains("_meta"));
        assertTrue(prompt.contains("jsonrpc"));
    }

    @Test
    void synthesizePostProcessesModelOutput() throws Exception {
        ChatModel fake = (system, user) -> "# MCP\n\nUse the _meta field.";
        String out = new Synthesizer(fake).synthesize(bundle());
        assertTrue(out.startsWith("---"));
        assertTrue(out.contains("name: mcp"));
        assertTrue(out.contains("# MCP"));
    }
}
