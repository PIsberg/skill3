package se.deversity.skill3.skillspector;

import se.deversity.skill3.console.ProgressBar;
import se.deversity.skill3.model.Source;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *       key never reaches the model even when the scanner is unavailable;</li>
 *   <li>runs the same SkillSpector CLI over the assembled corpus to surface prompt-injection
 *       and other findings, which are reported (not silently dropped); and</li>
 *   <li><b>quarantines</b> any source carrying a high-severity finding — it is dropped from the
 *       set handed to the synthesizer, so a poisoned page never shapes the skill. The finding is
 *       still recorded (and still trips the run gate); quarantine is mitigation, not amnesty.</li>
 * </ol>
 *
 * <p>Sources are the pipeline's mutable carriers, so redaction is applied in place: the
 * synthesizer that runs next only ever sees the sanitized, non-quarantined text.
 */
public final class InputVetter {

    /** Per-source scan filenames are {@code source-N.txt}; this maps a finding's file back to N. */
    private static final Pattern SOURCE_FILE = Pattern.compile("source-(\\d+)\\.txt");

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
     * @param kept        sources that survived vetting — the set to synthesize from
     * @param quarantined sources dropped for carrying a high-severity finding
     */
    public record Result(SkillSpectorReport report, int redactions, boolean vetted, boolean clean,
                         List<Source> kept, List<Source> quarantined) {
        public Result {
            kept = List.copyOf(kept);
            quarantined = List.copyOf(quarantined);
        }
    }

    /**
     * Redacts secrets from {@code sources} in place, scans the corpus, and partitions the sources
     * into those kept for synthesis and those quarantined for a high-severity finding.
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
                List<Source> kept = new ArrayList<>();
                List<Source> quarantined = new ArrayList<>();
                partition(sources, report, kept, quarantined);
                progress.done(report.clean()
                        ? "input clean (" + redactions + " redaction(s))"
                        : report.findings().size() + " finding(s), " + quarantined.size()
                                + " quarantined, " + redactions + " redaction(s)");
                return new Result(report, redactions, true, report.clean(), kept, quarantined);
            } catch (SkillSpectorUnavailableException e) {
                // Secrets are already redacted; only the injection scan (and so quarantine) is skipped.
                progress.done("scanner unavailable — sources not scanned (" + redactions + " redaction(s))");
                return new Result(null, redactions, false, false, sources, List.of());
            }
        } finally {
            deleteRecursively(scanDir);
        }
    }

    /** Splits {@code sources} into kept vs quarantined by mapping high-severity findings back to their source. */
    private static void partition(List<Source> sources, SkillSpectorReport report,
                                  List<Source> kept, List<Source> quarantined) {
        Set<Integer> bad = new HashSet<>();
        for (Finding f : report.highSeverityFindings()) {
            int idx = sourceIndex(f.file());
            if (idx >= 0 && idx < sources.size()) {
                bad.add(idx);
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            (bad.contains(i) ? quarantined : kept).add(sources.get(i));
        }
    }

    /** {@return the 0-based source index encoded in a finding's {@code source-N.txt} file, or -1} */
    private static int sourceIndex(String file) {
        if (file == null) {
            return -1;
        }
        Matcher m = SOURCE_FILE.matcher(file);
        return m.find() ? Integer.parseInt(m.group(1)) - 1 : -1;
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
