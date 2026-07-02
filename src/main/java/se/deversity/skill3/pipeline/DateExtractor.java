package se.deversity.skill3.pipeline;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a publication date from an HTML document. The whole freshness
 * mechanism depends on this; when no date is found the source is left undated and
 * handled conservatively downstream (low recency weight; excluded under strict).
 */
public class DateExtractor {

    private static final String[] META_SELECTORS = {
            "meta[property=article:published_time]",
            "meta[itemprop=datePublished]",
            "meta[name=date]",
            "meta[name=publish-date]",
            "meta[property=og:published_time]",
    };

    /**
     * Update timestamp, not a publication date: an old article re-touched yesterday must not
     * score as fresh post-cutoff evidence, so this is consulted only after every true
     * published-date signal (meta, {@code <time>}, JSON-LD) has failed.
     */
    private static final String UPDATED_TIME_SELECTOR = "meta[property=og:updated_time]";

    /** Non-ISO shapes seen in the wild: RFC-1123 (`Tue, 21 May 2026 …`), slashes, unpadded ISO. */
    private static final DateTimeFormatter[] FALLBACK_FORMATS = {
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
    };

    private static final Pattern JSON_LD_DATE =
            Pattern.compile("\"datePublished\"\\s*:\\s*\"([^\"]+)\"");

    /** {@return the publication date, or {@code null} if none could be determined} */
    public LocalDate extract(Document doc) {
        if (doc == null) {
            return null;
        }
        for (String sel : META_SELECTORS) {
            Element e = doc.selectFirst(sel);
            if (e != null) {
                LocalDate d = parse(e.attr("content"));
                if (d != null) {
                    return d;
                }
            }
        }
        Element time = doc.selectFirst("time[datetime]");
        if (time != null) {
            LocalDate d = parse(time.attr("datetime"));
            if (d != null) {
                return d;
            }
        }
        for (Element script : doc.select("script[type=application/ld+json]")) {
            Matcher m = JSON_LD_DATE.matcher(script.data());
            if (m.find()) {
                LocalDate d = parse(m.group(1));
                if (d != null) {
                    return d;
                }
            }
        }
        Element updated = doc.selectFirst(UPDATED_TIME_SELECTOR);
        if (updated != null) {
            return parse(updated.attr("content")); // last resort; see UPDATED_TIME_SELECTOR
        }
        return null;
    }

    static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length())));
        } catch (Exception ignored) {
            // fall through
        }
        for (DateTimeFormatter format : FALLBACK_FORMATS) {
            try {
                return LocalDate.parse(s, format);
            } catch (Exception ignored) {
                // try the next shape
            }
        }
        return null;
    }
}
