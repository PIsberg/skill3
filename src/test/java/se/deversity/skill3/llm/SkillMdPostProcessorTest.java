package se.deversity.skill3.llm;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Cutoff;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMdPostProcessorTest {

    private final ContextBundle bundle = new ContextBundle(
            "mcp", "claude-opus-4-8", new Cutoff(YearMonth.of(2026, 1), "test"), List.of());

    @Test
    void sanitizesReservedNameAndStripsTagsInDescription() {
        String raw = "---\nname: claude\ndescription: A <b>bold</b> tool\n---\n# Body\nuse it";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        assertTrue(out.contains("name: learned-skill"));
        assertFalse(out.contains("name: claude"));
        assertTrue(out.contains("A bold tool"));
        assertFalse(out.contains("<b>"));
        assertTrue(out.contains("cutoff: 2026-01"));
        assertTrue(out.contains("target-model: claude-opus-4-8"));
        assertTrue(out.contains("learned-date: 2026-06-22"));
    }

    @Test
    void synthesizesFrontmatterWhenMissing() {
        String raw = "# MCP\n\nUse the _meta field on every request.";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        assertTrue(out.startsWith("---"));
        assertTrue(out.contains("name: mcp"));
        assertTrue(out.contains("Use the _meta field on every request."));
        assertTrue(out.contains("# MCP"));
    }

    @Test
    void preservesScopedPackageInstructions() {
        ContextBundle packageBundle = new ContextBundle(
                "@xquik/tweetclaw", "claude-opus-4-8", new Cutoff(YearMonth.of(2026, 1), "test"),
                List.of());
        String raw = """
                ---
                name: @xquik/tweetclaw
                description: A Skill for using TweetClaw from OpenClaw.
                ---
                # TweetClaw

                Use TweetClaw when an OpenClaw workflow needs approval-gated X automation.

                ```bash
                npm install @xquik/tweetclaw
                ```
                """;
        String out = SkillMdPostProcessor.render(raw, packageBundle, LocalDate.of(2026, 6, 22));

        assertTrue(out.contains("name: xquik-tweetclaw"));
        assertTrue(out.contains("# TweetClaw"));
        assertTrue(out.contains("npm install @xquik/tweetclaw"));
        assertFalse(out.contains("name: @xquik/tweetclaw"));
    }

    @Test
    void stripsWrappingCodeFence() {
        String raw = "```markdown\n---\nname: x\ndescription: hi\n---\n# H\nbody\n```";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));
        assertTrue(out.contains("name: x"));
        assertFalse(out.contains("```"));
    }

    @Test
    void truncatesOverlongDescription() {
        String raw = "---\nname: t\ndescription: " + "x".repeat(1500) + "\n---\n# B\nbody";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));
        assertFalse(out.contains("x".repeat(1100)));
    }

    @Test
    void escapesQuotesInDescription() {
        String raw = "---\nname: t\ndescription: He said \"hi\"\n---\n# B\nbody";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));
        assertTrue(out.contains("\\\"hi\\\""));
    }

    // --- Fix 1: don't trust degenerate model descriptions ---

    @Test
    void rejectsDescriptionEqualToNameAndDerivesFromProse() {
        String raw = "---\nname: mcp\ndescription: mcp\n---\n## Overview\n\n"
                + "The protocol carries capabilities in the _meta field of every request.";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        assertFalse(out.contains("description: \"mcp\""));
        assertTrue(out.contains("The protocol carries capabilities in the _meta field"));
    }

    @Test
    void rejectsDescriptionThatLeaksNameKey() {
        String raw = "---\nname: mcp\ndescription: name: mcp\n---\n## Overview\n\n"
                + "A real sentence describing what the skill is for and when to use it.";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        assertFalse(out.contains("description: \"name: mcp\""));
        assertTrue(out.contains("A real sentence describing what the skill is for"));
    }

    @Test
    void isUsableDescriptionRules() {
        assertFalse(SkillMdPostProcessor.isUsableDescription(null, "mcp"));
        assertFalse(SkillMdPostProcessor.isUsableDescription("mcp", "mcp"));       // equals name
        assertFalse(SkillMdPostProcessor.isUsableDescription("name: mcp", "mcp")); // leaked key
        assertFalse(SkillMdPostProcessor.isUsableDescription("too short", "mcp")); // < 12 chars
        assertTrue(SkillMdPostProcessor.isUsableDescription(
                "A clear one-sentence description of the skill.", "mcp"));
    }

    // --- Fix 3: strip leaked secondary frontmatter and scaffolding from the body ---

    @Test
    void cleansLeakedFrontmatterAndScaffolding() {
        String raw = """
                ---
                name: json-rpc
                description: name: json-rpc
                ---
                ---\s
                name: json-rpc
                description: A technical skill for JSON-RPC.

                === BEGIN OVERVIEW (authored) ===
                Overview:
                The JSON-RPC is a lightweight remote procedure call protocol over JSON.

                [Source 1] https://example.com
                Authority: 0.50 | Post-Cutoff: false | Published: 2005-07-11
                === END OVERVIEW ===
                """;
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        // description derived from real prose, not the leaked key
        assertFalse(out.contains("description: \"name: json-rpc\""));
        assertTrue(out.contains("description: \"The JSON-RPC is a lightweight remote procedure call"));
        // scaffolding and leaked secondary frontmatter removed
        assertFalse(out.contains("=== "));
        assertFalse(out.contains("Authority:"));
        assertFalse(out.contains("A technical skill for JSON-RPC."));
    }

    @Test
    void stripsSharedDelimiterSecondaryFrontmatter() {
        // The model emitted two frontmatter blocks sharing a single --- delimiter
        // (three delimiters total), so the body begins with key lines, not a ---.
        String raw = """
                ---
                name: mcp
                description: The Model Context Protocol is a standard.
                metadata:
                  version: 1.0.0
                ---
                name: Agent Skills MCP
                description: The Model Context Protocol enables tools.
                ---

                ## Overview

                MCP lets an agent reach external tools over a single integration.
                """;
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        // The leaked second block must not survive into the body.
        assertFalse(out.contains("name: Agent Skills MCP"));
        assertFalse(out.contains("Agent Skills MCP"));
        assertTrue(out.contains("## Overview"));
        assertTrue(out.contains("MCP lets an agent reach external tools"));
        // Exactly one frontmatter block: the generated one closes once before the body.
        assertEquals("---", out.split("\n", 2)[0]);
    }

    @Test
    void stripsSecondaryFrontmatterTerminatedByCodeFence() {
        // Observed in the wild (the 'trump' run): a leaked second frontmatter block closed by a
        // stray ``` fence instead of a --- delimiter.
        String raw = """
                ---
                name: trump
                description: A real one-sentence summary of the topic for the skill.
                ---
                name: Trump Documents Skill
                description: Summarizes recent developments.
                ```

                ## Overview

                Recent reporting covers classified-document disclosures.
                """;
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));

        assertFalse(out.contains("name: Trump Documents Skill"));
        assertFalse(out.contains("Trump Documents Skill"));
        assertFalse(out.contains("```"));
        assertTrue(out.contains("## Overview"));
        assertTrue(out.contains("Recent reporting covers classified-document disclosures."));
    }

    @Test
    void keepsBodyLineThatMerelyLooksLikeAKeyWithoutDelimiter() {
        // A prose body starting with "version: ..." and NO closing --- must be preserved.
        String raw = "## Notes\n\nversion: pin it in your manifest before shipping.";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));
        assertTrue(out.contains("version: pin it in your manifest before shipping."));
    }

    @Test
    void stampsAttributionFooter() {
        String raw = "# MCP\n\nUse the _meta field on every request.";
        String out = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));
        assertTrue(out.contains(SkillMdPostProcessor.ATTRIBUTION));
        assertTrue(out.contains("https://github.com/PIsberg/skill3"));
    }

    @Test
    void attributionIsNotDuplicatedWhenReRendered() {
        String raw = "# MCP\n\nUse the _meta field on every request.";
        String once = SkillMdPostProcessor.render(raw, bundle, LocalDate.of(2026, 6, 22));
        // The self-correction loop feeds render() its own previous output.
        String twice = SkillMdPostProcessor.render(once, bundle, LocalDate.of(2026, 6, 22));
        int first = twice.indexOf(SkillMdPostProcessor.ATTRIBUTION);
        int last = twice.lastIndexOf(SkillMdPostProcessor.ATTRIBUTION);
        assertEquals(first, last); // appears exactly once
    }

    @Test
    void cleanBodyStripsMarkersAndAuthorityLines() {
        String body = "=== BEGIN X ===\nReal content here.\nAuthority: 0.5 | Post-Cutoff: false\n=== END X ===";
        String cleaned = SkillMdPostProcessor.cleanBody(body);
        assertFalse(cleaned.contains("==="));
        assertFalse(cleaned.contains("Authority:"));
        assertTrue(cleaned.contains("Real content here."));
    }
}
