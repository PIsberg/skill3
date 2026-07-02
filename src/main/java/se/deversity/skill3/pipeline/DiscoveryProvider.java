package se.deversity.skill3.pipeline;

import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AISecure;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Builds the discovery pair — a {@link SearchClient} and a {@link PageFetcher} — for a run,
 * centralizing the choice between live Brave search (optionally cached) and an offline curated
 * input file. Adding a discovery backend is a method here, not another branch in the CLI.
 */
@AISecure(aspect = "forwards the Brave subscription token to the search client; must not log it")
public final class DiscoveryProvider {

    private DiscoveryProvider() {
    }

    /** The two collaborators the pipeline needs for discovery. */
    public record Sources(SearchClient search, PageFetcher fetcher) {
    }

    /** Offline discovery from a user-curated corpus file (no key, no network). */
    public static Sources fromInputFile(Path inputFile) throws IOException {
        FileCorpus corpus = FileCorpus.load(inputFile);
        return new Sources(corpus, corpus);
    }

    /**
     * Live Brave discovery. When {@code cache} is non-null, both the search and the page fetch
     * are wrapped so re-running a topic is served from disk.
     */
    public static Sources brave(String apiKey, @Nullable String freshness, @Nullable DiskCache cache) {
        SearchClient search = new BraveSearchClient(apiKey, freshness);
        PageFetcher fetcher = new HttpPageFetcher();
        if (cache != null) {
            // The freshness window is part of the request Brave sees but not of the
            // search(query, count) signature, so it must qualify the cache key — otherwise
            // runs with different cutoffs would share (wrong-window) cached results.
            search = new CachingSearchClient(search, cache, freshness);
            fetcher = new CachingPageFetcher(fetcher, cache);
        }
        return new Sources(search, fetcher);
    }
}
