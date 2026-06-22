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
    void whitespaceIgnoredForAgreement() {
        Source a = source("https://a.com", 0.5, "a( b )");
        Source b = source("https://b.com", 0.5, "a(b)");
        new ConsensusValidator(2).annotate(List.of(a, b));
        assertEquals(2, a.consensusCount);
    }

    @Test
    void minAgreementOneKeepsLonelyBlock() {
        Source c = source("https://c.com", 0.3, "alone();");
        new ConsensusValidator(1).annotate(List.of(c));
        assertTrue(c.codeBlocks.contains("alone();"));
    }
}
