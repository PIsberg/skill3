package se.deversity.skill3.pipeline;

import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cutoff-anchored freshness scoring. Each source is scored
 * {@code authority * recencyWeight}. Post-cutoff publication earns a recency weight
 * that <em>decays with age</em>: {@value #RECENCY_POST_CUTOFF} for a source published
 * today, falling linearly to {@value #RECENCY_POST_FLOOR} for one published right after
 * the cutoff — so in fast-moving topics last week's source outranks one from months ago.
 * The floor stays above the pre-cutoff weight ({@value #RECENCY_PRE_CUTOFF}), so a
 * post-cutoff source still outranks pre-cutoff content of equal authority. With
 * {@code strict}, pre-cutoff and undated sources are dropped.
 *
 * <p>An upper bound ({@code today}) drops sources published after the run date —
 * Brave's freshness window is only a hint, and a future-dated page is a leak or a
 * mis-dated source, never legitimate evidence for "what changed by today".
 */
public class FreshnessFilter {

    static final double RECENCY_POST_CUTOFF = 1.0;
    /** Lower bound of the post-cutoff decay band (a source published at the cutoff edge). */
    static final double RECENCY_POST_FLOOR = 0.6;
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
        return s.postCutoff ? postCutoffWeight(s.published) : RECENCY_PRE_CUTOFF;
    }

    /**
     * Linear decay across the post-cutoff window: full weight at {@code today}, falling to
     * {@link #RECENCY_POST_FLOOR} at the cutoff edge. Falls back to full weight when there is
     * no real run date (e.g. {@link LocalDate#MAX}) so scoring stays stable without a today.
     */
    private double postCutoffWeight(LocalDate published) {
        if (today.equals(LocalDate.MAX)) {
            return RECENCY_POST_CUTOFF;
        }
        long span = ChronoUnit.DAYS.between(cutoff.month().atEndOfMonth(), today);
        if (span <= 0) {
            return RECENCY_POST_CUTOFF;
        }
        long age = ChronoUnit.DAYS.between(published, today); // 0 == published today
        double ageFraction = Math.min(1.0, Math.max(0.0, (double) age / span));
        return RECENCY_POST_CUTOFF - ageFraction * (RECENCY_POST_CUTOFF - RECENCY_POST_FLOOR);
    }
}
