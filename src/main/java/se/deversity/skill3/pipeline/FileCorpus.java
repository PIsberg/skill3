package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline discovery from a user-curated input file — a no-network alternative to
 * {@link BraveSearchClient}. The file is the pre-curated result set: instead of
 * searching the web and scraping pages, the user pastes the relevant documents
 * (URL, optional title/date, and body content) into one file.
 *
 * <p>It implements both discovery seams so it drops straight into the existing
 * pipeline: {@link SearchClient} yields every source URL in the file and
 * {@link PageFetcher} replays each document's body. Everything downstream — date
 * extraction, authority scoring, content extraction, consensus, freshness and
 * synthesis — runs unchanged, exactly as it would for Brave-discovered pages.
 *
 * <h2>File format</h2>
 * Documents are separated by a line that reads exactly {@code === SOURCE ===}.
 * Each document begins with {@code key: value} header lines ({@code url} is
 * required; {@code title} and {@code date} as {@code yyyy-MM-dd} are optional),
 * then a blank line, then the body:
 *
 * <pre>{@code
 * === SOURCE ===
 * url: https://modelcontextprotocol.io/specification
 * title: MCP Specification 2026-03
 * date: 2026-03-01
 *
 * Body content here. Plain text, Markdown (```fenced``` code blocks and
 * # headings are recognised), or raw HTML are all accepted.
 *
 * === SOURCE ===
 * url: https://example.com/another
 * ...
 * }</pre>
 *
 * <p>Any text before the first {@code === SOURCE ===} marker is ignored, so the
 * file may start with a comment. A body line that needs to read literally
 * {@code === SOURCE ===} is the one thing the format cannot represent.
 */
public final class FileCorpus implements SearchClient, PageFetcher {

    /** A line that, trimmed, equals this starts a new document. */
    static final String DELIMITER = "=== SOURCE ===";

    /** Recognised header keys; any other {@code key: value} line ends the header block. */
    private static final Pattern HEADER =
            Pattern.compile("^\\s*(url|title|date)\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    /** Cheap heuristic: does the body already contain block-level HTML to reuse as-is? */
    private static final Pattern HTML_TAG = Pattern.compile(
            "(?is)<\\s*(p|div|pre|h[1-6]|ul|ol|li|article|section|table|main|body|html|blockquote)\\b");

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.*)$");

    /** Synthesized HTML body per source URL, in first-seen order. */
    private final Map<String, String> htmlByUrl;

    private FileCorpus(Map<String, String> htmlByUrl) {
        this.htmlByUrl = htmlByUrl;
    }

    /** Reads and parses a corpus file. */
    public static FileCorpus load(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Parses corpus text (exposed for testing without a temp file). */
    static FileCorpus parse(String text) throws IOException {
        Map<String, String> byUrl = new LinkedHashMap<>();
        for (Doc doc : splitRecords(text)) {
            if (byUrl.putIfAbsent(doc.url, doc.toHtml()) != null) {
                System.err.println("  ! duplicate url in input file, keeping first: " + doc.url);
            }
        }
        if (byUrl.isEmpty()) {
            throw new IOException("No sources found in input file; expected blocks separated by '"
                    + DELIMITER + "', each with a 'url:' header.");
        }
        return new FileCorpus(byUrl);
    }

    /**
     * {@return every source URL in the file, in order} The file is already the
     * curated result set, so the whole corpus is returned regardless of
     * {@code query} or {@code count} — discovery is the user's selection, not a
     * live search to be ranked or capped here.
     */
    @Override
    public List<String> search(String query, int count) {
        return new ArrayList<>(htmlByUrl.keySet());
    }

    /** The file is the curated result set, so the pipeline should not plan or run live queries. */
    @Override
    public boolean isCuratedCorpus() {
        return true;
    }

    /** {@return the stored body for {@code url}, as synthesized HTML} */
    @Override
    public String fetch(String url) throws IOException {
        String html = htmlByUrl.get(url);
        if (html == null) {
            throw new IOException("No such source in input file: " + url);
        }
        return html;
    }

    // --- parsing -----------------------------------------------------------

    private static List<Doc> splitRecords(String text) throws IOException {
        List<Doc> records = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int i = 0;
        // Skip any preamble before the first delimiter (allows a leading comment).
        while (i < lines.length && !stripCr(lines[i]).strip().equals(DELIMITER)) {
            i++;
        }
        while (i < lines.length) {
            i++; // consume the delimiter line
            List<String> block = new ArrayList<>();
            while (i < lines.length && !stripCr(lines[i]).strip().equals(DELIMITER)) {
                block.add(stripCr(lines[i]));
                i++;
            }
            records.add(parseRecord(block));
        }
        return records;
    }

    private static Doc parseRecord(List<String> block) throws IOException {
        String url = null;
        String title = null;
        String date = null;
        int b = 0;
        for (; b < block.size(); b++) {
            Matcher m = HEADER.matcher(block.get(b));
            if (!m.matches()) {
                break;
            }
            String value = m.group(2).strip();
            switch (m.group(1).toLowerCase(Locale.ROOT)) {
                case "url" -> url = value;
                case "title" -> title = value;
                case "date" -> date = value;
                default -> { /* unreachable: pattern only matches known keys */ }
            }
        }
        if (url == null || url.isBlank()) {
            throw new IOException("Input-file source is missing a 'url:' header.");
        }
        // Skip a single blank line separating headers from the body.
        if (b < block.size() && block.get(b).isBlank()) {
            b++;
        }
        String body = String.join("\n", block.subList(b, block.size())).strip();
        return new Doc(url, title, date, body);
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    /** One parsed document; renders itself into the minimal HTML the pipeline expects. */
    private record Doc(String url, String title, String date, String body) {

        String toHtml() {
            StringBuilder head = new StringBuilder("<head><title>")
                    .append(escape(title != null && !title.isBlank() ? title : url))
                    .append("</title>");
            if (date != null && !date.isBlank()) {
                head.append("<meta name=\"date\" content=\"").append(escape(date)).append("\">");
            }
            head.append("</head>");
            return "<html>" + head + "<body>" + renderBody() + "</body></html>";
        }

        private String renderBody() {
            if (HTML_TAG.matcher(body).find()) {
                return body; // already HTML — reuse exactly as a scraped page would be
            }
            return markdownToHtml(body);
        }
    }

    /** Minimal Markdown/plain-text rendering: fenced code, ATX headings, paragraphs. */
    private static String markdownToHtml(String body) {
        StringBuilder out = new StringBuilder();
        List<String> para = new ArrayList<>();
        boolean inFence = false;
        StringBuilder code = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (line.strip().startsWith("```")) {
                if (inFence) {
                    out.append("<pre>").append(escape(code.toString())).append("</pre>");
                    code.setLength(0);
                } else {
                    flushPara(out, para);
                }
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                if (code.length() > 0) {
                    code.append('\n');
                }
                code.append(line);
                continue;
            }
            if (line.isBlank()) {
                flushPara(out, para);
                continue;
            }
            Matcher h = HEADING.matcher(line.strip());
            if (h.matches()) {
                flushPara(out, para);
                String tag = "h" + h.group(1).length();
                out.append('<').append(tag).append('>').append(escape(h.group(2).strip()))
                        .append("</").append(tag).append('>');
            } else {
                para.add(line.strip());
            }
        }
        if (inFence && code.length() > 0) { // unterminated fence — keep what we have
            out.append("<pre>").append(escape(code.toString())).append("</pre>");
        }
        flushPara(out, para);
        return out.toString();
    }

    private static void flushPara(StringBuilder out, List<String> para) {
        if (!para.isEmpty()) {
            out.append("<p>").append(escape(String.join(" ", para))).append("</p>");
            para.clear();
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
