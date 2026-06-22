package se.deversity.skill3.pipeline;

import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cutoff-anchored freshness scoring. Each source is scored
 * {@code authority * recencyWeight}; post-cutoff publication earns the full
 * recency weight. Sorting by the combined score realizes the override rule: a
 * post-cutoff authoritative source (1.0 x 1.0) always outranks pre-cutoff content
 * (<= 1.0 x 0.5). With {@code strict}, pre-cutoff and undated sources are dropped.
 *
 * <p>An upper bound ({@code today}) drops sources published after the run date —
 * Brave's freshness window is only a hint, and a future-dated page is a leak or a
 * mis-dated source, never legitimate evidence for "what changed by today".
 */
public class FreshnessFilter {

    static final double RECENCY_POST_CUTOFF = 1.0;
    static final double RECENCY_PRE_CUTOFF = 0.5;
    static final double RECENCY_UNDATED = 0.3;

    private final Cutoff cutoff;
    private final boolean strict;
    private final LocalDate today;

    public FreshnessFilter(Cutoff cutoff, boolean strict) {
        this(cutoff, strict, LocalDate.MAX);
    }

    public FreshnessFilter(Cutoff cutoff, boolean strict, LocalDate today) {
        this.cutoff = cutoff;
        this.strict = strict;
        this.today = today;
    }

    /** Scores, optionally filters, and returns sources sorted best-first. */
    public List<Source> apply(List<Source> sources) {
        List<Source> out = new ArrayList<>();
        for (Source s : sources) {
            if (s.published != null && s.published.isAfter(today)) {
                continue; // future-dated -> freshness leak or mis-dated; not valid evidence
            }
            s.postCutoff = s.published != null
                    && s.published.isAfter(cutoff.month().atEndOfMonth());
            s.recencyWeight = recency(s);
            s.combinedScore = s.authority * s.recencyWeight;
            if (strict && !s.postCutoff) {
                continue; // hard filter excludes pre-cutoff and undated
            }
            out.add(s);
        }
        out.sort(Comparator.comparingDouble((Source s) -> s.combinedScore).reversed());
        return out;
    }

    private double recency(Source s) {
        if (s.published == null) {
            return RECENCY_UNDATED;
        }
        return s.postCutoff ? RECENCY_POST_CUTOFF : RECENCY_PRE_CUTOFF;
    }
}
