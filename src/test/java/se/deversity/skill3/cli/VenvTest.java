package se.deversity.skill3.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VenvTest {

    @Test
    void resolvesWindowsLayout() {
        assertEquals(Path.of(".venv", "Scripts", "skillspector.exe"),
                Venv.bin("skillspector", true));
    }

    @Test
    void resolvesPosixLayout() {
        assertEquals(Path.of(".venv", "bin", "skillspector"),
                Venv.bin("skillspector", false));
    }

    @Test
    void publicBinMatchesCurrentPlatform() {
        assertEquals(Venv.bin("skillspector", Venv.isWindows()), Venv.bin("skillspector"));
    }
}
