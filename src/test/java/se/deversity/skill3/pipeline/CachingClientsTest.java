package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingClientsTest {

    private DiskCache cache(Path dir) {
        return new DiskCache(dir, Duration.ofDays(7));
    }

    @Test
    void searchHitsDelegateOnceThenServesFromCache(@TempDir Path dir) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        SearchClient delegate = (q, n) -> {
            calls.incrementAndGet();
            return List.of("https://a", "https://b");
        };
        CachingSearchClient caching = new CachingSearchClient(delegate, cache(dir));

        assertEquals(List.of("https://a", "https://b"), caching.search("mcp", 5));
        assertEquals(List.of("https://a", "https://b"), caching.search("mcp", 5));
        assertEquals(1, calls.get()); // second call served from disk
    }

    @Test
    void searchCachesEmptyResultsAndDistinguishesByCount(@TempDir Path dir) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        SearchClient delegate = (q, n) -> {
            calls.incrementAndGet();
            return List.of();
        };
        CachingSearchClient caching = new CachingSearchClient(delegate, cache(dir));

        assertEquals(List.of(), caching.search("mcp", 5));
        assertEquals(List.of(), caching.search("mcp", 5)); // cached empty
        caching.search("mcp", 9);                          // different count -> different key
        assertEquals(2, calls.get());
    }

    @Test
    void searchDistinguishesByFreshnessQualifier(@TempDir Path dir) throws IOException {
        // Two runs with different cutoffs use different freshness windows; they must not
        // share cache entries even though query and count are identical.
        AtomicInteger calls = new AtomicInteger();
        SearchClient delegate = (q, n) -> {
            calls.incrementAndGet();
            return List.of("https://a");
        };
        DiskCache cache = cache(dir);

        new CachingSearchClient(delegate, cache, "2026-01-01to2026-06-22").search("mcp", 5);
        new CachingSearchClient(delegate, cache, "2024-01-01to2026-06-22").search("mcp", 5);
        assertEquals(2, calls.get()); // different window -> different entry

        new CachingSearchClient(delegate, cache, "2026-01-01to2026-06-22").search("mcp", 5);
        assertEquals(2, calls.get()); // same window -> served from cache
    }

    @Test
    void cachingSearchPropagatesCuratedFlag(@TempDir Path dir) {
        SearchClient curated = new SearchClient() {
            @Override public List<String> search(String q, int n) {
                return List.of();
            }
            @Override public boolean isCuratedCorpus() {
                return true;
            }
        };
        assertTrue(new CachingSearchClient(curated, cache(dir)).isCuratedCorpus());
        assertFalse(new CachingSearchClient((q, n) -> List.of(), cache(dir)).isCuratedCorpus());
    }

    @Test
    void fetchHitsDelegateOnceThenServesFromCache(@TempDir Path dir) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        PageFetcher delegate = url -> {
            calls.incrementAndGet();
            return "<html>body of " + url + "</html>";
        };
        CachingPageFetcher caching = new CachingPageFetcher(delegate, cache(dir));

        assertEquals("<html>body of https://x</html>", caching.fetch("https://x"));
        assertEquals("<html>body of https://x</html>", caching.fetch("https://x"));
        assertEquals(1, calls.get());
        // A different URL is a separate entry.
        caching.fetch("https://y");
        assertEquals(2, calls.get());
    }
}
