package se.deversity.skill3.pipeline;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Caches discovery results so re-running a topic doesn't re-hit the search API. */
public final class CachingSearchClient implements SearchClient {

    private final SearchClient delegate;
    private final DiskCache cache;
    private final String qualifier;

    public CachingSearchClient(SearchClient delegate, DiskCache cache) {
        this(delegate, cache, null);
    }

    /**
     * @param qualifier extra cache-key component for request context the delegate bakes in
     *        outside the {@code search(query, count)} signature — e.g. the Brave freshness
     *        window, which changes with the cutoff. Without it, runs with different windows
     *        would share entries and serve results for the wrong window.
     */
    public CachingSearchClient(SearchClient delegate, DiskCache cache, @Nullable String qualifier) {
        this.delegate = delegate;
        this.cache = cache;
        this.qualifier = qualifier == null ? "" : qualifier;
    }

    @Override
    public List<String> search(String query, int count) throws IOException {
        String key = DiskCache.key("search", qualifier, query, Integer.toString(count));
        Optional<String> hit = cache.get(key);
        if (hit.isPresent()) {
            String body = hit.get();
            return body.isEmpty() ? List.of() : List.of(body.split("\n", -1));
        }
        List<String> urls = delegate.search(query, count);
        cache.put(key, String.join("\n", urls));
        return urls;
    }

    @Override
    public boolean isCuratedCorpus() {
        return delegate.isCuratedCorpus();
    }
}
