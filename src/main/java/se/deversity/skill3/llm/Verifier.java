package se.deversity.skill3.llm;

import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Source;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Accuracy gate: re-grounds a synthesized {@code SKILL.md} against the very sources it
 * was built from. The rest of the pipeline checks <em>format</em> (the post-processor)
 * and <em>safety</em> (SkillSpector) but never <em>truth</em>; this is the missing step.
 * One model call removes claims the sources do not support and demotes future-dated
 * releases from "shipped" to "announced". The result still goes through the deterministic
 * post-processor, so format guarantees are preserved.
 */
public class Verifier {

    private static final int MAX_SOURCES = 20;
    private static final int MAX_EXCERPTS = 10;

    private static final String SYSTEM = """
            You are a fact-checker for an Agent Skills SKILL.md. You are given a DRAFT
            and the SOURCES it must be built from, plus today's date. Correct the draft
            so every factual claim is supported by the SOURCES:
            - Remove or soften any claim, number, date, version, or identifier not
              supported by the SOURCES. Do NOT add new facts or new source URLs.
            - Anything dated AFTER today is at most ANNOUNCED or PLANNED — never present a
              future-dated release or event as already shipped; rephrase as
              "planned/expected for <date>" or drop it.
            - Keep the existing structure and these strict rules: exactly ONE --- YAML
              frontmatter block (name, description) then Markdown; never a second ---
              block; list each source in Sources as `- <url>`.
            Output ONLY the corrected SKILL.md beginning with `---` — no preamble,
            no commentary, and no surrounding code fences.
            """;

    private final ChatModel model;

    public Verifier(ChatModel model) {
        this.model = model;
    }

    /** {@return a corrected draft (pre-post-processing) grounded in {@code bundle}'s sources} */
    public String verify(String draftSkillMd, ContextBundle bundle, LocalDate today) throws IOException {
        String user = "Today: " + today + "\n\n=== DRAFT SKILL.md ===\n" + draftSkillMd
                + "\n\n=== SOURCES (the only admissible evidence) ===\n" + sourcesBlock(bundle);
        return model.complete(SYSTEM, user);
    }

    private static String sourcesBlock(ContextBundle bundle) {
        StringBuilder sb = new StringBuilder();
        List<Source> sources = bundle.sources();
        for (int i = 0; i < Math.min(MAX_SOURCES, sources.size()); i++) {
            Source s = sources.get(i);
            sb.append("\n[").append(s.url).append("] published=").append(s.published).append('\n');
            for (int j = 0; j < Math.min(MAX_EXCERPTS, s.excerpts.size()); j++) {
                sb.append("- ").append(s.excerpts.get(j)).append('\n');
            }
        }
        return sb.toString();
    }
}
