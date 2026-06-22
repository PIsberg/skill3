package se.deversity.skill3.skillspector;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed SkillSpector output. The real CLI's exact JSON/SARIF schema is confirmed
 * during {@code setup}; this parser is intentionally tolerant — it looks for a
 * findings/results array and pulls best-effort fields, falling back to the raw
 * output so nothing is silently lost.
 */
public class SkillSpectorReport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Finding> findings;
    private final String raw;

    public SkillSpectorReport(List<Finding> findings, String raw) {
        this.findings = List.copyOf(findings);
        this.raw = raw;
    }

    public List<Finding> findings() {
        return findings;
    }

    public String raw() {
        return raw;
    }

    public boolean clean() {
        return findings.isEmpty();
    }

    public static SkillSpectorReport parse(String output) {
        List<Finding> findings = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(output);
            // SkillSpector `--format json` nests findings under "issues"; tolerate
            // other shapes (and SARIF runs[].results) as fallbacks.
            JsonNode array = firstArray(root, "issues", "findings", "results", "vulnerabilities");
            if (array == null) {
                JsonNode sarif = root.path("runs").path(0).path("results");
                if (sarif.isArray()) {
                    array = sarif;
                }
            }
            if (array == null && root.isArray()) {
                array = root;
            }
            if (array != null) {
                for (JsonNode n : array) {
                    JsonNode loc = n.path("location");
                    findings.add(new Finding(
                            text(n, "category", "id", "rule_id", "rule", "type"),
                            text(n, "severity", "level"),
                            text(n, "explanation", "message", "finding", "description", "detail"),
                            firstNonEmpty(text(loc, "file", "path"), text(n, "file", "path")),
                            firstNonNegative(intOf(loc, "start_line", "line"), intOf(n, "line", "lineNumber"))));
                }
            }
        } catch (JacksonException | RuntimeException e) {
            // Not JSON we understand; leave findings empty and keep raw output.
        }
        return new SkillSpectorReport(findings, output);
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static int firstNonNegative(int a, int b) {
        return a >= 0 ? a : b;
    }

    private static JsonNode firstArray(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && n.isArray()) {
                return n;
            }
        }
        return null;
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return "";
    }

    private static int intOf(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && v.isInt()) {
                return v.asInt();
            }
        }
        return -1;
    }
}
