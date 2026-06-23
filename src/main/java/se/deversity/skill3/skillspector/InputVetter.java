package se.deversity.skill3.skillspector;

import se.deversity.skill3.console.ProgressBar;
import se.deversity.skill3.model.Source;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Vets the untrusted retrieved corpus <em>before</em> it reaches the synthesizer LLM.
 *
 * <p>The synthesizer's own prompt already frames sources as untrusted DATA, but a fetched
 * page is still a hostile-input surface: it can carry prompt-injection instructions or leaked
 * secrets that we would otherwise relay verbatim into the model (and, for hosted providers,
 * off the machine). This stage adds an input-side gate that mirrors the existing output-side
 * {@link SelfCorrectionLoop}:
 *
 * <ol>
 *   <li>deterministically redacts secret-shaped tokens from each source in place — so a leaked
 *       key never reaches the model even when the scanner is unavailable; and</li>
 *   <li>runs the same SkillSpector CLI over the assembled corpus to surface prompt-injection
 *       and other findings, which are reported (not silently dropped).</li>
 * </ol>
 *
 * <p>Sources are the pipeline's mutable carriers, so redaction is applied in place: the
 * synthesizer that runs next only ever sees the sanitized text.
 */
public final class InputVetter {

    private final SkillSpectorRunner runner;

    public InputVetter(SkillSpectorRunner runner) {
        this.runner = runner;
    }

    /**
     * Outcome of one input-vetting pass.
     *
     * @param report      the scan report, or {@code null} when the scanner was unavailable
     * @param redactions  how many source fields had a secret-shaped value redacted
     * @param vetted      whether SkillSpector actually scanned the corpus
     * @param clean       whether the scan found nothing (false when unavailable)
     */
    public record Result(SkillSpectorReport report, int redactions, boolean vetted, boolean clean) {
    }

    /**
     * Redacts secrets from {@code sources} in place, then scans the corpus.
     *
     * @param sources  the ranked sources about to be synthesized (mutated in place)
     * @param progress progress sink; use {@link ProgressBar#silent()} for non-interactive runs
     */
    public Result vet(List<Source> sources, ProgressBar progress) throws IOException {
        Path scanDir = Files.createTempDirectory("skill3-input-vet");
        try {
            progress.start(sources.size() + 1, "Vetting input");
            int redactions = 0;
            for (int i = 0; i < sources.size(); i++) {
                Source s = sources.get(i);
                progress.step("source " + (i + 1) + "/" + sources.size() + " " + host(s.url));
                redactions += redactInPlace(s);
                Files.writeString(scanDir.resolve("source-" + (i + 1) + ".txt"), assemble(s));
            }
            progress.step("SkillSpector scanning " + sources.size() + " source(s)");
            try {
                SkillSpectorReport report = runner.scan(scanDir);
                progress.done(report.clean()
                        ? "input clean (" + redactions + " redaction(s))"
                        : report.findings().size() + " finding(s), " + redactions + " redaction(s)");
                return new Result(report, redactions, true, report.clean());
            } catch (SkillSpectorUnavailableException e) {
                // Secrets are already redacted; only the injection scan is skipped.
                progress.done("scanner unavailable — sources not scanned (" + redactions + " redaction(s))");
                return new Result(null, redactions, false, false);
            }
        } finally {
            deleteRecursively(scanDir);
        }
    }

    /** Redacts secret-shaped tokens in the source's text fields, returning the count of changed fields. */
    private static int redactInPlace(Source s) {
        int n = 0;
        String title = SelfCorrectionLoop.redactSecrets(s.title);
        if (!title.equals(s.title)) {
            s.title = title;
            n++;
        }
        for (int i = 0; i < s.excerpts.size(); i++) {
            String redacted = SelfCorrectionLoop.redactSecrets(s.excerpts.get(i));
            if (!redacted.equals(s.excerpts.get(i))) {
                s.excerpts.set(i, redacted);
                n++;
            }
        }
        // codeBlocks may be an immutable list set by an earlier stage; rebuild rather than set().
        List<String> rebuilt = new ArrayList<>(s.codeBlocks.size());
        boolean changed = false;
        for (String code : s.codeBlocks) {
            String redacted = SelfCorrectionLoop.redactSecrets(code);
            rebuilt.add(redacted);
            if (!redacted.equals(code)) {
                changed = true;
                n++;
            }
        }
        if (changed) {
            s.codeBlocks = rebuilt;
        }
        return n;
    }

    /** Flattens a source to the plain text the scanner sees (and the model would have seen). */
    private static String assemble(Source s) {
        StringBuilder sb = new StringBuilder();
        sb.append("url: ").append(s.url).append('\n');
        if (!s.title.isBlank()) {
            sb.append("title: ").append(s.title).append('\n');
        }
        for (String excerpt : s.excerpts) {
            sb.append(excerpt).append('\n');
        }
        for (String code : s.codeBlocks) {
            sb.append(code).append('\n');
        }
        return sb.toString();
    }

    private static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? url : h;
        } catch (RuntimeException e) {
            return url;
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a temp scan dir
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of a temp scan dir
        }
    }
}
