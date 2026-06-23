package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.util.List;

/** Discovery search abstraction; implemented by {@link BraveSearchClient}. */
@FunctionalInterface
public interface SearchClient {

    /** {@return result URLs for the query, best-first, up to {@code count}} */
    List<String> search(String query, int count) throws IOException;

    /**
     * {@return whether this client already <em>is</em> the curated result set} When true, the
     * pipeline skips LLM query planning (the planned queries would be ignored anyway) and just
     * uses the topic as a nominal query. {@link FileCorpus} overrides this; live search does not.
     */
    default boolean isCuratedCorpus() {
        return false;
    }
}
