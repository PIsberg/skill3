package se.deversity.skill3.cli;

import java.nio.file.Path;
import java.util.Locale;

/** Cross-platform paths for the local Python virtual environment used by setup. */
public final class Venv {

    public static final Path DIR = Path.of(".venv");
    public static final Path SOURCE = Path.of("skillspector-src");

    private Venv() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Resolves an executable inside the venv (Scripts/ on Windows, bin/ on POSIX). */
    public static Path bin(String name) {
        Path dir = isWindows() ? DIR.resolve("Scripts") : DIR.resolve("bin");
        return dir.resolve(isWindows() ? name + ".exe" : name);
    }
}
