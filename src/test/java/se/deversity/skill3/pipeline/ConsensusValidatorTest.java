package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import se.deversity.skill3.model.Source;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsensusValidatorTest {

    private Source source(String url, double authority, String... code) {
        Source s = new Source(url);
        s.authority = authority;
        for (String c : code) {
            s.codeBlocks.add(c);
        }
        return s;
    }

    @Test
    void agreedBlockAcrossHostsIsKept() {
        Source a = source("https://a.com/x", 0.5, "let x = 1;");
        Source b = source("https://b.com/y", 0.5, "let x = 1;"); // same after normalization

        new ConsensusValidator(2).annotate(List.of(a, b));

        assertTrue(a.codeBlocks.contains("let x = 1;"));
        assertEquals(2, a.consensusCount);
    }

    @Test
    void lonelyLowAuthorityBlockIsDropped() {
        Source c = source("https://c.com", 0.5, "rare();");
        new ConsensusValidator(2).annotate(List.of(c));
        assertTrue(c.codeBlocks.isEmpty());
        assertEquals(0, c.consensusCount);
    }

    @Test
    void highAuthorityBlockKeptWithoutAgreement() {
        Source d = source("https://d.com", 0.9, "official();");
        new ConsensusValidator(2).annotate(List.of(d));
        assertTrue(d.codeBlocks.contains("official();"));
    }

    @Test
    void whitespaceRunsCollapseForAgreement() {
        // Formatting drift (extra spaces, line wrapping) still matches...
        Source a = source("https://a.com", 0.5, "a(  b );\n  c();");
        Source b = source("https://b.com", 0.5, "a( b ); c();");
        new ConsensusValidator(2).annotate(List.of(a, b));
        assertEquals(2, a.consensusCount);
    }

    @Test
    void tokenBoundariesAreNotErasedByNormalization() {
        // ...but snippets that differ once whitespace is gone must not falsely agree
        // (the old strip-all normalization mapped both of these to "a(b)c()").
        Source a = source("https://a.com", 0.5, "a(b)c()");
        Source b = source("https://b.com", 0.5, "a(b) c()");
        new ConsensusValidator(2).annotate(List.of(a, b));
        assertEquals(0, a.consensusCount);
        assertTrue(a.codeBlocks.isEmpty());
    }

    @Test
    void midAuthoritySourceDoesNotBypassConsensus() {
        // github.com scores exactly 0.7 ("standard repository") — a lone block from it
        // must still need cross-source agreement; only per-run authoritative hosts skip it.
        Source gh = source("https://github.com/org/repo", 0.7, "unverified();");
        new ConsensusValidator(2).annotate(List.of(gh));
        assertTrue(gh.codeBlocks.isEmpty());
    }

    @Test
    void minAgreementOneKeepsLonelyBlock() {
        Source c = source("https://c.com", 0.3, "alone();");
        new ConsensusValidator(1).annotate(List.of(c));
        assertTrue(c.codeBlocks.contains("alone();"));
    }
}
