package se.deversity.skill3.skillspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillSpectorRunnerTest {

    @Test
    void missingExecutableThrowsUnavailable(@TempDir Path dir) {
        SkillSpectorRunner runner = new SkillSpectorRunner("skill3-nonexistent-binary-xyz");
        assertThrows(SkillSpectorUnavailableException.class, () -> runner.scan(dir));
    }

    @Test
    void interpretParsesValidJson() throws IOException {
        SkillSpectorReport report = SkillSpectorRunner.interpret("{\"issues\": []}", 0, "");
        assertTrue(report.clean());
    }

    @Test
    void interpretKeepsFindingsEvenWhenExitNonZero() throws IOException {
        // Scanners often exit non-zero precisely because they found issues; valid JSON => not a failure.
        SkillSpectorReport report = SkillSpectorRunner.interpret(
                "{\"issues\": [{\"id\": \"x\", \"severity\": \"HIGH\", \"message\": \"m\"}]}", 1, "warn");
        assertFalseClean(report);
    }

    @Test
    void interpretThrowsWithStderrOnBlankOutput() {
        IOException e = assertThrows(IOException.class,
                () -> SkillSpectorRunner.interpret("", 1, "Traceback: ModuleNotFoundError: skillspector"));
        assertTrue(e.getMessage().contains("exit code 1"));
        assertTrue(e.getMessage().contains("ModuleNotFoundError"));
    }

    @Test
    void interpretThrowsOnGarbledOutputWithNonZeroExit() {
        IOException e = assertThrows(IOException.class,
                () -> SkillSpectorRunner.interpret("Segmentation fault", 139, "core dumped"));
        assertTrue(e.getMessage().contains("core dumped"));
    }

    @Test
    void interpretReportsNoStderrWhenEmpty() {
        IOException e = assertThrows(IOException.class, () -> SkillSpectorRunner.interpret("", 2, ""));
        assertTrue(e.getMessage().contains("No stderr"));
    }

    private static void assertFalseClean(SkillSpectorReport report) {
        assertEquals(false, report.clean());
    }
}
