package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.skill3.model.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCorpusTest {

    private static final String TWO_SOURCES = """
            A leading comment before the first marker is ignored.

            === SOURCE ===
            url: https://modelcontextprotocol.io/spec
            title: MCP Spec
            date: 2026-03-01

            # Resources
            The _meta field is now accepted on every request in the 2026-03 revision.

            ```
            client.call("tools/list");
            ```

            === SOURCE ===
            url: https://github.com/org/repo
            date: 2026-04-01

            A sufficiently long paragraph describing the new release behaviour and flags.
            """;

    @Test
    void searchReturnsEveryUrlInOrderIgnoringQueryAndCount() throws IOException {
        FileCorpus corpus = FileCorpus.parse(TWO_SOURCES);

        // The file is the curated result set: query and count are intentionally ignored.
        assertEquals(
                List.of("https://modelcontextprotocol.io/spec", "https://github.com/org/repo"),
                corpus.search("anything", 1));
    }

    @Test
    void fetchSynthesizesHtmlWithTitleDateHeadingCodeAndParagraph() throws IOException {
        FileCorpus corpus = FileCorpus.parse(TWO_SOURCES);

        String html = corpus.fetch("https://modelcontextprotocol.io/spec");

        assertTrue(html.contains("<title>MCP Spec</title>"));
        assertTrue(html.contains("<meta name=\"date\" content=\"2026-03-01\">"));
        assertTrue(html.contains("<h1>Resources</h1>"));
        // Quotes are HTML-escaped in the synthesized markup; Jsoup decodes them back downstream.
        assertTrue(html.contains("<pre>client.call(&quot;tools/list&quot;);</pre>"));
        assertTrue(html.contains("<p>The _meta field is now accepted on every request"));
    }

    @Test
    void rawHtmlBodyIsReusedAsIs() throws IOException {
        FileCorpus corpus = FileCorpus.parse("""
                === SOURCE ===
                url: https://example.com/page

                <h2>Heading</h2><p>An HTML paragraph that is long enough to be an excerpt later.</p>
                """);

        String html = corpus.fetch("https://example.com/page");
        assertTrue(html.contains("<h2>Heading</h2>"));
        assertTrue(html.contains("<p>An HTML paragraph"));
    }

    @Test
    void plainTextSpecialCharsAreEscaped() throws IOException {
        FileCorpus corpus = FileCorpus.parse("""
                === SOURCE ===
                url: https://example.com/p

                Generics like List<String> & Map are escaped so Jsoup keeps the text intact.
                """);

        String html = corpus.fetch("https://example.com/p");
        assertTrue(html.contains("List&lt;String&gt; &amp; Map"));
    }

    @Test
    void missingUrlHeaderThrows() {
        IOException e = assertThrows(IOException.class, () -> FileCorpus.parse("""
                === SOURCE ===
                title: no url here

                body
                """));
        assertTrue(e.getMessage().contains("url"));
    }

    @Test
    void emptyOrMarkerlessFileThrows() {
        assertThrows(IOException.class, () -> FileCorpus.parse("just some text, no markers"));
        assertThrows(IOException.class, () -> FileCorpus.parse(""));
    }

    @Test
    void duplicateUrlKeepsFirstBody() throws IOException {
        FileCorpus corpus = FileCorpus.parse("""
                === SOURCE ===
                url: https://example.com/dup

                First body kept for this url even though it appears twice in the file.

                === SOURCE ===
                url: https://example.com/dup

                Second body for the same url should be dropped on load.
                """);

        assertEquals(List.of("https://example.com/dup"), corpus.search("q", 5));
        assertTrue(corpus.fetch("https://example.com/dup").contains("First body kept"));
    }

    @Test
    void fetchUnknownUrlThrows() throws IOException {
        FileCorpus corpus = FileCorpus.parse(TWO_SOURCES);
        assertThrows(IOException.class, () -> corpus.fetch("https://nope"));
    }

    @Test
    void loadReadsFromDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("corpus.txt");
        Files.writeString(file, TWO_SOURCES);
        assertEquals(2, FileCorpus.load(file).search("q", 5).size());
    }

    /** End-to-end through the real RetrievalService: the corpus enriches into Sources. */
    @Test
    void flowsThroughRetrievalServiceIntoEnrichedSources() throws IOException {
        FileCorpus corpus = FileCorpus.parse(TWO_SOURCES);
        RetrievalService service = new RetrievalService(
                corpus, corpus, new DateExtractor(), new AuthorityScorer(Set.of()));

        List<Source> sources = service.retrieve(List.of("ignored query"), 5);

        assertEquals(2, sources.size());
        Source mcp = sources.stream()
                .filter(s -> s.url.contains("modelcontextprotocol")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 3, 1), mcp.published);
        assertEquals(0.5, mcp.authority); // modelcontextprotocol.io: not authoritative unless configured
        assertFalse(mcp.codeBlocks.isEmpty());
        assertFalse(mcp.excerpts.isEmpty());
    }
}
