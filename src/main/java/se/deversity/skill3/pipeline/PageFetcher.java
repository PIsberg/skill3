package se.deversity.skill3.pipeline;

import se.deversity.vibetags.annotations.AIContract;

import java.io.IOException;

/** Fetches raw HTML for a URL; implemented by {@link HttpPageFetcher}. */
@AIContract(reason = "Fetch seam. Keeping page retrieval behind it is what lets extraction, "
        + "date parsing and scoring be tested against HTML fixtures with no network, and it is "
        + "the boundary at which --input-file replaces the network entirely.")
@FunctionalInterface
public interface PageFetcher {

    /** {@return the response body as HTML} */
    String fetch(String url) throws IOException;
}
