package se.deversity.skill3;

import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.llm.SkillMdPostProcessor;
import se.deversity.skill3.llm.Synthesizer;
import se.deversity.skill3.llm.Verifier;
import se.deversity.skill3.console.ProgressBar;
import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;
import se.deversity.skill3.pipeline.AuthorityScorer;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.IngestionPipeline;
import se.deversity.skill3.pipeline.PageFetcher;
import se.deversity.skill3.pipeline.QueryPlanner;
import se.deversity.skill3.pipeline.RetrievalService;
import se.deversity.skill3.pipeline.SearchClient;
import se.deversity.skill3.skillspector.Finding;
import se.deversity.skill3.skillspector.InputVetter;
import se.deversity.skill3.skillspector.Reviser;
import se.deversity.skill3.skillspector.SelfCorrectionLoop;
import se.deversity.skill3.skillspector.SkillSpectorReport;
import se.deversity.skill3.skillspector.SkillSpectorRunner;
import se.deversity.skill3.skillspector.SkillSpectorUnavailableException;
import se.deversity.skill3.web.WebPreviewGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import se.deversity.skill3.model.RunManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The full relearn orchestration with all collaborators injected, so it can be
 * driven end-to-end in tests with fakes (no live network/LLM/SkillSpector).
 * {@link se.deversity.skill3.cli.LearnCommand} wires the real implementations.
 */
public class LearnPipeline {

    static final String FIX_SYSTEM = """
            You revise an Agent Skills SKILL.md to resolve security findings from a
            scanner while keeping the content accurate and within scope. If a finding
            is a false positive, still return the corrected SKILL.md — do not argue.
            Output ONLY the raw SKILL.md beginning with `---`: no preamble, no
            commentary, and no surrounding code fences.
            """;

    /** Inputs for one run. */
    public record Request(String skillName, String targetModel, Cutoff cutoff,
                          boolean strictCutoff, Path outputDir) {
    }

    /**
     * Outcome of one run. {@code report} (output scan) and {@code inputVet} (input-corpus
     * scan) are null when vetting was skipped.
     */
    public record Result(Path skillFile, Path htmlFile, Path manifestFile, String skillMd,
                         boolean vetted, SkillSpectorReport report, InputVetter.Result inputVet) {
        public boolean clean() {
            return report != null && report.clean();
        }

        /**
         * High-severity findings the run gate treats as blocking, pooled across BOTH the
         * input-corpus scan and the output-skill scan. Empty when vetting was skipped or
         * everything is clean/advisory.
         */
        public List<Finding> blockingFindings() {
            List<Finding> blocking = new ArrayList<>();
            if (report != null) {
                blocking.addAll(report.highSeverityFindings());
            }
            if (inputVet != null && inputVet.report() != null) {
                blocking.addAll(inputVet.report().highSeverityFindings());
            }
            return blocking;
        }
    }

    /** Outcome of discovery (phases 1–2): the queries used and the ranked sources they produced. */
    public record Discovery(List<String> queries, List<Source> ranked) {
        public Discovery {
            queries = List.copyOf(queries);
            ranked = List.copyOf(ranked);
        }
    }

    /** Tunable run settings; defaults preserve the original local-first behaviour. */
    public record Options(int maxResults, int minAgreement, int maxIterations,
                          boolean richContext, Set<String> authoritativeHosts, boolean verify,
                          boolean sequential) {
        public Options {
            authoritativeHosts = Set.copyOf(authoritativeHosts);
        }

        public static Options defaults() {
            return new Options(5, 2, 3, false, Set.of(), false, false);
        }
    }

    private final SearchClient search;
    private final PageFetcher fetcher;
    private final DateExtractor dates;
    private final ChatModel model;
    private final SkillSpectorRunner spector; // nullable -> skip vetting
    private final int maxResults;
    private final int minAgreement;
    private final int maxIterations;
    /** Feed more sources/excerpts to the synthesizer — worthwhile for big-context models. */
    private final boolean richContext;
    /** Hosts treated as authoritative (score 1.0) during discovery ranking. */
    private final Set<String> authoritativeHosts;
    /** Run the accuracy gate (re-ground claims against the sources) after synthesis. */
    private final boolean verify;
    /** Fetch pages one at a time instead of concurrently (gentler on rate-limited backends). */
    private final boolean sequential;

    /** Convenience for the common case: default {@link Options}. */
    public LearnPipeline(SearchClient search, PageFetcher fetcher, DateExtractor dates,
                         ChatModel model, SkillSpectorRunner spector) {
        this(search, fetcher, dates, model, spector, Options.defaults());
    }

    public LearnPipeline(SearchClient search, PageFetcher fetcher, DateExtractor dates,
                         ChatModel model, SkillSpectorRunner spector, Options options) {
        this.search = search;
        this.fetcher = fetcher;
        this.dates = dates;
        this.model = model;
        this.spector = spector;
        this.maxResults = options.maxResults();
        this.minAgreement = options.minAgreement();
        this.maxIterations = options.maxIterations();
        this.richContext = options.richContext();
        this.authoritativeHosts = options.authoritativeHosts();
        this.verify = options.verify();
        this.sequential = options.sequential();
    }

    /**
     * Phases 1–2 only: plan (unless the search client is a curated corpus), retrieve, then rank.
     * Exposed so {@code --dry-run} can inspect discovery without spending model calls on synthesis.
     *
     * @return the queries used and the ranked sources, best-first (ranked is never empty —
     *         throws if discovery yields nothing)
     */
    public Discovery discover(Request req) throws IOException {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        RetrievalService retrieval = new RetrievalService(
                search, fetcher, dates, new AuthorityScorer(authoritativeHosts), sequential);

        final List<String> queries;
        if (search.isCuratedCorpus()) {
            // The corpus is already the curated result set; planning queries would only waste a
            // model call (FileCorpus ignores them). Use the topic as a nominal query.
            System.out.println("Discovery: curated input corpus (skipping query planning).");
            queries = List.of(req.skillName());
        } else {
            // Ask the model what to search for — topic-agnostic, post-cutoff-focused.
            queries = new QueryPlanner(model).plan(req.skillName(), req.cutoff(), today);
            System.out.println("Planned " + queries.size() + " discovery queries:");
            for (String q : queries) {
                System.out.println("  - " + q);
            }
        }

        List<Source> sources = retrieval.retrieve(queries, maxResults);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No usable sources found.");
        }

        // Upper-bounded by today: future-dated sources are dropped, not ranked.
        List<Source> ranked = new IngestionPipeline(req.cutoff(), req.strictCutoff(), minAgreement, today)
                .ingest(sources);
        if (ranked.isEmpty()) {
            throw new IllegalStateException("All sources filtered out (try without --strict-cutoff).");
        }
        return new Discovery(queries, ranked);
    }

    public Result run(Request req) throws IOException {
        Files.createDirectories(req.outputDir());
        Path skillFile = req.outputDir().resolve("SKILL.md");

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Map<String, Long> timings = new LinkedHashMap<>();

        long t0 = System.nanoTime();
        Discovery discovery = discover(req);
        timings.put("discoverMs", elapsedMs(t0));
        List<Source> ranked = discovery.ranked();

        // Phase 3a — vet the untrusted input corpus BEFORE it reaches the synthesizer LLM:
        // redact secret-shaped tokens in place, scan the sources for prompt injection, and
        // quarantine any source with a high-severity finding. The synthesizer below then only
        // ever sees the sanitized, non-quarantined sources.
        List<Source> forSynthesis = ranked;
        InputVetter.Result inputVet = null;
        if (spector != null) {
            System.out.println("Vetting input corpus (prompt-injection / secret leakage) before synthesis...");
            long tInVet = System.nanoTime();
            try {
                // \r-redraw only on an interactive console; piped/CI output gets no bar
                // (frames would land as garbled concatenated lines in the log).
                inputVet = new InputVetter(spector).vet(ranked,
                        new ProgressBar(System.out, System.console() != null));
                reportInputVetting(inputVet);
                if (!inputVet.quarantined().isEmpty()) {
                    forSynthesis = inputVet.kept();
                    if (forSynthesis.isEmpty()) {
                        throw new IllegalStateException("All " + ranked.size() + " source(s) quarantined by "
                                + "input vetting (high-severity findings); nothing safe to synthesize from.");
                    }
                }
            } catch (IOException e) {
                // A vetting I/O hiccup must not sink the run; the source markers still fence the data.
                System.out.println("Input vetting error (continuing without it): " + e.getMessage());
            }
            timings.put("inputVetMs", elapsedMs(tInVet));
        }

        ContextBundle bundle = new ContextBundle(
                req.skillName(), req.targetModel(), req.cutoff(), forSynthesis);
        Synthesizer synthesizer = richContext
                ? new Synthesizer(model, 20, 20, 8)
                : new Synthesizer(model);
        long tSynth = System.nanoTime();
        String skillMd = synthesizer.synthesize(bundle);
        timings.put("synthesizeMs", elapsedMs(tSynth));

        if (verify) {
            System.out.println("Verifying claims against sources...");
            long tVerify = System.nanoTime();
            skillMd = SkillMdPostProcessor.render(
                    new Verifier(model).verify(skillMd, bundle, today), bundle, today);
            timings.put("verifyMs", elapsedMs(tVerify));
        }

        boolean vetted = false;
        SkillSpectorReport report = null;
        if (spector != null) {
            // Vet in a staging directory, not the output dir: an aborted run must never leave
            // an unvetted SKILL.md behind as if it were a finished skill, and the scanner
            // shouldn't see leftovers (index.html, run.json) from a previous run.
            Reviser reviser = (current, rep) -> SkillMdPostProcessor.render(
                    model.complete(FIX_SYSTEM, fixPrompt(current, rep)), bundle,
                    LocalDate.now(ZoneId.systemDefault()));
            long tVet = System.nanoTime();
            Path staging = Files.createTempDirectory("skill3-vet-");
            try {
                SelfCorrectionLoop.Result res =
                        new SelfCorrectionLoop(spector, reviser, maxIterations)
                                .run(staging, staging.resolve("SKILL.md"), skillMd);
                skillMd = res.skillMd();
                report = res.finalReport();
                vetted = true;
            } catch (SkillSpectorUnavailableException e) {
                vetted = false; // skipped; skill still emitted
            } catch (IOException e) {
                // Same best-effort policy as input vetting: a scanner I/O failure (timeout,
                // broken venv) must not sink the run — emit the skill unvetted and say so.
                System.out.println("Output vetting error (skill emitted unvetted): " + e.getMessage());
                vetted = false;
            } finally {
                Files.deleteIfExists(staging.resolve("SKILL.md"));
                Files.deleteIfExists(staging);
            }
            timings.put("vetMs", elapsedMs(tVet));
        }

        // All fallible phases (model calls, vetting) are done — only now touch the output dir,
        // so a failed run leaves it exactly as it was.
        Files.writeString(skillFile, skillMd);
        Path html = req.outputDir().resolve("index.html");
        Files.writeString(html, new WebPreviewGenerator().render(skillMd));

        timings.put("totalMs", elapsedMs(t0));
        Path manifestFile = writeManifest(req, discovery, today, vetted, report, inputVet, timings);

        return new Result(skillFile, html, manifestFile, skillMd, vetted, report, inputVet);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void reportInputVetting(InputVetter.Result iv) {
        if (iv.redactions() > 0) {
            System.out.println("Input vetting: redacted " + iv.redactions()
                    + " secret-shaped value(s) from the sources before synthesis.");
        }
        if (!iv.vetted()) {
            System.out.println("Input vetting: SkillSpector unavailable; sources not scanned "
                    + "(secrets still redacted). Run `setup` to enable it.");
            return;
        }
        if (iv.clean()) {
            System.out.println("Input vetting: clean — no injection findings in the sources.");
            return;
        }
        System.out.println("WARNING: " + iv.report().findings().size()
                + " input finding(s) in the retrieved sources (treated as untrusted data):");
        for (Finding f : iv.report().findings()) {
            System.out.println("  - [" + f.severity() + "] " + f.category() + ": " + f.message());
        }
        if (!iv.quarantined().isEmpty()) {
            System.out.println("WARNING: quarantined " + iv.quarantined().size() + " source(s) with "
                    + "high-severity finding(s) — dropped before synthesis:");
            for (Source s : iv.quarantined()) {
                System.out.println("  - " + s.url);
            }
        }
    }

    /** Writes the {@code run.json} provenance manifest next to the skill and returns its path. */
    private Path writeManifest(Request req, Discovery discovery, LocalDate today,
                               boolean vetted, SkillSpectorReport report,
                               InputVetter.Result inputVet, Map<String, Long> timings)
            throws IOException {
        List<RunManifest.SourceRef> refs = new ArrayList<>();
        for (Source s : discovery.ranked()) {
            refs.add(new RunManifest.SourceRef(
                    s.url, s.published == null ? null : s.published.toString(),
                    s.authority, s.recencyWeight, s.combinedScore, s.postCutoff, s.consensusCount));
        }
        boolean inputVetted = inputVet != null && inputVet.vetted();
        boolean inputClean = inputVet != null && inputVet.clean();
        int inputFindings = inputVet == null || inputVet.report() == null
                ? 0 : inputVet.report().findings().size();
        int inputRedactions = inputVet == null ? 0 : inputVet.redactions();
        int inputQuarantined = inputVet == null ? 0 : inputVet.quarantined().size();

        RunManifest manifest = new RunManifest(
                "skill3", req.skillName(), req.targetModel(), req.cutoff().iso(), today.toString(),
                discovery.queries(), refs.size(), verify, vetted,
                report != null && report.clean(), report == null ? 0 : report.findings().size(),
                inputVetted, inputClean, inputFindings, inputRedactions, inputQuarantined,
                refs, timings);

        Path manifestFile = req.outputDir().resolve("run.json");
        Files.writeString(manifestFile,
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        return manifestFile;
    }

    static String fixPrompt(String current, SkillSpectorReport report) {
        StringBuilder sb = new StringBuilder("FINDINGS:\n");
        for (Finding f : report.findings()) {
            sb.append("- [").append(f.severity()).append("] ")
                    .append(f.category()).append(": ").append(f.message()).append('\n');
        }
        sb.append("\nCURRENT SKILL.md:\n").append(current);
        return sb.toString();
    }
}
