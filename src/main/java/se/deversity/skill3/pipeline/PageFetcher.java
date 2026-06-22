package se.deversity.skill3.pipeline;

import java.io.IOException;

/** Fetches raw HTML for a URL; implemented by {@link HttpPageFetcher}. */
@FunctionalInterface
public interface PageFetcher {

    /** {@return the response body as HTML} */
    String fetch(String url) throws IOException;
}
