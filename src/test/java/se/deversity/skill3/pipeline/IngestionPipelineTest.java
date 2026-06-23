package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionPipelineTest {

    private static final Cutoff CUTOFF = new Cutoff(YearMonth.of(2026, 1), "test");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 22);

    private static Source source(String url, double authority, LocalDate published, String code) {
        Source s = new Source(url);
        s.authority = authority;
        s.published = published;
        s.codeBlocks.add(code);
        return s;
    }

    @Test
    void ranksPostCutoffAuthoritativeSourcesFirstAndAnnotatesConsensus() {
        Source authoritative = source("https://a.io/x", 1.0, LocalDate.of(2026, 5, 1), "shared();");
        Source other = source("https://b.io/y", 0.5, LocalDate.of(2026, 4, 1), "shared();");
        Source preCutoff = source("https://c.io/z", 1.0, LocalDate.of(2025, 6, 1), "old();");

        List<Source> ranked = new IngestionPipeline(CUTOFF, false, 2, TODAY)
                .ingest(List.of(other, preCutoff, authoritative));

        assertEquals(3, ranked.size());
        // Highest combined score (post-cutoff, authority 1.0, freshest) leads.
        assertEquals("https://a.io/x", ranked.get(0).url);
        // Ranking is monotonic by combined score.
        assertTrue(ranked.get(0).combinedScore >= ranked.get(1).combinedScore);
        assertTrue(ranked.get(1).combinedScore >= ranked.get(2).combinedScore);
        // "shared();" appears on two distinct hosts -> consensus >= 2 for both of them.
        assertTrue(ranked.stream().filter(s -> s.codeBlocks.contains("shared();"))
                .allMatch(s -> s.consensusCount >= 2));
    }

    @Test
    void strictCutoffDropsPreCutoffSources() {
        Source post = source("https://a.io/x", 1.0, LocalDate.of(2026, 5, 1), "x();");
        Source pre = source("https://b.io/y", 1.0, LocalDate.of(2025, 6, 1), "y();");

        List<Source> ranked = new IngestionPipeline(CUTOFF, true, 2, TODAY).ingest(List.of(post, pre));

        assertEquals(1, ranked.size());
        assertEquals("https://a.io/x", ranked.get(0).url);
    }
}
