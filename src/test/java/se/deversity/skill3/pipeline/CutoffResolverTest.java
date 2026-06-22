package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.Cutoff;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CutoffResolverTest {

    private final CutoffResolver resolver = new CutoffResolver();

    @Test
    void resolvesKnownModel() {
        Cutoff c = resolver.resolve("claude-opus-4-8", null);
        assertEquals(YearMonth.of(2026, 1), c.month());
    }

    @Test
    void overrideWinsOverModel() {
        Cutoff c = resolver.resolve("claude-opus-4-8", "2025-03");
        assertEquals(YearMonth.of(2025, 3), c.month());
    }

    @Test
    void unknownModelWithoutOverrideThrows() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("mystery-model", null));
    }

    @Test
    void malformedOverrideThrows() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("claude-opus-4-8", "March 2025"));
    }

    @Test
    void overrideIsTrimmed() {
        assertEquals(YearMonth.of(2025, 3), resolver.resolve("claude-opus-4-8", "  2025-03  ").month());
    }
}
