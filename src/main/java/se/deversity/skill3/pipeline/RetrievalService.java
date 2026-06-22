package se.deversity.skill3.pipeline;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import se.deversity.skill3.model.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 1: discovery and retrieval. Searches for documentation, fetches each
 * page, and extracts text excerpts, code blocks, a publication date, and an
 * authority score into {@link Source} objects.
 *
 * <p>The scraper "fallback" is realised by simply proceeding with whatever pages
 * were fetched; when too few usable sources are found the caller is warned.
 */
public class RetrievalService {

    private static final int MAX_EXCERPTS = 40;

    private final SearchClient search;
    private final PageFetcher fetcher;
    private final DateExtractor dates;
    private final AuthorityScorer authority;

    public RetrievalService(SearchClient search, PageFetcher fetcher,
                            DateExtractor dates, AuthorityScorer authority) {
        this.search = search;
        this.fetcher = fetcher;
        this.dates = dates;
        this.authority = authority;
    }

    /** {@return retrieved sources for {@code skillName}; may be empty} */
    public List<Source> retrieve(String skillName, int maxResults) throws java.io.IOException {
        List<String> urls = search.search(skillName + " documentation", maxResults);
        List<Source> sources = new ArrayList<>();
        for (String url : urls) {
            try {
                String html = fetcher.fetch(url);
                Document doc = Jsoup.parse(html, url);
                Source s = new Source(url);
                s.title = doc.title();
                s.published = dates.extract(doc);
                s.authority = authority.score(url);
                extractContent(doc, s);
                if (!s.excerpts.isEmpty() || !s.codeBlocks.isEmpty()) {
                    sources.add(s);
                }
            } catch (Exception e) {
                // Skip unreachable / unparseable pages; discovery is best-effort.
                System.err.println("  ! skipped " + url + " (" + e.getMessage() + ")");
            }
        }
        return sources;
    }

    static void extractContent(Document doc, Source s) {
        for (Element code : doc.select("pre")) {
            String text = code.text();
            if (!text.isBlank()) {
                s.codeBlocks.add(text.strip());
            }
        }
        for (Element block : doc.select("h1, h2, h3, p, li")) {
            if (s.excerpts.size() >= MAX_EXCERPTS) {
                break;
            }
            String text = block.text().strip();
            if (text.length() >= 40) {
                s.excerpts.add(text);
            }
        }
    }
}
