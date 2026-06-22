package se.deversity.skill3.cli;

import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares the local environment: locate a compatible Python, clone SkillSpector,
 * create a venv, install it, and verify the CLI. External processes are invoked
 * with explicit argument lists (never a shell string).
 *
 * <p>SkillSpector requires Python &ge; 3.12 and &lt; 3.15, so {@code setup} probes
 * for a suitable interpreter rather than assuming {@code python} on PATH is new
 * enough.
 */
@Command(name = "setup",
        mixinStandardHelpOptions = true,
        description = "Clone and install NVIDIA SkillSpector into a local virtualenv.")
public class SetupCommand implements Callable<Integer> {

    private static final String REPO = "https://github.com/NVIDIA/SkillSpector.git";
    private static final int MIN_MINOR = 12;
    private static final int MAX_MINOR = 14; // requires-python <3.15
    private static final Pattern VERSION = Pattern.compile("Python 3\\.(\\d+)");

    @Override
    public Integer call() {
        try {
            System.out.println("[1/5] Locating a compatible Python (3.12-3.14)...");
            List<String> python = findCompatiblePython();
            if (python == null) {
                System.err.println("No Python 3.12-3.14 found. SkillSpector requires that range. "
                        + "Install a supported Python and retry.");
                return 1;
            }
            System.out.println("  using: " + String.join(" ", python));

            System.out.println("[2/5] Cloning SkillSpector...");
            if (Files.isDirectory(Venv.SOURCE)) {
                System.out.println("  (skillspector-src already present, skipping clone)");
            } else if (run(List.of("git", "clone", "--depth", "1", REPO, Venv.SOURCE.toString())) != 0) {
                System.err.println("git clone failed.");
                return 1;
            }

            System.out.println("[3/5] Creating virtualenv (.venv)...");
            List<String> venvCmd = new ArrayList<>(python);
            venvCmd.add("-m");
            venvCmd.add("venv");
            venvCmd.add(Venv.DIR.toString());
            if (run(venvCmd) != 0) {
                System.err.println("venv creation failed.");
                return 1;
            }

            System.out.println("[4/5] Installing SkillSpector...");
            if (run(List.of(Venv.bin("pip").toString(), "install", "-e", Venv.SOURCE.toString())) != 0) {
                System.err.println("pip install failed.");
                return 1;
            }

            System.out.println("[5/5] Verifying SkillSpector CLI...");
            if (run(List.of(Venv.bin("skillspector").toString(), "--help")) != 0) {
                System.err.println("SkillSpector verification failed.");
                return 1;
            }

            System.out.println("Setup complete. SkillSpector is installed at " + Venv.bin("skillspector"));
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Setup error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("Setup error: " + e.getMessage());
            return 1;
        }
    }

    /** {@return the first interpreter base command in [3.12, 3.14], or null} */
    static List<String> findCompatiblePython() {
        for (List<String> candidate : candidates()) {
            int minor = probeMinor(candidate);
            if (minor >= MIN_MINOR && minor <= MAX_MINOR) {
                return candidate;
            }
        }
        return null;
    }

    private static List<List<String>> candidates() {
        List<List<String>> c = new ArrayList<>();
        if (Venv.isWindows()) {
            c.add(List.of("py", "-3.14"));
            c.add(List.of("py", "-3.13"));
            c.add(List.of("py", "-3.12"));
            c.add(List.of("python"));
        } else {
            c.add(List.of("python3.14"));
            c.add(List.of("python3.13"));
            c.add(List.of("python3.12"));
            c.add(List.of("python3"));
            c.add(List.of("python"));
        }
        return c.stream().map(List::copyOf).toList();
    }

    /** {@return the Python 3 minor version of {@code base}, or -1 if unavailable} */
    static int probeMinor(List<String> base) {
        List<String> cmd = new ArrayList<>(base);
        cmd.add("--version");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            Matcher m = VERSION.matcher(out);
            return m.find() ? Integer.parseInt(m.group(1)) : -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private static int run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (!out.isBlank()) {
            System.out.println(out.stripTrailing());
        }
        return code;
    }
}
