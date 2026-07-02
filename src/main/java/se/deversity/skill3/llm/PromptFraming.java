package se.deversity.skill3.llm;

import java.util.regex.Pattern;

/**
 * Defends the prompt's data framing. {@link Synthesizer} and {@link Verifier} fence
 * untrusted source text between {@code === … ===} marker lines and instruct the model to
 * treat everything inside as data; a fetched page containing its own marker-lookalike
 * line (e.g. {@code === END SOURCES ===} followed by injected instructions) could
 * visually close that fence and escape the untrusted region. Breaking any line-leading
 * run of {@code =} in embedded text makes such a line impossible while leaving the
 * content otherwise untouched.
 */
final class PromptFraming {

    /** A line-leading run of two or more {@code =} — the shape of a frame marker. */
    private static final Pattern MARKER_RUN = Pattern.compile("(?m)^(\\s*)={2,}");

    private PromptFraming() {
    }

    /** {@return {@code text} with any line-leading {@code =}-run collapsed to a single {@code =},
     * so no embedded line can read as a frame marker} */
    static String neutralizeMarkers(String text) {
        return MARKER_RUN.matcher(text).replaceAll("$1=");
    }
}
