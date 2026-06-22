package se.deversity.skill3;

import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.llm.SkillMdPostProcessor;
import se.deversity.skill3.llm.Synthesizer;
import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;
import se.deversity.skill3.pipeline.AuthorityScorer;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.IngestionPipeline;
import se.deversity.skill3.pipeline.PageFetcher;
import se.deversity.skill3.pipeline.RetrievalService;
import se.deversity.skill3.pipeline.SearchClient;
import se.deversity.skill3.skillspector.Finding;
import se.deversity.skill3.skillspector.Reviser;
import se.deversity.skill3.skillspector.SelfCorrectionLoop;
import se.deversity.skill3.skillspector.SkillSpectorReport;
import se.deversity.skill3.skillspector.SkillSpectorRunner;
import se.deversity.skill3.skillspector.SkillSpectorUnavailableException;
import se.deversity.skill3.web.WebPreviewGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * The full relearn orchestration with all collaborators injected, so it can be
 * driven end-to-end in tests with fakes (no live network/LLM/SkillSpector).
 * {@link se.deversity.skill3.cli.LearnCommand} wires the real implementations.
 */
public class LearnPipeline {

    static final String FIX_SYSTEM = """
            You revise an Agent Skills SKILL.md to resolve security findings from a
            scanner while keeping the content accurate and within scope. Return the
            full corrected SKILL.md only.
            """;

    /** Inputs for one run. */
    public record Request(String skillName, String targetModel, Cutoff cutoff,
                          boolean strictCutoff, Path outputDir) {
    }

    /** Outcome of one run. {@code report} is null when vetting was skipped. */
    public record Result(Path skillFile, Path htmlFile, String skillMd,
                         boolean vetted, SkillSpectorReport report) {
        public boolean clean() {
            return report != null && report.clean();
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

    public LearnPipeline(SearchClient search, PageFetcher fetcher, DateExtractor dates,
                         ChatModel model, SkillSpectorRunner spector) {
        this(search, fetcher, dates, model, spector, 10, 2, 3, false);
    }

    public LearnPipeline(SearchClient search, PageFetcher fetcher, DateExtractor dates,
                         ChatModel model, SkillSpectorRunner spector, boolean richContext) {
        this(search, fetcher, dates, model, spector, 10, 2, 3, richContext);
    }

    public LearnPipeline(SearchClient search, PageFetcher fetcher, DateExtractor dates,
                         ChatModel model, SkillSpectorRunner spector,
                         int maxResults, int minAgreement, int maxIterations) {
        this(search, fetcher, dates, model, spector, maxResults, minAgreement, maxIterations, false);
    }

    public LearnPipeline(SearchClient search, PageFetcher fetcher, DateExtractor dates,
                         ChatModel model, SkillSpectorRunner spector,
                         int maxResults, int minAgreement, int maxIterations, boolean richContext) {
        this.search = search;
        this.fetcher = fetcher;
        this.dates = dates;
        this.model = model;
        this.spector = spector;
        this.maxResults = maxResults;
        this.minAgreement = minAgreement;
        this.maxIterations = maxIterations;
        this.richContext = richContext;
    }

    public Result run(Request req) throws IOException {
        Files.createDirectories(req.outputDir());
        Path skillFile = req.outputDir().resolve("SKILL.md");

        RetrievalService retrieval = new RetrievalService(
                search, fetcher, dates, new AuthorityScorer(Set.of()));
        List<Source> sources = retrieval.retrieve(req.skillName(), maxResults);
        if (sources.isEmpty()) {
            throw new IllegalStateException("No usable sources found.");
        }

        List<Source> ranked =
                new IngestionPipeline(req.cutoff(), req.strictCutoff(), minAgreement).ingest(sources);
        if (ranked.isEmpty()) {
            throw new IllegalStateException("All sources filtered out (try without --strict-cutoff).");
        }

        ContextBundle bundle = new ContextBundle(
                req.skillName(), req.targetModel(), req.cutoff(), ranked);
        Synthesizer synthesizer = richContext
                ? new Synthesizer(model, 20, 20, 8)
                : new Synthesizer(model);
        String skillMd = synthesizer.synthesize(bundle);
        Files.writeString(skillFile, skillMd);

        boolean vetted = false;
        SkillSpectorReport report = null;
        if (spector != null) {
            Reviser reviser = (current, rep) -> SkillMdPostProcessor.render(
                    model.complete(FIX_SYSTEM, fixPrompt(current, rep)), bundle,
                    LocalDate.now(ZoneId.systemDefault()));
            try {
                SelfCorrectionLoop.Result res =
                        new SelfCorrectionLoop(spector, reviser, maxIterations)
                                .run(req.outputDir(), skillFile, skillMd);
                skillMd = res.skillMd();
                report = res.finalReport();
                vetted = true;
            } catch (SkillSpectorUnavailableException e) {
                vetted = false; // skipped; skill still emitted
            }
        }

        Path html = req.outputDir().resolve("index.html");
        Files.writeString(html, new WebPreviewGenerator().render(skillMd));

        return new Result(skillFile, html, skillMd, vetted, report);
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
