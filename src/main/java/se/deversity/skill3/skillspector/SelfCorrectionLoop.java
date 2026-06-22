package se.deversity.skill3.skillspector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Hybrid vetting: scan, then for each round apply deterministic sanitization
 * (redact secrets) followed by an LLM revision pass, and rescan — up to
 * {@code maxIterations}. Residual findings are returned, not hidden.
 */
public class SelfCorrectionLoop {

    /** Secret-shaped patterns redacted deterministically before the LLM pass. */
    private static final Pattern[] SECRETS = {
            Pattern.compile("(?i)\\b(api[_-]?key|secret|token|password)\\b(\\s*[:=]\\s*)(\\S+)"),
            Pattern.compile("\\bsk-[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bghp_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
    };

    private static final String REDACTED = "***REDACTED***";

    public record Result(String skillMd, SkillSpectorReport finalReport, boolean clean, int iterations) {
    }

    private final SkillSpectorRunner runner;
    private final Reviser reviser;
    private final int maxIterations;

    public SelfCorrectionLoop(SkillSpectorRunner runner, Reviser reviser, int maxIterations) {
        this.runner = runner;
        this.reviser = reviser;
        this.maxIterations = Math.max(1, maxIterations);
    }

    public Result run(Path skillDir, Path skillFile, String initial)
            throws IOException, SkillSpectorUnavailableException {
        String current = initial;
        SkillSpectorReport report;
        int i = 0;
        for (; i < maxIterations; i++) {
            Files.writeString(skillFile, current);
            report = runner.scan(skillDir);
            if (report.clean()) {
                return new Result(current, report, true, i);
            }
            current = redactSecrets(current);
            current = reviser.revise(current, report);
        }
        Files.writeString(skillFile, current);
        report = runner.scan(skillDir);
        return new Result(current, report, report.clean(), i);
    }

    static String redactSecrets(String content) {
        String out = content;
        out = SECRETS[0].matcher(out).replaceAll("$1$2" + REDACTED);
        for (int i = 1; i < SECRETS.length; i++) {
            out = SECRETS[i].matcher(out).replaceAll(REDACTED);
        }
        return out;
    }
}
