package se.deversity.skill3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;
import se.deversity.skill3.pipeline.DateExtractor;
import se.deversity.skill3.pipeline.FileCorpus;
import se.deversity.skill3.pipeline.PageFetcher;
import se.deversity.skill3.pipeline.SearchClient;
import se.deversity.skill3.skillspector.Finding;
import se.deversity.skill3.skillspector.SkillSpectorReport;
import se.deversity.skill3.skillspector.SkillSpectorRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** End-to-end pipeline tests driven entirely with in-process fakes. */
class LearnPipelineE2ETest {

    private static final Cutoff CUTOFF = new Cutoff(YearMonth.of(2026, 1), "test");

    private static String page(String published, String code) {
        return "<html><head><title>Doc</title>"
                + "<meta property='article:published_time' content='" + published + "'></head><body>"
                + "<pre>" + code + "</pre>"
                + "<p>A sufficiently long paragraph of documentation about the topic.</p>"
                + "</body></html>";
    }

    private SearchClient search(String... urls) {
        return (q, n) -> List.of(urls);
    }

    private PageFetcher fetcher(Map<String, String> pages) {
        return url -> pages.getOrDefault(url, "<html></html>");
    }

    private ChatModel model() {
        // Ignores the prompt; returns a minimal draft the post-processor will normalize.
        return (system, user) -> "# MCP\n\nUse the _meta field on every request.";
    }

    private LearnPipeline.Request request(Path dir, boolean strict) {
        return new LearnPipeline.Request("mcp", "claude-opus-4-8", CUTOFF, strict, dir);
    }

    @Test
    void happyPathWritesCompliantSkillAndPreview(@TempDir Path dir) throws Exception {
        Map<String, String> pages = Map.of(
                "https://modelcontextprotocol.io/spec", page("2026-05-01", "shared();"),
                "https://github.com/org/repo", page("2026-04-01", "shared();"));

        SkillSpectorRunner spector = mock(SkillSpectorRunner.class);
        when(spector.scan(any())).thenReturn(new SkillSpectorReport(List.of(), "[]"));

        LearnPipeline pipeline = new LearnPipeline(
                search("https://modelcontextprotocol.io/spec", "https://github.com/org/repo"),
                fetcher(pages), new DateExtractor(), model(), spector);

        LearnPipeline.Result res = pipeline.run(request(dir, false));

        assertTrue(Files.exists(res.skillFile()));
        assertTrue(Files.exists(res.htmlFile()));
        String md = Files.readString(res.skillFile());
        assertTrue(md.startsWith("---"));
        assertTrue(md.contains("name: mcp"));
        assertTrue(md.contains("cutoff: 2026-01"));
        assertTrue(md.contains("target-model: claude-opus-4-8"));
        assertTrue(Files.readString(res.htmlFile()).contains("<!DOCTYPE html>"));
        assertTrue(res.vetted());
        assertTrue(res.clean());

        // Provenance manifest is written and records the sources, queries and timings.
        assertTrue(Files.exists(res.manifestFile()));
        String manifest = Files.readString(res.manifestFile());
        assertTrue(manifest.contains("\"skill\" : \"mcp\""));
        assertTrue(manifest.contains("modelcontextprotocol.io/spec"));
        assertTrue(manifest.contains("\"totalMs\""));
        assertTrue(manifest.contains("\"sourceCount\" : 2"));
    }

    @Test
    void inputFileCorpusDrivesFullPipelineOffline(@TempDir Path dir) throws Exception {
        // A user-curated corpus file replaces Brave: the same FileCorpus is the
        // SearchClient and the PageFetcher, so the run never touches the network.
        Path corpusFile = dir.resolve("corpus.txt");
        Files.writeString(corpusFile, """
                === SOURCE ===
                url: https://modelcontextprotocol.io/spec
                date: 2026-05-01

                The 2026-05 revision documents the new _meta field on every request.

                ```
                shared();
                ```

                === SOURCE ===
                url: https://github.com/org/repo
                date: 2026-04-01

                A sufficiently long paragraph describing the released behaviour and flags.

                ```
                shared();
                ```
                """);
        FileCorpus corpus = FileCorpus.load(corpusFile);

        SkillSpectorRunner spector = mock(SkillSpectorRunner.class);
        when(spector.scan(any())).thenReturn(new SkillSpectorReport(List.of(), "[]"));

        LearnPipeline pipeline = new LearnPipeline(
                corpus, corpus, new DateExtractor(), model(), spector);

        LearnPipeline.Result res = pipeline.run(request(dir, false));

        assertTrue(Files.exists(res.skillFile()));
        String md = Files.readString(res.skillFile());
        assertTrue(md.startsWith("---"));
        assertTrue(md.contains("name: mcp"));
        assertTrue(res.vetted());
        assertTrue(res.clean());
    }

    @Test
    void curatedCorpusDiscoversWithoutCallingTheModel(@TempDir Path dir) throws Exception {
        // A FileCorpus is a curated result set, so discovery must not spend a query-planning call.
        Path corpusFile = dir.resolve("corpus.txt");
        Files.writeString(corpusFile, """
                === SOURCE ===
                url: https://modelcontextprotocol.io/spec
                date: 2026-05-01

                A sufficiently long paragraph describing the 2026-05 revision and its new flags.
                """);
        FileCorpus corpus = FileCorpus.load(corpusFile);
        ChatModel mustNotRun = (system, user) -> {
            throw new IllegalStateException("model must not be called for a curated corpus");
        };

        LearnPipeline pipeline = new LearnPipeline(corpus, corpus, new DateExtractor(), mustNotRun, null);
        List<Source> ranked = pipeline.discover(request(dir, false)).ranked();

        assertEquals(1, ranked.size());
        assertEquals("https://modelcontextprotocol.io/spec", ranked.get(0).url);
    }

    @Test
    void discoverReturnsRankedSourcesForDryRun(@TempDir Path dir) throws Exception {
        Map<String, String> pages = Map.of(
                "https://modelcontextprotocol.io/spec", page("2026-05-01", "shared();"),
                "https://github.com/org/repo", page("2026-04-01", "shared();"));

        LearnPipeline pipeline = new LearnPipeline(
                search("https://modelcontextprotocol.io/spec", "https://github.com/org/repo"),
                fetcher(pages), new DateExtractor(), model(), null);

        LearnPipeline.Discovery discovery = pipeline.discover(request(dir, false));
        List<Source> ranked = discovery.ranked();

        assertEquals(2, ranked.size());
        assertFalse(discovery.queries().isEmpty());
        // Best-first: the post-cutoff, higher-combined-score source leads.
        assertTrue(ranked.get(0).combinedScore >= ranked.get(1).combinedScore);
        assertFalse(Files.exists(dir.resolve("SKILL.md"))); // discovery writes nothing
    }

    @Test
    void selfCorrectionConvergesAfterFinding(@TempDir Path dir) throws Exception {
        Map<String, String> pages = Map.of(
                "https://a.com/doc", page("2026-05-01", "shared();"),
                "https://b.com/doc", page("2026-04-01", "shared();"));

        SkillSpectorReport dirty = new SkillSpectorReport(
                List.of(new Finding("prompt-injection", "HIGH", "m", "SKILL.md", 1)), "raw");
        SkillSpectorReport clean = new SkillSpectorReport(List.of(), "[]");
        SkillSpectorRunner spector = mock(SkillSpectorRunner.class);
        when(spector.scan(any())).thenReturn(dirty, clean);

        LearnPipeline pipeline = new LearnPipeline(
                search("https://a.com/doc", "https://b.com/doc"),
                fetcher(pages), new DateExtractor(), model(), spector);

        LearnPipeline.Result res = pipeline.run(request(dir, false));

        assertTrue(res.vetted());
        assertTrue(res.clean());
    }

    @Test
    void vettingSkippedWhenNoSkillSpector(@TempDir Path dir) throws Exception {
        Map<String, String> pages = Map.of("https://a.com/doc", page("2026-05-01", "x();"));

        LearnPipeline pipeline = new LearnPipeline(
                search("https://a.com/doc"), fetcher(pages), new DateExtractor(), model(), null);

        LearnPipeline.Result res = pipeline.run(request(dir, false));

        assertFalse(res.vetted());
        assertTrue(Files.exists(res.skillFile()));
    }

    @Test
    void verifyPassRunsAndStillProducesSkill(@TempDir Path dir) throws Exception {
        Map<String, String> pages = Map.of("https://a.com/doc", page("2026-05-01", "x();"));

        // verify=true triggers the accuracy gate (a second model call); the fake handles both.
        LearnPipeline pipeline = new LearnPipeline(
                search("https://a.com/doc"), fetcher(pages), new DateExtractor(), model(), null,
                new LearnPipeline.Options(5, 2, 3, false, java.util.Set.of(), true));

        LearnPipeline.Result res = pipeline.run(request(dir, false));

        assertTrue(Files.exists(res.skillFile()));
        assertTrue(Files.readString(res.skillFile()).startsWith("---"));
    }

    @Test
    void emptySearchResultsThrows(@TempDir Path dir) {
        LearnPipeline pipeline = new LearnPipeline(
                search(), fetcher(Map.of()), new DateExtractor(), model(), null);
        assertThrows(IllegalStateException.class, () -> pipeline.run(request(dir, false)));
    }

    @Test
    void strictCutoffFilteringEverythingThrows(@TempDir Path dir) {
        // Only a pre-cutoff source; strict mode drops it -> nothing left to synthesize.
        Map<String, String> pages = Map.of("https://a.com/doc", page("2025-06-01", "x();"));
        LearnPipeline pipeline = new LearnPipeline(
                search("https://a.com/doc"), fetcher(pages), new DateExtractor(), model(), null);
        assertThrows(IllegalStateException.class, () -> pipeline.run(request(dir, true)));
    }
}
