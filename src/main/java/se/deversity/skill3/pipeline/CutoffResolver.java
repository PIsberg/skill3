package se.deversity.skill3.pipeline;

import se.deversity.skill3.model.Cutoff;
import se.deversity.vibetags.annotations.AIContext;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a knowledge-cutoff month from a target-model id or an explicit
 * override. The built-in table is intentionally small and expected to go stale;
 * entries should be sourced from model documentation and are always overridable
 * with {@code --cutoff-time}.
 */
@AIContext(
        focus = "Keep the cutoff TABLE small and sourced from published model documentation",
        avoids = "hardcoding per-skill logic; the cutoff is always overridable via --cutoff-time")
public class CutoffResolver {

    // Overridable, may go stale. claude-opus-4-8 is asserted (Jan 2026);
    // the others are illustrative — VERIFY before relying on them.
    private static final Map<String, YearMonth> TABLE = new LinkedHashMap<>();

    static {
        TABLE.put("claude-opus-4-8", YearMonth.of(2026, 1));
        TABLE.put("claude-opus-4-7", YearMonth.of(2025, 9));   // VERIFY
        TABLE.put("claude-sonnet-4-6", YearMonth.of(2025, 7)); // VERIFY
    }

    /**
     * Resolves the knowledge cutoff from a target-model id or an explicit override.
     *
     * @param targetModel    model id; ignored if {@code cutoffOverride} is set
     * @param cutoffOverride explicit {@code yyyy-MM} cutoff, or null/blank
     * @throws IllegalArgumentException on an unknown model with no override, or a
     *                                  malformed override
     */
    public Cutoff resolve(String targetModel, String cutoffOverride) {
        if (cutoffOverride != null && !cutoffOverride.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(cutoffOverride.trim());
                return new Cutoff(ym, "override " + ym);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "Invalid --cutoff-time '" + cutoffOverride + "'; expected yyyy-MM (e.g. 2026-01).", e);
            }
        }
        YearMonth ym = TABLE.get(targetModel);
        if (ym == null) {
            throw new IllegalArgumentException(
                    "Unknown --target-model '" + targetModel + "'. "
                            + "Pass --cutoff-time yyyy-MM to set the knowledge cutoff explicitly.");
        }
        return new Cutoff(ym, targetModel + " (" + ym + ")");
    }
}
