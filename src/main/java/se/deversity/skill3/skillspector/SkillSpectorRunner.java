package se.deversity.skill3.skillspector;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invokes the SkillSpector CLI via {@link ProcessBuilder} with an explicit
 * argument list (never a shell string). The executable path and exact flags are
 * recorded during {@code setup}; the defaults here mirror the documented surface
 * and can be overridden.
 */
public class SkillSpectorRunner {

    /** Cap on captured stderr echoed into an exception, so a runaway log stays readable. */
    private static final int MAX_STDERR_CHARS = 2000;
    /** Hard bound on one scan: a wedged child (broken venv, deadlocked Python) must not stall
     *  the pipeline — the self-correction loop calls scan() once per iteration. */
    private static final Duration DEFAULT_SCAN_TIMEOUT = Duration.ofMinutes(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String executable;
    private final List<String> scanArgs;
    private final Duration scanTimeout;

    public SkillSpectorRunner(String executable) {
        // --no-llm: run static analysis only, so vetting needs no API key
        // (matches Skill3's fully-local design). Findings still emit on stdout.
        this(executable, List.of("scan", "--no-llm", "--format", "json"));
    }

    public SkillSpectorRunner(String executable, List<String> scanArgs) {
        this(executable, scanArgs, DEFAULT_SCAN_TIMEOUT);
    }

    SkillSpectorRunner(String executable, List<String> scanArgs, Duration scanTimeout) {
        this.executable = executable;
        this.scanArgs = List.copyOf(scanArgs);
        this.scanTimeout = scanTimeout;
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
        // Drain both pipes on their own threads: a child that fills a pipe buffer while the
        // parent blocks reading the other would deadlock, and a child that never exits must
        // not block the parent in readAllBytes(). Keep the drained stderr instead of
        // discarding it: when the scan fails (e.g. a broken venv, a Python traceback, an
        // architecture mismatch) it is the only useful diagnostic.
        byte[][] outHolder = {new byte[0]};
        byte[][] errHolder = {new byte[0]};
        Thread outDrain = drain(process.getInputStream(), outHolder, "skillspector-stdout-drain");
        Thread errDrain = drain(process.getErrorStream(), errHolder, "skillspector-stderr-drain");
        try {
            if (!process.waitFor(scanTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("SkillSpector scan timed out after " + scanTimeout.toSeconds()
                        + "s; killed '" + executable + "'");
            }
            // The pipes hit EOF once the child is gone; the joins are bounded only to guard
            // against a grandchild keeping the descriptors open. A successful join is also the
            // happens-before edge that makes the holder writes visible on this thread.
            outDrain.join(10_000);
            errDrain.join(10_000);
            String output = new String(outHolder[0], StandardCharsets.UTF_8);
            String stderr = new String(errHolder[0], StandardCharsets.UTF_8);
            return interpret(output, process.exitValue(), stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("SkillSpector scan interrupted", e);
        } finally {
            // Deterministically release the child's handles instead of waiting for GC —
            // scan() is called repeatedly by the self-correction loop. Kill the process
            // if waitFor() was interrupted mid-scan or the scan timed out.
            if (process.isAlive()) {
                process.destroy();
            }
        }
    }

    private static Thread drain(InputStream stream, byte[][] holder, String name) {
        Thread t = new Thread(() -> {
            try (InputStream in = stream) {
                holder[0] = in.readAllBytes();
            } catch (IOException ignored) {
                // best-effort drain
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
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
