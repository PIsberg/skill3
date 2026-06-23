package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreshnessFilterTest {

    private final Cutoff cutoff = new Cutoff(YearMonth.of(2026, 1), "test");

    private Source source(String url, double authority, LocalDate published) {
        Source s = new Source(url);
        s.authority = authority;
        s.published = published;
        return s;
    }

    @Test
    void postCutoffOutranksPreCutoff() {
        Source post = source("https://a", 1.0, LocalDate.of(2026, 5, 1));
        Source pre = source("https://b", 1.0, LocalDate.of(2025, 6, 1));

        List<Source> out = new FreshnessFilter(cutoff, false).apply(List.of(pre, post));

        assertEquals("https://a", out.get(0).url);
        assertTrue(post.postCutoff);
        assertFalse(pre.postCutoff);
        assertEquals(1.0, post.recencyWeight);
        assertEquals(0.5, pre.recencyWeight);
        assertEquals(1.0, post.combinedScore);
        assertEquals(0.5, pre.combinedScore);
    }

    @Test
    void undatedGetsLowestRecency() {
        Source undated = source("https://a", 1.0, null);
        new FreshnessFilter(cutoff, false).apply(List.of(undated));
        assertEquals(0.3, undated.recencyWeight);
    }

    @Test
    void strictDropsPreCutoffAndUndated() {
        Source post = source("https://a", 1.0, LocalDate.of(2026, 5, 1));
        Source pre = source("https://b", 1.0, LocalDate.of(2025, 6, 1));
        Source undated = source("https://c", 1.0, null);

        List<Source> out = new FreshnessFilter(cutoff, true).apply(List.of(post, pre, undated));

        assertEquals(1, out.size());
        assertEquals("https://a", out.get(0).url);
    }

    @Test
    void dropsSourcesDatedAfterToday() {
        LocalDate today = LocalDate.of(2026, 6, 22);
        Source valid = source("https://a", 1.0, LocalDate.of(2026, 5, 1));
        Source future = source("https://b", 1.0, LocalDate.of(2026, 7, 28)); // after the run date

        List<Source> out = new FreshnessFilter(cutoff, false, today).apply(List.of(valid, future));

        assertEquals(1, out.size());
        assertEquals("https://a", out.get(0).url);
    }

    @Test
    void postCutoffRecencyDecaysWithAgeWhenTodayIsKnown() {
        LocalDate today = LocalDate.of(2026, 12, 31);
        Source fresh = source("https://a", 1.0, LocalDate.of(2026, 12, 1)); // near today
        Source older = source("https://b", 1.0, LocalDate.of(2026, 2, 1));  // just after cutoff

        List<Source> out = new FreshnessFilter(cutoff, false, today).apply(List.of(older, fresh));

        assertTrue(fresh.postCutoff);
        assertTrue(older.postCutoff);
        // Both inside the post-cutoff band, but the fresher one scores higher and ranks first.
        assertTrue(fresh.recencyWeight > older.recencyWeight,
                "fresher source should earn more recency: " + fresh.recencyWeight + " vs " + older.recencyWeight);
        assertTrue(older.recencyWeight >= 0.6 && fresh.recencyWeight <= 1.0);
        assertTrue(older.recencyWeight > 0.5); // still above the pre-cutoff weight
        assertEquals("https://a", out.get(0).url);
    }

    @Test
    void cutoffMonthItselfCountsAsPreCutoff() {
        Source s = source("https://a", 1.0, LocalDate.of(2026, 1, 15));
        new FreshnessFilter(cutoff, false).apply(List.of(s));
        assertFalse(s.postCutoff);
        assertEquals(0.5, s.recencyWeight);
    }
}
