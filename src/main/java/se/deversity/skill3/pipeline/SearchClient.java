package se.deversity.skill3.pipeline;

import se.deversity.vibetags.annotations.AIContract;

import java.io.IOException;
import java.util.List;

/** Discovery search abstraction; implemented by {@link BraveSearchClient}. */
@AIContract(reason = "Discovery seam. BraveSearchClient (live) and FileCorpus (--input-file) "
        + "both implement it, and isCuratedCorpus() is what tells the pipeline to skip LLM query "
        + "planning. Removing the default method, or changing what it returns, silently re-enables "
        + "planning for a corpus that is already the curated result set.")
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
