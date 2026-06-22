package se.deversity.skill3.skillspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfCorrectionLoopTest {

    private static SkillSpectorReport dirty() {
        return new SkillSpectorReport(List.of(new Finding("prompt-injection", "high", "m", "f", 1)), "raw");
    }

    private static SkillSpectorReport clean() {
        return new SkillSpectorReport(List.of(), "[]");
    }

    @Test
    void redactsCommonSecretShapes() {
        String redacted = SelfCorrectionLoop.redactSecrets("api_key: sk-ABCDEFGHIJKLMNOPQRSTUVWX");
        assertTrue(redacted.contains("***REDACTED***"));
        assertFalse(redacted.contains("sk-ABCDEFGHIJKLMNOPQRSTUVWX"));
    }

    @Test
    void convergesWhenRescanIsClean(@TempDir Path dir) throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any())).thenReturn(dirty(), clean());

        SelfCorrectionLoop loop = new SelfCorrectionLoop(runner, (cur, rep) -> "fixed", 3);
        SelfCorrectionLoop.Result res = loop.run(dir, dir.resolve("SKILL.md"), "draft");

        assertTrue(res.clean());
        assertEquals(1, res.iterations());
    }

    @Test
    void reportsResidualFindingsAfterMaxIterations(@TempDir Path dir) throws Exception {
        SkillSpectorRunner runner = mock(SkillSpectorRunner.class);
        when(runner.scan(any())).thenReturn(dirty());

        SelfCorrectionLoop loop = new SelfCorrectionLoop(runner, (cur, rep) -> "still-bad", 2);
        SelfCorrectionLoop.Result res = loop.run(dir, dir.resolve("SKILL.md"), "draft");

        assertFalse(res.clean());
        assertEquals(2, res.iterations());
        assertFalse(res.finalReport().findings().isEmpty());
    }
}
