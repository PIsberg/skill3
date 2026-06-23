package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryProviderTest {

    @Test
    void inputFileGivesOneCuratedCorpusForBothSeams(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("corpus.txt");
        Files.writeString(file, """
                === SOURCE ===
                url: https://example.com/doc

                A sufficiently long paragraph of curated documentation content here.
                """);

        DiscoveryProvider.Sources sources = DiscoveryProvider.fromInputFile(file);

        assertInstanceOf(FileCorpus.class, sources.search());
        assertSame(sources.search(), sources.fetcher()); // one corpus drives both seams
        assertTrue(sources.search().isCuratedCorpus());
    }

    @Test
    void braveWithoutCacheUsesRawClients() {
        DiscoveryProvider.Sources sources = DiscoveryProvider.brave("key", "2026-01-01to2026-06-22", null);
        assertInstanceOf(BraveSearchClient.class, sources.search());
        assertInstanceOf(HttpPageFetcher.class, sources.fetcher());
    }

    @Test
    void braveWithCacheWrapsBothSeams(@TempDir Path dir) {
        DiskCache cache = new DiskCache(dir, Duration.ofDays(7));
        DiscoveryProvider.Sources sources = DiscoveryProvider.brave("key", null, cache);
        assertInstanceOf(CachingSearchClient.class, sources.search());
        assertInstanceOf(CachingPageFetcher.class, sources.fetcher());
    }
}
