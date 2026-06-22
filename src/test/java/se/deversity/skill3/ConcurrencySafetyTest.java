package se.deversity.skill3;

import se.deversity.asynctest.AsyncTest;
import se.deversity.asynctest.DetectorType;
import se.deversity.skill3.llm.NameSanitizer;
import se.deversity.skill3.llm.SkillMdPostProcessor;
import se.deversity.skill3.model.ContextBundle;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;
import se.deversity.skill3.pipeline.AuthorityScorer;
import se.deversity.skill3.pipeline.CutoffResolver;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.PageFetcher;
import se.deversity.skill3.pipeline.RetrievalService;
import se.deversity.skill3.pipeline.SearchClient;
import se.deversity.skill3.skillspector.SkillSpectorReport;
import se.deversity.skill3.skillspector.SkillSpectorRunner;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency stress tests via async-test-lib. The Skill3 pipeline runs sequentially today,
 * but several components hold shared state that <em>would</em> be hit concurrently the moment
 * discovery/retrieval is parallelized: static compiled {@code Pattern}s, a static Jackson
 * {@code ObjectMapper}, a static cutoff lookup table, and shared immutable host sets. These
 * tests hammer each from many threads at once to prove (or disprove) thread-safety now, so a
 * future parallel retrieval stage can rely on it.
 *
 * <p>{@link DetectorType#UNCOMMITTED_CHANGES} is excluded everywhere: it is an environmental
 * check (it flags a dirty Git working tree at test end), not a property of the code under test,
 * and this project is developed with an intentionally dirty tree.
 */
class ConcurrencySafetyTest {

    private static final ContextBundle BUNDLE = new ContextBundle(
            "mcp", "claude-opus-4-8", new Cutoff(YearMonth.of(2026, 1), "test"), List.of());

    /** Shared across all worker threads on purpose: score() must be safe for concurrent reads. */
    private final AuthorityScorer authorityScorer =
            new AuthorityScorer(Set.of("modelcontextprotocol.io", "anthropic.com"));

    /** Shared across all worker threads on purpose: resolve() reads the static cutoff table. */
    private final CutoffResolver cutoffResolver = new CutoffResolver();

    @AsyncTest(threads = 8, invocations = 100, excludes = DetectorType.UNCOMMITTED_CHANGES)
    void nameSanitizerIsStatelessUnderConcurrency() {
        assertEquals("learned-skill", NameSanitizer.sanitize("Claude"));
        assertEquals("model-context-protocol", NameSanitizer.sanitize("Model Context Protocol!"));
    }

    @AsyncTest(threads = 8, invocations = 100, excludes = DetectorType.UNCOMMITTED_CHANGES)
    void skillMdRenderUsesStaticPatternsThreadSafely() {
        String raw = "---\nname: mcp\ndescription: A real one-sentence description of the skill.\n---\n"
                + "## Overview\n\nMCP connects agents to external tools over one integration.";
        String out = SkillMdPostProcessor.render(raw, BUNDLE, LocalDate.of(2026, 6, 22));
        assertTrue(out.contains("name: mcp"));
        assertTrue(out.contains("## Overview"));
    }

    @AsyncTest(threads = 8, invocations = 100, excludes = DetectorType.UNCOMMITTED_CHANGES)
    void skillSpectorReportSharesObjectMapperSafely() {
        String json = "{\"issues\":[{\"category\":\"X\",\"severity\":\"LOW\","
                + "\"message\":\"m\",\"file\":\"f\",\"line\":1}]}";
        SkillSpectorReport report = SkillSpectorReport.parse(json);
        assertEquals(1, report.findings().size());
    }

    @AsyncTest(threads = 8, invocations = 100, excludes = DetectorType.UNCOMMITTED_CHANGES)
    void cutoffResolverReadsStaticTableConcurrently() {
        Cutoff resolved = cutoffResolver.resolve("claude-opus-4-8", null);
        assertEquals("2026-01", resolved.iso());
    }

    @AsyncTest(threads = 8, invocations = 100, excludes = DetectorType.UNCOMMITTED_CHANGES)
    void authorityScorerScoresConcurrently() {
        assertEquals(1.0, authorityScorer.score("https://modelcontextprotocol.io/spec"));
        assertEquals(0.2, authorityScorer.score("https://medium.com/@someone/post"));
        assertEquals(0.7, authorityScorer.score("https://github.com/org/repo"));
    }

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /**
     * The real concurrent path: {@link SkillSpectorRunner#scan} spawns a process plus a daemon
     * stderr-drain thread. Driven here with a harmless {@code echo} so the stress runner can
     * watch process I/O, thread, and resource handling across many concurrent invocations.
     */
    @AsyncTest(threads = 4, invocations = 10, timeoutMs = 10000,
            excludes = DetectorType.UNCOMMITTED_CHANGES)
    void skillSpectorRunnerManagesProcessResourcesCleanly() throws Exception {
        String exe = WINDOWS ? "cmd" : "sh";
        List<String> args = WINDOWS ? List.of("/c", "echo", "{}") : List.of("-c", "echo {}");
        SkillSpectorReport report = new SkillSpectorRunner(exe, args).scan(Path.of("."));
        assertNotNull(report);
    }

    /**
     * The parallel fan-out itself: {@link RetrievalService#retrieve} fetches every URL on its own
     * virtual thread. Driven here by many concurrent callers (each spinning up its own executor) to
     * prove the fan-out returns every source, in input order, with no loss/duplication/corruption.
     */
    @AsyncTest(threads = 6, invocations = 50, timeoutMs = 10000,
            excludes = DetectorType.UNCOMMITTED_CHANGES)
    void parallelRetrievalIsConsistentUnderConcurrency() throws Exception {
        SearchClient search = (q, n) -> List.of(
                "https://a.example/doc", "https://b.example/doc", "https://github.com/org/repo");
        PageFetcher fetcher = url -> "<html><head><title>Doc</title></head><body>"
                + "<pre>code for " + url + "</pre>"
                + "<p>A sufficiently long paragraph of documentation about " + url + ".</p>"
                + "</body></html>";
        RetrievalService service = new RetrievalService(
                search, fetcher, new DateExtractor(), new AuthorityScorer(Set.of()));

        List<Source> sources = service.retrieve("topic", 5);

        assertEquals(3, sources.size());
        assertEquals("https://a.example/doc", sources.get(0).url);       // input order preserved
        assertEquals("https://github.com/org/repo", sources.get(2).url);
    }

    @AsyncTest(threads = 8, invocations = 100, excludes = DetectorType.UNCOMMITTED_CHANGES)
    void contextBundleIsSafelyPublishedAndImmutable() {
        Source s = new Source("https://modelcontextprotocol.io");
        ContextBundle bundle = new ContextBundle(
                "mcp", "claude-opus-4-8", new Cutoff(YearMonth.of(2026, 1), "test"), List.of(s));
        assertEquals(1, bundle.sources().size());
        assertEquals("mcp", bundle.skillName());
    }
}
