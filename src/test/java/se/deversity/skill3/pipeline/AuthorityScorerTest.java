package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorityScorerTest {

    private final AuthorityScorer scorer = new AuthorityScorer(Set.of("modelcontextprotocol.io"));

    @Test
    void authoritativeDomainScoresOne() {
        assertEquals(1.0, scorer.score("https://modelcontextprotocol.io/spec"));
    }

    @Test
    void subdomainOfAuthoritativeScoresOne() {
        assertEquals(1.0, scorer.score("https://docs.modelcontextprotocol.io/x"));
    }

    @Test
    void bloggingPlatformScoresLow() {
        assertEquals(0.2, scorer.score("https://someone.medium.com/post"));
    }

    @Test
    void gistScoresLowEvenThoughOnGithub() {
        assertEquals(0.2, scorer.score("https://gist.github.com/u/abc"));
    }

    @Test
    void githubRepoScoresMid() {
        assertEquals(0.7, scorer.score("https://github.com/org/repo"));
    }

    @Test
    void unknownDomainScoresDefault() {
        assertEquals(0.5, scorer.score("https://example.org/page"));
    }

    @Test
    void unparseableUrlScoresDefault() {
        assertEquals(0.5, scorer.score("not a url"));
    }
}
