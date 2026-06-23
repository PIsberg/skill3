package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.util.Optional;

/** Caches fetched page bodies so re-running a topic doesn't re-download every source. */
public final class CachingPageFetcher implements PageFetcher {

    private final PageFetcher delegate;
    private final DiskCache cache;

    public CachingPageFetcher(PageFetcher delegate, DiskCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public String fetch(String url) throws IOException {
        String key = DiskCache.key("page", url);
        Optional<String> hit = cache.get(key);
        if (hit.isPresent()) {
            return hit.get();
        }
        String body = delegate.fetch(url);
        cache.put(key, body);
        return body;
    }
}
