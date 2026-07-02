package se.deversity.skill3.pipeline;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.Nullable;
import se.deversity.skill3.model.Source;
import se.deversity.vibetags.annotations.AIThreadSafe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Phase 1: discovery and retrieval. Searches for documentation, fetches each
 * page, and extracts text excerpts, code blocks, a publication date, and an
 * authority score into {@link Source} objects.
 *
 * <p>The per-URL fetches run concurrently on virtual threads by default — they are
 * blocking I/O and independent — while result aggregation happens on the calling
 * thread, so no {@link Source} is ever shared between workers. Input order is
 * preserved. When {@code sequential} is set the same fetches run one at a time on
 * the calling thread (no executor); this is an opt-in mode for callers that want to
 * be gentle on a rate-limited backend or need fully deterministic timing.
 *
 * <p>The scraper "fallback" is realised by simply proceeding with whatever pages
 * were fetched; when too few usable sources are found the caller is warned.
 */
@AIThreadSafe(strategy = AIThreadSafe.Strategy.IMMUTABLE,
        note = "Collaborators (PageFetcher/HttpClient, DateExtractor, AuthorityScorer) are "
                + "stateless/immutable; each fetch task builds its own Source and results are "
                + "merged on the caller thread. Keep it that way — do not share mutable state "
                + "between fetch tasks. The opt-in `sequential` mode only removes concurrency "
                + "(fetches run on the caller thread); it cannot weaken the invariant — serial "
                + "execution is strictly safer than the parallel default it replaces.")
public class RetrievalService {

    private static final int MAX_EXCERPTS = 40;
    private static final int MAX_CODE_BLOCKS = 40;
    /** Per-block cap; a single giant <pre> (minified bundle, data dump) is not evidence. */
    private static final int MAX_CODE_BLOCK_CHARS = 8000;

    private final SearchClient search;
    private final PageFetcher fetcher;
    private final DateExtractor dates;
    private final AuthorityScorer authority;
    /** When true, fetch pages one at a time on the caller thread instead of fanning out. */
    private final boolean sequential;

    /** Builds a service that fetches pages concurrently (the default). */
    public RetrievalService(SearchClient search, PageFetcher fetcher,
                            DateExtractor dates, AuthorityScorer authority) {
        this(search, fetcher, dates, authority, false);
    }

    public RetrievalService(SearchClient search, PageFetcher fetcher,
                            DateExtractor dates, AuthorityScorer authority, boolean sequential) {
        this.search = search;
        this.fetcher = fetcher;
        this.dates = dates;
        this.authority = authority;
        this.sequential = sequential;
    }

    /** {@return retrieved sources for the single default query {@code skillName documentation}} */
    public List<Source> retrieve(String skillName, int maxResults) throws IOException {
        return fetchAll(search.search(skillName + " documentation", maxResults));
    }

    /**
     * Runs every planned query, merges and de-duplicates the result URLs (preserving
     * first-seen order), and fetches them concurrently.
     *
     * @return retrieved sources across all queries; may be empty
     */
    public List<Source> retrieve(List<String> queries, int maxResultsPerQuery) throws IOException {
        Set<String> urls = new LinkedHashSet<>();
        for (String query : queries) {
            urls.addAll(search.search(query, maxResultsPerQuery));
        }
        return fetchAll(new ArrayList<>(urls));
    }

    private List<Source> fetchAll(List<String> urls) throws IOException {
        List<FetchResult> results = sequential ? fetchSerially(urls) : fetchConcurrently(urls);

        // Aggregate on this thread only — workers never touch shared state. Iterating in
        // submission order keeps the output deterministic (URL order in, order out).
        List<Source> sources = new ArrayList<>();
        for (FetchResult result : results) {
            if (result.source() != null) {
                sources.add(result.source());
            } else if (result.skipMessage() != null) {
                System.err.println("  ! " + result.skipMessage());
            }
        }
        return sources;
    }

    /** Default path: fan out the blocking fetches over virtual threads; close() joins them all. */
    private List<FetchResult> fetchConcurrently(List<String> urls) throws IOException {
        List<Future<FetchResult>> futures = new ArrayList<>(urls.size());
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String url : urls) {
                futures.add(pool.submit(() -> fetchOne(url)));
            }
        }
        List<FetchResult> results = new ArrayList<>(futures.size());
        for (Future<FetchResult> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                System.err.println("  ! fetch task failed: " + e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Retrieval interrupted", e);
            }
        }
        return results;
    }

    /** Opt-in path: fetch one URL at a time on the caller thread — no concurrency at all. */
    private List<FetchResult> fetchSerially(List<String> urls) {
        List<FetchResult> results = new ArrayList<>(urls.size());
        for (String url : urls) {
            results.add(fetchOne(url));
        }
        return results;
    }

    /** Outcome of one page fetch: a populated source, or a skip message — never both. */
    private record FetchResult(@Nullable Source source, @Nullable String skipMessage) {
    }

    private FetchResult fetchOne(String url) {
        try {
            String html = fetcher.fetch(url);
            Document doc = Jsoup.parse(html, url);
            Source s = new Source(url);
            s.title = doc.title();
            s.published = dates.extract(doc);
            s.authority = authority.score(url);
            extractContent(doc, s);
            if (!s.excerpts.isEmpty() || !s.codeBlocks.isEmpty()) {
                return new FetchResult(s, null);
            }
            return new FetchResult(null, null); // fetched but no usable content
        } catch (Exception e) {
            // Skip unreachable / unparseable pages; discovery is best-effort.
            return new FetchResult(null, "skipped " + url + " (" + e.getMessage() + ")");
        }
    }

    static void extractContent(Document doc, Source s) {
        // Drop site chrome before extracting, so navigation, sidebars, cookie banners and
        // scripts don't crowd out the real documentation (fewer tokens, less noise). `header`
        // is intentionally NOT removed — some docs put the page's <h1> inside it.
        doc.select("script, style, noscript, nav, aside, footer, form,"
                + " [role=navigation], [role=banner], [role=contentinfo],"
                + " .sidebar, .toc, .breadcrumb, .cookie-banner, .cookie-consent").remove();
        for (Element code : doc.select("pre")) {
            if (s.codeBlocks.size() >= MAX_CODE_BLOCKS) {
                break; // capped like excerpts: a pathological page must not balloon memory/tokens
            }
            String text = code.text();
            if (!text.isBlank()) {
                s.codeBlocks.add(truncate(text.strip(), MAX_CODE_BLOCK_CHARS));
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

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
