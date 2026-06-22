package se.deversity.skill3.skillspector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillSpectorRunnerTest {

    @Test
    void missingExecutableThrowsUnavailable(@TempDir Path dir) {
        SkillSpectorRunner runner = new SkillSpectorRunner("skill3-nonexistent-binary-xyz");
        assertThrows(SkillSpectorUnavailableException.class, () -> runner.scan(dir));
    }
}
