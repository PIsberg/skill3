package se.deversity.skill3.skillspector;

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
        Thread drain = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                err.readAllBytes();
            } catch (IOException ignored) {
                // best-effort drain
            }
        }, "skillspector-stderr-drain");
        drain.setDaemon(true);
        drain.start();
        try (InputStream out = process.getInputStream()) {
            String output = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            drain.join(1000);
            return SkillSpectorReport.parse(output);
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
}
