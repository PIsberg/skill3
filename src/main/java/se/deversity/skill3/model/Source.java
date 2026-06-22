package se.deversity.skill3.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A single discovered documentation source, enriched as it flows through the
 * ingestion pipeline. Plain mutable data carrier — stages set the scoring fields.
 */
public class Source {

    /** Source URL (also the identity key for consensus by host). */
    public final String url;

    public String title = "";

    /** Extracted plain-text excerpts (paragraphs, headings, API signatures). */
    public final List<String> excerpts = new ArrayList<>();

    /** Extracted fenced code blocks (pruned by consensus). */
    public List<String> codeBlocks = new ArrayList<>();

    /** Publication date, or {@code null} if none could be extracted. */
    public LocalDate published;

    /** Authority weight in [0, 1] (see {@code AuthorityScorer}). */
    public double authority;

    /** Recency weight in [0, 1] relative to the cutoff (see {@code FreshnessFilter}). */
    public double recencyWeight;

    /** {@code authority * recencyWeight}; the ranking key. */
    public double combinedScore;

    /** Number of independent sources agreeing on this source's code (see consensus). */
    public int consensusCount;

    /** True when {@link #published} is strictly after the cutoff month. */
    public boolean postCutoff;

    public Source(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "Source{url=%s, authority=%.2f, recency=%.2f, combined=%.2f, postCutoff=%s, published=%s}"
                .formatted(url, authority, recencyWeight, combinedScore, postCutoff, published);
    }
}
