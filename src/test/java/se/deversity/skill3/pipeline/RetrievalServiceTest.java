package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RetrievalServiceTest {

    @Test
    void retrievesAndEnrichesSources() throws Exception {
        SearchClient search = (q, n) -> List.of("https://github.com/org/repo");
        PageFetcher fetcher = url -> "<html><head><title>Doc</title>"
                + "<meta property='article:published_time' content='2026-05-21'></head><body>"
                + "<pre>let x = 1;</pre>"
                + "<p>This is a sufficiently long paragraph of documentation text.</p>"
                + "</body></html>";

        RetrievalService service = new RetrievalService(
                search, fetcher, new DateExtractor(), new AuthorityScorer(java.util.Set.of()));

        List<Source> sources = service.retrieve("mcp", 5);

        assertEquals(1, sources.size());
        Source s = sources.get(0);
        assertEquals("https://github.com/org/repo", s.url);
        assertEquals(0.7, s.authority);
        assertEquals(LocalDate.of(2026, 5, 21), s.published);
        assertFalse(s.codeBlocks.isEmpty());
        assertFalse(s.excerpts.isEmpty());
    }

    @Test
    void multiQueryMergesAndDedupesUrls() throws Exception {
        SearchClient search = (q, n) -> q.contains("iran")
                ? List.of("https://a/iran", "https://shared")
                : List.of("https://b/tax", "https://shared");
        PageFetcher fetcher = url -> "<html><head><title>D</title></head><body>"
                + "<p>A sufficiently long paragraph of documentation text here.</p></body></html>";
        RetrievalService service = new RetrievalService(
                search, fetcher, new DateExtractor(), new AuthorityScorer(java.util.Set.of()));

        List<Source> sources = service.retrieve(List.of("trump iran", "trump tax"), 5);

        // "https://shared" appears in both queries but is fetched once -> 3 unique sources.
        assertEquals(3, sources.size());
    }

    @Test
    void skipsPagesThatFailToFetch() throws Exception {
        SearchClient search = (q, n) -> List.of("https://broken");
        PageFetcher fetcher = url -> {
            throw new java.io.IOException("boom");
        };
        RetrievalService service = new RetrievalService(
                search, fetcher, new DateExtractor(), new AuthorityScorer(java.util.Set.of()));
        assertEquals(0, service.retrieve("mcp", 5).size());
    }
}
