package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Caches discovery results so re-running a topic doesn't re-hit the search API. */
public final class CachingSearchClient implements SearchClient {

    private final SearchClient delegate;
    private final DiskCache cache;

    public CachingSearchClient(SearchClient delegate, DiskCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public List<String> search(String query, int count) throws IOException {
        String key = DiskCache.key("search", query, Integer.toString(count));
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
