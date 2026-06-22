package se.deversity.skill3.llm;

import se.deversity.skill3.model.ContextBundle;
import se.deversity.vibetags.annotations.AICore;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw LLM draft into a spec-compliant {@code SKILL.md}. The generator —
 * not the model — guarantees the frontmatter rules: sanitized name, a bounded
 * tag-free description, and deterministic metadata.
 */
@AICore(sensitivity = "High",
        note = "Deterministically guarantees SKILL.md spec compliance; model output is never trusted. "
                + "Changes risk emitting invalid frontmatter — keep the parsing and frontmatter "
                + "synthesis covered by SkillMdPostProcessorTest.")
public final class SkillMdPostProcessor {

    private static final int DESC_MAX = 1024;
    private static final int DESC_MIN = 12;
    private static final Pattern NAME = Pattern.compile("(?m)^name:\\s*(.+?)\\s*$");
    private static final Pattern DESCRIPTION = Pattern.compile("(?m)^description:\\s*(.+?)\\s*$");
    /** A leaked scaffolding marker line, e.g. {@code === BEGIN OVERVIEW ===}. */
    private static final Pattern MARKER = Pattern.compile("^={2,}.*={2,}$");
    /** A bare section label, e.g. {@code Overview:}. */
    private static final Pattern LABEL = Pattern.compile("^[A-Za-z][A-Za-z0-9 /-]{0,24}:\\s*$");
    /** A leaked frontmatter key line. */
    private static final Pattern FRONTMATTER_KEY = Pattern.compile(
            "^(name|description|metadata|version|learned-date|target-model|cutoff)\\s*:.*",
            Pattern.CASE_INSENSITIVE);

    private SkillMdPostProcessor() {
    }

    public static String render(String raw, ContextBundle bundle, LocalDate learnedDate) {
        String content = unwrapFencedDocument(stripCodeFence(raw == null ? "" : raw.strip()));
        String frontmatter = "";
        String body = content;

        if (content.startsWith("---")) {
            int end = content.indexOf("\n---", 3);
            if (end >= 0) {
                frontmatter = content.substring(3, end).strip();
                body = content.substring(content.indexOf('\n', end + 1) + 1).strip();
            }
        }

        body = cleanBody(body);

        String rawName = firstGroup(NAME, frontmatter);
        String name = NameSanitizer.sanitize(rawName != null ? rawName : bundle.skillName());

        // Don't trust the model's description: reject degenerate values and
        // derive from the body's first real prose instead.
        String rawDesc = unquote(firstGroup(DESCRIPTION, frontmatter));
        String description = sanitizeDescription(
                isUsableDescription(rawDesc, name) ? rawDesc : deriveDescription(body, bundle));

        if (body.isBlank()) {
            body = "# " + bundle.skillName() + "\n\n## Overview\n\n" + description;
        }

        body = withAttribution(body);

        return """
                ---
                name: %s
                description: "%s"
                metadata:
                  version: 1.0.0
                  learned-date: %s
                  target-model: %s
                  cutoff: %s
                ---
                %s
                """.formatted(
                name,
                yamlEscape(description),
                learnedDate,
                bundle.targetModel(),
                bundle.cutoff().iso(),
                body);
    }

    /** Provenance footer the generator stamps onto every skill. */
    static final String ATTRIBUTION = "_Created with [skill3](https://github.com/PIsberg/skill3)._";

    /**
     * Appends the provenance footer, removing any prior copy first. {@code render()} is re-run by
     * the self-correction loop on its own output, so this must be idempotent — exactly one footer,
     * no matter how many times a draft is revised.
     */
    private static String withAttribution(String body) {
        String b = body;
        int marker = b.lastIndexOf("_Created with [skill3]");
        if (marker >= 0) {
            b = b.substring(0, marker).stripTrailing();
            if (b.endsWith("---")) {
                b = b.substring(0, b.length() - 3).stripTrailing();
            }
        }
        return b + "\n\n---\n\n" + ATTRIBUTION;
    }

    static String sanitizeDescription(String raw) {
        String s = raw == null ? "" : raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").strip();
        if (s.isEmpty()) {
            s = "Reference skill.";
        }
        return s.length() > DESC_MAX ? s.substring(0, DESC_MAX).strip() : s;
    }

    /** A model-supplied description is trusted only if it isn't degenerate. */
    static boolean isUsableDescription(String raw, String name) {
        if (raw == null) {
            return false;
        }
        String d = raw.strip();
        if (d.length() < DESC_MIN) {
            return false; // too short
        }
        if (d.toLowerCase(Locale.ROOT).contains("name:")) {
            return false; // leaked frontmatter key
        }
        return !d.equalsIgnoreCase(name) && !d.equalsIgnoreCase("name: " + name);
    }

    /** First real prose line of the body (skips headings, labels, markers, leaked keys). */
    private static String deriveDescription(String body, ContextBundle bundle) {
        for (String line : body.split("\n", -1)) {
            String t = line.strip();
            boolean skippable = t.isEmpty()
                    || t.startsWith("#") || t.startsWith("---") || t.startsWith("===")
                    || t.startsWith("-") || t.startsWith("*") || t.startsWith("```")
                    || t.startsWith("[Source")
                    || FRONTMATTER_KEY.matcher(t).matches()
                    || LABEL.matcher(t).matches();
            if (!skippable) {
                return t;
            }
        }
        return "Up-to-date guidance for " + bundle.skillName()
                + "; use when working with " + bundle.skillName()
                + " to follow patterns current past the " + bundle.cutoff().iso()
                + " knowledge cutoff.";
    }

    /**
     * Removes assembly artifacts a weak model may emit: a leaked secondary
     * frontmatter block at the start, scaffolding markers ({@code === … ===}),
     * and echoed source-metadata lines ({@code Authority: … | Post-Cutoff: …}).
     */
    static String cleanBody(String body) {
        String b = stripLeakedFrontmatter(body == null ? "" : body.strip());

        StringBuilder out = new StringBuilder();
        for (String line : b.split("\n", -1)) {
            String t = line.strip();
            if (MARKER.matcher(t).matches()) {
                continue; // === BEGIN X ===
            }
            if (t.startsWith("Authority:")) {
                continue; // Authority: .. | Post-Cutoff: .. | Published: ..
            }
            out.append(line).append('\n');
        }

        return out.toString().replaceAll("\\n{3,}", "\n\n").strip();
    }

    /**
     * Strips a leaked secondary frontmatter block at the start of the body. Two shapes occur:
     * <ul>
     *   <li>the block keeps its opening {@code ---} (the model emitted {@code ---}…{@code ---}…
     *       {@code ---} with four delimiters), or</li>
     *   <li>the opening {@code ---} was already consumed upstream as the real frontmatter's closing
     *       delimiter (the model shared a delimiter: {@code ---}…{@code ---}…{@code ---} with three),
     *       so the body begins directly with frontmatter key lines.</li>
     * </ul>
     * The second shape is only stripped when the key run is terminated by a {@code ---} line, so
     * ordinary prose that happens to begin with a {@code key:} line is never mistaken for frontmatter.
     */
    private static String stripLeakedFrontmatter(String b) {
        String[] lines = b.split("\n", -1);

        if (lines[0].strip().equals("---")) {
            int i = 1;
            boolean scanning = true;
            while (scanning && i < lines.length) {
                String t = lines[i].strip();
                if (t.equals("---")) {
                    i++;                 // consume the closing delimiter
                    scanning = false;
                } else if (t.isEmpty() || FRONTMATTER_KEY.matcher(t).matches()) {
                    i++;
                } else {
                    scanning = false;    // real content begins
                }
            }
            return String.join("\n", Arrays.copyOfRange(lines, i, lines.length)).strip();
        }

        // Shared-delimiter / fenced case: the opening --- was consumed upstream, so the body begins
        // directly with frontmatter key lines. Measure the leading run of key/blank lines...
        int j = 0;
        int keyCount = 0;
        while (j < lines.length) {
            String t = lines[j].strip();
            if (t.isEmpty()) {
                j++;
            } else if (FRONTMATTER_KEY.matcher(t).matches()) {
                keyCount++;
                j++;
            } else {
                break;
            }
        }
        if (keyCount == 0) {
            return b; // no leaked frontmatter
        }
        // ...then strip it. Consume a trailing delimiter (--- or a stray ``` fence) if the run is
        // closed by one. A lone key-looking line with no delimiter is treated as real prose, not a
        // leak (>= 2 consecutive key lines is the tell-tale of a leaked frontmatter block).
        int from = j;
        if (j < lines.length) {
            String t = lines[j].strip();
            if (t.equals("---") || t.startsWith("```")) {
                from = j + 1; // consume the terminating delimiter/fence
            } else if (keyCount < 2) {
                return b;
            }
        }
        return String.join("\n", Arrays.copyOfRange(lines, from, lines.length)).strip();
    }

    /** A whole SKILL.md the model wrapped in a ```-fence after a chatty preamble. */
    private static final Pattern FENCED_DOCUMENT = Pattern.compile(
            "(?s)```[a-zA-Z0-9]*\\s*\\n(---\\s*\\n.*)\\n```");

    /**
     * Recovers a SKILL.md the model emitted inside a code fence preceded by commentary
     * (e.g. a self-correction reply: "These are false positives, but here's the fix: ```...```").
     * Only triggers when the content does not already start with frontmatter, so a normal
     * document whose body contains code fences is never disturbed.
     */
    private static String unwrapFencedDocument(String content) {
        if (content.startsWith("---")) {
            return content;
        }
        Matcher m = FENCED_DOCUMENT.matcher(content);
        return m.find() ? m.group(1).strip() : content;
    }

    private static String stripCodeFence(String raw) {
        String s = raw;
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) {
                s = s.substring(nl + 1);
            }
            if (s.stripTrailing().endsWith("```")) {
                s = s.stripTrailing();
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.strip();
    }

    private static String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String yamlEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }
}
