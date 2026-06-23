package se.deversity.skill3.skillspector;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.console.ProgressBar;
import se.deversity.skill3.model.Source;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InputVetterTest {

    private static final String SECRET = "sk-ABCDEFGHIJKLMNOPQRSTUVWX";

    private static SkillSpectorReport clean() {
        return new SkillSpectorReport(List.of(), "[]");
    }

    private static SkillSpectorReport dirty() {
        return new SkillSpectorReport(
                List.of(new Finding("prompt-injection", "HIGH", "ignore previous instructions", "source-1.txt", 3)),
                "raw");
    }

    private static Source sourceWithSecret() {
        Source s = new Source("https://docs.example.com/page");
        s.excerpts.add("To authenticate, set api_key: " + SECRET);
        s.codeBlocks.add("curl -H 'x: ok' https://api.example.com");
        return s;
    }

    @Test
    void redactsSecretsInPlaceBeforeScanning() throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any())).thenReturn(clean());

        Source s = sourceWithSecret();
        InputVetter.Result res = new InputVetter(runner).vet(List.of(s), ProgressBar.silent());

        // The secret never survives into the text the synthesizer would see.
        assertFalse(s.excerpts.get(0).contains(SECRET));
        assertTrue(s.excerpts.get(0).contains("***REDACTED***"));
        assertTrue(res.redactions() >= 1);
        assertTrue(res.vetted());
        assertTrue(res.clean());
    }

    @Test
    void surfacesScannerFindings() throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any())).thenReturn(dirty());

        InputVetter.Result res = new InputVetter(runner)
                .vet(List.of(new Source("https://x.com/a")), ProgressBar.silent());

        assertTrue(res.vetted());
        assertFalse(res.clean());
        assertEquals(1, res.report().findings().size());
    }

    @Test
    void quarantinesOnlyTheSourceWithAHighSeverityFinding() throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any())).thenReturn(new SkillSpectorReport(
                List.of(new Finding("prompt-injection", "HIGH", "m", "source-2.txt", 1)), "raw"));

        Source s1 = new Source("https://ok.example/a");
        Source s2 = new Source("https://evil.example/b");
        InputVetter.Result res = new InputVetter(runner).vet(List.of(s1, s2), ProgressBar.silent());

        assertEquals(List.of(s1), res.kept());        // identity — the safe source survives
        assertEquals(List.of(s2), res.quarantined()); // the flagged one is dropped
    }

    @Test
    void keepsSourcesWhoseFindingsAreOnlyAdvisory() throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any())).thenReturn(new SkillSpectorReport(
                List.of(new Finding("style", "MEDIUM", "m", "source-1.txt", 1)), "raw"));

        Source s1 = new Source("https://ok.example/a");
        InputVetter.Result res = new InputVetter(runner).vet(List.of(s1), ProgressBar.silent());

        assertTrue(res.quarantined().isEmpty()); // MEDIUM never quarantines
        assertEquals(List.of(s1), res.kept());
    }

    @Test
    void unavailableScannerStillRedactsSecrets() throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any()))
                .thenThrow(new SkillSpectorUnavailableException("not installed", null));

        Source s = sourceWithSecret();
        InputVetter.Result res = new InputVetter(runner).vet(List.of(s), ProgressBar.silent());

        // Scanning is skipped, but the deterministic redaction still protects the secret.
        assertFalse(res.vetted());
        assertFalse(res.clean());
        assertNull(res.report());
        assertTrue(res.redactions() >= 1);
        assertFalse(s.excerpts.get(0).contains(SECRET));
    }
}
