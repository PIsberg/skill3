package se.deversity.skill3.skillspector;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Invokes the SkillSpector CLI via {@link ProcessBuilder} with an explicit
 * argument list (never a shell string). The executable path and exact flags are
 * recorded during {@code setup}; the defaults here mirror the documented surface
 * and can be overridden.
 */
public class SkillSpectorRunner {

    /** Cap on captured stderr echoed into an exception, so a runaway log stays readable. */
    private static final int MAX_STDERR_CHARS = 2000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String executable;
    private final List<String> scanArgs;

    public SkillSpectorRunner(String executable) {
        // --no-llm: run static analysis only, so vetting needs no API key
        // (matches Skill3's fully-local design). Findings still emit on stdout.
        this(executable, List.of("scan", "--no-llm", "--format", "json"));
    }

    public SkillSpectorRunner(String executable, List<String> scanArgs) {
        this.executable = executable;
        this.scanArgs = List.copyOf(scanArgs);
    }

    public SkillSpectorReport scan(Path skillDir) throws SkillSpectorUnavailableException, IOException {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(scanArgs);
        command.add(skillDir.toString());

        // Do NOT merge stderr into stdout: the JSON report is on stdout, logs on
        // stderr. Drain stderr on a separate thread to avoid pipe-buffer deadlock.
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new SkillSpectorUnavailableException(
                    "Could not run '" + executable + "'. Run `setup` first.", e);
        }
        // Keep the drained stderr instead of discarding it: when the scan fails (e.g. a broken
        // venv, a Python traceback, an architecture mismatch) it is the only useful diagnostic.
        byte[][] errHolder = {new byte[0]};
        Thread drain = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                errHolder[0] = err.readAllBytes();
            } catch (IOException ignored) {
                // best-effort drain
            }
        }, "skillspector-stderr-drain");
        drain.setDaemon(true);
        drain.start();
        try (InputStream out = process.getInputStream()) {
            String output = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            drain.join(1000);
            String stderr = new String(errHolder[0], StandardCharsets.UTF_8);
            return interpret(output, exit, stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("SkillSpector scan interrupted", e);
        } finally {
            // Deterministically release the child's handles instead of waiting for GC —
            // scan() is called repeatedly by the self-correction loop. Stop the drain
            // thread and kill the process if waitFor() was interrupted mid-scan.
            drain.interrupt();
            if (process.isAlive()) {
                process.destroy();
            }
        }
    }

    /**
     * Turns a raw scan invocation into a report, or throws with diagnostics when the scan
     * clearly failed: blank stdout, or a non-zero exit whose stdout isn't even parseable JSON
     * (a non-zero exit <em>with</em> valid JSON is normal — that's how a scanner reports findings).
     * The captured stderr is the actionable part, so it rides along in the message.
     */
    static SkillSpectorReport interpret(String stdout, int exitCode, String stderr) throws IOException {
        if (stdout.isBlank() || (exitCode != 0 && !isParseableJson(stdout))) {
            String tail = stderr == null ? "" : stderr.strip();
            throw new IOException("SkillSpector scan failed (exit code " + exitCode + "). "
                    + (tail.isEmpty() ? "No stderr output." : "stderr: " + truncate(tail)));
        }
        return SkillSpectorReport.parse(stdout);
    }

    private static boolean isParseableJson(String s) {
        try {
            MAPPER.readTree(s);
            return true;
        } catch (JacksonException | RuntimeException e) {
            return false;
        }
    }

    private static String truncate(String s) {
        return s.length() <= MAX_STDERR_CHARS ? s : s.substring(0, MAX_STDERR_CHARS) + "… (truncated)";
    }
}
