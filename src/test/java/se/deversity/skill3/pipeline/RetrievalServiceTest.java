package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalServiceTest {

    @Test
    void extractContentDropsSiteChromeBoilerplate() {
        Document doc = Jsoup.parse("""
                <html><body>
                  <nav><a href='/'>Home</a> Navigation links that are quite long indeed here</nav>
                  <aside class='sidebar'>Sidebar promo text that is also sufficiently long to pass</aside>
                  <main>
                    <p>The 2026-03 revision adds the _meta field to every request envelope now.</p>
                  </main>
                  <footer>Copyright notice and a privacy policy link that is long enough too</footer>
                </body></html>""");
        Source s = new Source("https://example.com/doc");
        RetrievalService.extractContent(doc, s);

        assertTrue(s.excerpts.stream().anyMatch(e -> e.contains("_meta field")));
        assertFalse(s.excerpts.stream().anyMatch(e -> e.contains("Navigation links")));
        assertFalse(s.excerpts.stream().anyMatch(e -> e.contains("Sidebar promo")));
        assertFalse(s.excerpts.stream().anyMatch(e -> e.contains("privacy policy")));
    }

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
    void sequentialModePreservesOrderAndRunsOnCallerThread() throws Exception {
        Thread caller = Thread.currentThread();
        java.util.List<Thread> fetchThreads = new java.util.ArrayList<>();
        SearchClient search = (q, n) -> List.of("https://a/1", "https://b/2", "https://c/3");
        PageFetcher fetcher = url -> {
            synchronized (fetchThreads) {
                fetchThreads.add(Thread.currentThread());
            }
            return "<html><head><title>" + url + "</title></head><body>"
                    + "<p>A sufficiently long paragraph of documentation text here.</p></body></html>";
        };
        RetrievalService service = new RetrievalService(
                search, fetcher, new DateExtractor(), new AuthorityScorer(java.util.Set.of()), true);

        List<Source> sources = service.retrieve(List.of("q"), 5);

        // Order in == order out, and nothing fanned out to a worker thread.
        assertEquals(List.of("https://a/1", "https://b/2", "https://c/3"),
                sources.stream().map(s -> s.url).toList());
        assertTrue(fetchThreads.stream().allMatch(t -> t == caller),
                "sequential fetches must run on the caller thread");
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
