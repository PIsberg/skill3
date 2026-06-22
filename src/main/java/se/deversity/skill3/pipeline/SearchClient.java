package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.util.List;

/** Discovery search abstraction; implemented by {@link BraveSearchClient}. */
@FunctionalInterface
public interface SearchClient {

    /** {@return result URLs for the query, best-first, up to {@code count}} */
    List<String> search(String query, int count) throws IOException;
}
