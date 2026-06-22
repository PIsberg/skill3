package se.deversity.skill3.model;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A resolved knowledge cutoff: the month boundary used as the freshness anchor,
 * plus a human-readable label describing where it came from.
 */
public record Cutoff(YearMonth month, String label) {

    /** ISO {@code yyyy-MM} representation of the cutoff month. */
    public String iso() {
        return month.toString();
    }

    /**
     * Brave {@code freshness} range from the first day of the cutoff month through {@code today},
     * e.g. {@code 2026-01-01to2026-06-22}. Anchors discovery on content published after the model's
     * knowledge cutoff.
     */
    public String freshnessRange(LocalDate today) {
        return month.atDay(1) + "to" + today;
    }
}
