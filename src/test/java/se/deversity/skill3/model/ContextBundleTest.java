package se.deversity.skill3.model;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextBundleTest {

    private static final Cutoff CUTOFF = new Cutoff(YearMonth.of(2026, 1), "test");

    @Test
    void copiesSourcesDefensivelyAtConstruction() {
        List<Source> input = new ArrayList<>();
        input.add(new Source("https://a"));
        ContextBundle bundle = new ContextBundle("mcp", "claude-opus-4-8", CUTOFF, input);

        input.add(new Source("https://b")); // mutating the caller's list must not leak in
        assertEquals(1, bundle.sources().size());
        assertEquals("https://a", bundle.sources().get(0).url);
    }

    @Test
    void exposedSourcesListIsUnmodifiable() {
        ContextBundle bundle =
                new ContextBundle("mcp", "claude-opus-4-8", CUTOFF, List.of(new Source("https://a")));
        assertThrows(UnsupportedOperationException.class,
                () -> bundle.sources().add(new Source("https://x")));
    }
}
