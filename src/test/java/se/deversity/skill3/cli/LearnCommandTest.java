package se.deversity.skill3.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exercises the real CLI entry point ({@code CommandLine.execute}) and its exit-code
 * contract: 2 for bad input, 0 for success. The offline {@code --input-file} +
 * {@code --dry-run} combination drives the full arg-parsing and discovery wiring with
 * no network, no Brave key, and no model call (curated corpora skip query planning).
 */
class LearnCommandTest {

    private static int run(String... args) {
        return new CommandLine(new LearnCommand()).execute(args);
    }

    private static Path corpus(Path dir) throws Exception {
        Path file = dir.resolve("corpus.txt");
        Files.writeString(file, """
                === SOURCE ===
                url: https://modelcontextprotocol.io/spec
                date: 2026-05-01

                The 2026-05 revision documents the new _meta field on every request.

                ```
                shared();
                ```

                === SOURCE ===
                url: https://github.com/org/repo
                date: 2026-04-01

                A sufficiently long paragraph describing the released behaviour and flags.

                ```
                shared();
                ```
                """);
        return file;
    }

    @Test
    void missingRequiredLlmModelIsUsageError() {
        assertEquals(2, run("mcp", "--input-file", "whatever.txt"));
    }

    @Test
    void malformedCutoffTimeExitsTwo() {
        assertEquals(2, run("mcp", "--llm-model", "m", "--input-file", "whatever.txt",
                "--cutoff-time", "March 2025"));
    }

    @Test
    void unknownTargetModelWithoutOverrideExitsTwo() {
        assertEquals(2, run("mcp", "--llm-model", "m", "--input-file", "whatever.txt",
                "--target-model", "mystery-model"));
    }

    @Test
    void unreadableInputFileExitsTwo(@TempDir Path dir) {
        assertEquals(2, run("mcp", "--llm-model", "m",
                "--input-file", dir.resolve("missing.txt").toString()));
    }

    @Test
    void offlineDryRunSucceedsAndWritesNothing(@TempDir Path dir) throws Exception {
        Path corpusFile = corpus(dir);
        Path outDir = dir.resolve("out");

        int exit = run("mcp", "--llm-model", "m",
                "--input-file", corpusFile.toString(),
                "--output-dir", outDir.toString(),
                "--dry-run");

        assertEquals(0, exit);
        assertFalse(Files.exists(outDir.resolve("SKILL.md"))); // dry run writes nothing
    }
}
