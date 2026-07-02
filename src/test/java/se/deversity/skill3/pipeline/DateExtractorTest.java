package se.deversity.skill3.pipeline;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateExtractorTest {

    private final DateExtractor extractor = new DateExtractor();

    @Test
    void readsArticlePublishedMeta() {
        String html = "<html><head>"
                + "<meta property='article:published_time' content='2026-05-21T10:00:00Z'>"
                + "</head><body></body></html>";
        assertEquals(LocalDate.of(2026, 5, 21), extractor.extract(Jsoup.parse(html)));
    }

    @Test
    void readsTimeElement() {
        String html = "<html><body><time datetime='2025-03-02'>March</time></body></html>";
        assertEquals(LocalDate.of(2025, 3, 2), extractor.extract(Jsoup.parse(html)));
    }

    @Test
    void readsJsonLdDatePublished() {
        String html = "<html><head><script type='application/ld+json'>"
                + "{\"datePublished\":\"2024-01-15T00:00:00Z\"}"
                + "</script></head><body></body></html>";
        assertEquals(LocalDate.of(2024, 1, 15), extractor.extract(Jsoup.parse(html)));
    }

    @Test
    void returnsNullWhenNoDate() {
        assertNull(extractor.extract(Jsoup.parse("<html><body><p>no date</p></body></html>")));
    }

    @Test
    void parseHandlesDateOnlyOffsetAndGarbage() {
        assertEquals(LocalDate.of(2026, 5, 21), DateExtractor.parse("2026-05-21"));
        assertEquals(LocalDate.of(2026, 5, 21), DateExtractor.parse("2026-05-21T08:00:00Z"));
        assertNull(DateExtractor.parse("not a date"));
        assertNull(DateExtractor.parse(""));
    }

    @Test
    void parseHandlesCommonNonIsoShapes() {
        assertEquals(LocalDate.of(2026, 5, 21), DateExtractor.parse("Thu, 21 May 2026 10:00:00 GMT"));
        assertEquals(LocalDate.of(2026, 5, 21), DateExtractor.parse("2026/05/21"));
        assertEquals(LocalDate.of(2026, 5, 1), DateExtractor.parse("2026/5/1"));
        assertEquals(LocalDate.of(2026, 5, 1), DateExtractor.parse("2026-5-1"));
    }

    @Test
    void publishedDateBeatsUpdatedTime() {
        // An old article re-touched recently must keep its publication date.
        String html = "<html><head>"
                + "<meta property='og:updated_time' content='2026-06-30T10:00:00Z'>"
                + "<meta property='article:published_time' content='2024-02-01T10:00:00Z'>"
                + "</head><body></body></html>";
        assertEquals(LocalDate.of(2024, 2, 1), extractor.extract(Jsoup.parse(html)));
    }

    @Test
    void jsonLdBeatsUpdatedTime() {
        String html = "<html><head>"
                + "<meta property='og:updated_time' content='2026-06-30T10:00:00Z'>"
                + "<script type='application/ld+json'>{\"datePublished\":\"2024-01-15T00:00:00Z\"}</script>"
                + "</head><body></body></html>";
        assertEquals(LocalDate.of(2024, 1, 15), extractor.extract(Jsoup.parse(html)));
    }

    @Test
    void updatedTimeIsUsedOnlyAsLastResort() {
        String html = "<html><head>"
                + "<meta property='og:updated_time' content='2026-06-30T10:00:00Z'>"
                + "</head><body></body></html>";
        assertEquals(LocalDate.of(2026, 6, 30), extractor.extract(Jsoup.parse(html)));
    }
}
