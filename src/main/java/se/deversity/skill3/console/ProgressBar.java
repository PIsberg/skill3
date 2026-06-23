package se.deversity.skill3.console;

/**
 * A minimal, dependency-free console progress bar that redraws a single line in
 * place with a carriage return — e.g. {@code Vetting input [########----] 67% source 4/6}.
 *
 * <p>It writes to an injected {@link Appendable} (default {@code System.out}) so it
 * stays testable: a test passes a {@link StringBuilder} and asserts on the frames,
 * or uses {@link #silent()} to disable rendering entirely. Rendering is best-effort —
 * an {@link java.io.IOException} from the sink is swallowed, never propagated into the
 * work the bar is reporting on.
 */
public final class ProgressBar {

    private static final int WIDTH = 24;

    private final Appendable sink;
    private final boolean enabled;

    private int total = 1;
    private int current;
    private String title = "";
    /** Length of the last frame written, so the next frame can pad over any leftover characters. */
    private int lastLen;

    public ProgressBar(Appendable sink) {
        this(sink, true);
    }

    public ProgressBar(Appendable sink, boolean enabled) {
        this.sink = sink;
        this.enabled = enabled;
    }

    /** A bar that renders nothing — for tests and non-interactive runs. */
    public static ProgressBar silent() {
        return new ProgressBar(new StringBuilder(), false);
    }

    /** Begins a run of {@code total} steps under {@code title} and draws the empty bar. */
    public void start(int total, String title) {
        this.total = Math.max(1, total);
        this.current = 0;
        this.title = title == null ? "" : title;
        this.lastLen = 0;
        render("");
    }

    /** Advances one step (capped at the total) and redraws with {@code label}. */
    public void step(String label) {
        current = Math.min(current + 1, total);
        render(label);
    }

    /** Fills the bar, redraws with {@code label}, and terminates the line. */
    public void done(String label) {
        current = total;
        render(label);
        newline();
    }

    private void render(String label) {
        if (!enabled) {
            return;
        }
        int filled = (int) Math.round((double) current / total * WIDTH);
        int pct = (int) Math.round((double) current / total * 100.0);
        StringBuilder sb = new StringBuilder("\r");
        if (!title.isEmpty()) {
            sb.append(title).append(' ');
        }
        sb.append('[');
        for (int i = 0; i < WIDTH; i++) {
            sb.append(i < filled ? '#' : '-');
        }
        sb.append("] ").append(pct).append('%');
        if (label != null && !label.isEmpty()) {
            sb.append(' ').append(label);
        }
        int contentLen = sb.length();
        for (int pad = contentLen; pad < lastLen; pad++) {
            sb.append(' ');
        }
        lastLen = contentLen;
        write(sb.toString());
    }

    private void newline() {
        if (enabled) {
            write(System.lineSeparator());
        }
    }

    private void write(String s) {
        try {
            sink.append(s);
        } catch (java.io.IOException ignored) {
            // Progress is cosmetic; never let a broken sink derail the actual work.
        }
    }
}
