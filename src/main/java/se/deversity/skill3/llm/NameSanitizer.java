package se.deversity.skill3.llm;

import se.deversity.vibetags.annotations.AIPure;
import se.deversity.vibetags.annotations.AISecure;

import java.util.Locale;

/**
 * Guarantees an Agent Skills {@code name}: lowercase {@code [a-z0-9-]}, &le; 64
 * chars, with the reserved words {@code anthropic} and {@code claude} stripped.
 * Never trusts the LLM to obey these rules.
 */
@AISecure(aspect = "output sanitization: reserved-word stripping must never be weakened")
public final class NameSanitizer {

    private static final int MAX = 64;
    private static final String FALLBACK = "learned-skill";

    private NameSanitizer() {
    }

    @AIPure
    public static String sanitize(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "-");      // non-alnum -> hyphen
        s = s.replaceAll("anthropic|claude", ""); // reserved words (substring rule)
        s = s.replaceAll("-+", "-");              // collapse
        s = s.replaceAll("^-+|-+$", "");          // trim hyphens
        if (s.length() > MAX) {
            s = s.substring(0, MAX).replaceAll("-+$", "");
        }
        return s.isBlank() ? FALLBACK : s;
    }
}
