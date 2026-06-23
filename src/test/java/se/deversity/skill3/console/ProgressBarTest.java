package se.deversity.skill3.console;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressBarTest {

    @Test
    void rendersTitlePercentAndLabel() {
        StringBuilder out = new StringBuilder();
        ProgressBar bar = new ProgressBar(out);

        bar.start(2, "Vetting");
        bar.step("source 1/2");
        bar.done("clean");

        String text = out.toString();
        assertTrue(text.contains("Vetting"));
        assertTrue(text.contains("0%"));
        assertTrue(text.contains("50%"));
        assertTrue(text.contains("100%"));
        assertTrue(text.contains("source 1/2"));
        assertTrue(text.contains("clean"));
        // The final frame fills the whole bar and terminates the line.
        assertTrue(text.contains("[########################]"));
        assertTrue(text.endsWith(System.lineSeparator()));
    }

    @Test
    void redrawsInPlaceWithCarriageReturn() {
        StringBuilder out = new StringBuilder();
        ProgressBar bar = new ProgressBar(out);
        bar.start(1, "Work");
        bar.done("ok");
        // Every frame begins with a carriage return so the line is overwritten, not appended.
        assertTrue(out.toString().startsWith("\r"));
    }

    @Test
    void stepIsCappedAtTotal() {
        StringBuilder out = new StringBuilder();
        ProgressBar bar = new ProgressBar(out);
        bar.start(1, "Work");
        bar.step("a");
        bar.step("b"); // beyond total — must not exceed 100%
        assertFalse(out.toString().contains("200%"));
        assertTrue(out.toString().contains("100%"));
    }

    @Test
    void silentBarWritesNothing() {
        ProgressBar bar = ProgressBar.silent();
        // Drives the full lifecycle; a silent bar must never throw and never render.
        bar.start(3, "Quiet");
        bar.step("x");
        bar.done("y");
        StringBuilder out = new StringBuilder();
        new ProgressBar(out, false).start(1, "Off");
        assertEquals("", out.toString());
    }
}
