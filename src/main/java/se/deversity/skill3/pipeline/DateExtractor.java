package se.deversity.skill3.pipeline;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
            "meta[property=og:updated_time]",
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
            return null;
        }
    }
}
