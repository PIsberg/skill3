package se.deversity.skill3.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders a SKILL.md into a self-contained dark-theme {@code index.html} preview.
 * Lightweight Markdown handling (headings, fenced code, lists, paragraphs, and the
 * inline span syntax that actually shows up in a SKILL.md: links, inline code, bold,
 * italic) — no external dependencies.
 */
public class WebPreviewGenerator {

    // Inline span patterns, applied to already-HTML-escaped text. Order matters:
    // inline code is handled first so link/emphasis syntax inside a code span is left alone.
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    // Italic only when underscores form a clear pair on word boundaries, so technical
    // identifiers like `_meta` (a lone leading underscore) are never mangled.
    private static final Pattern ITALIC = Pattern.compile("(?<![\\w])_([^_\\n]+)_(?![\\w])");

    public String render(String skillMd) {
        String body = stripFrontmatter(skillMd);
        return TEMPLATE.replace("%%CONTENT%%", toHtml(body));
    }

    static String stripFrontmatter(String md) {
        String s = md.stripLeading();
        if (s.startsWith("---")) {
            int end = s.indexOf("\n---", 3);
            if (end >= 0) {
                int nl = s.indexOf('\n', end + 1);
                return nl >= 0 ? s.substring(nl + 1) : "";
            }
        }
        return md;
    }

    static String toHtml(String md) {
        List<String> out = new ArrayList<>();
        boolean inCode = false;
        boolean inList = false;
        StringBuilder para = new StringBuilder();

        for (String line : md.split("\n", -1)) {
            if (line.strip().startsWith("```")) {
                flushPara(out, para);
                if (!inCode) {
                    closeList(out, inList);
                    inList = false;
                    out.add("<pre><code>");
                } else {
                    out.add("</code></pre>");
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                out.add(escape(line));
                continue;
            }
            String t = line.strip();
            if (t.isEmpty()) {
                flushPara(out, para);
                closeList(out, inList);
                inList = false;
            } else if (t.startsWith("### ")) {
                flushPara(out, para);
                closeList(out, inList);
                inList = false;
                out.add("<h3>" + inline(t.substring(4)) + "</h3>");
            } else if (t.startsWith("## ")) {
                flushPara(out, para);
                closeList(out, inList);
                inList = false;
                out.add("<h2>" + inline(t.substring(3)) + "</h2>");
            } else if (t.startsWith("# ")) {
                flushPara(out, para);
                closeList(out, inList);
                inList = false;
                out.add("<h1>" + inline(t.substring(2)) + "</h1>");
            } else if (t.startsWith("- ")) {
                flushPara(out, para);
                if (!inList) {
                    out.add("<ul>");
                    inList = true;
                }
                out.add("<li>" + inline(t.substring(2)) + "</li>");
            } else {
                if (para.length() > 0) {
                    para.append(' ');
                }
                para.append(inline(t));
            }
        }
        flushPara(out, para);
        closeList(out, inList);
        return String.join("\n", out);
    }

    private static void flushPara(List<String> out, StringBuilder para) {
        if (para.length() > 0) {
            out.add("<p>" + para + "</p>");
            para.setLength(0);
        }
    }

    private static void closeList(List<String> out, boolean inList) {
        if (inList) {
            out.add("</ul>");
        }
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * HTML-escapes {@code s}, then renders inline Markdown spans. Order is deliberate: code
     * spans first (so link/emphasis syntax inside them is left alone), then bold/italic, and
     * links LAST — the link replacement injects {@code target="_blank"}, whose underscore would
     * otherwise interfere with italic matching (e.g. the {@code _Created with ..._} footer).
     */
    static String inline(String s) {
        String h = escape(s);
        h = INLINE_CODE.matcher(h).replaceAll("<code>$1</code>");
        h = BOLD.matcher(h).replaceAll("<strong>$1</strong>");
        h = ITALIC.matcher(h).replaceAll("<em>$1</em>");
        h = LINK.matcher(h).replaceAll("<a href=\"$2\" target=\"_blank\" rel=\"noopener noreferrer\">$1</a>");
        return h;
    }

    private static final String TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Skill Preview</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800&display=swap" rel="stylesheet">
            <style>
              :root {
                --bg: hsl(220, 20%, 10%);
                --card: hsla(220, 15%, 16%, 0.7);
                --teal: hsl(180, 100%, 45%);
                --magenta: hsl(320, 100%, 60%);
                --text: hsl(220, 15%, 90%);
              }
              * { box-sizing: border-box; }
              body {
                margin: 0; padding: 3rem 1rem; background: var(--bg); color: var(--text);
                font-family: 'Inter', system-ui, sans-serif; line-height: 1.6;
              }
              main {
                max-width: 820px; margin: 0 auto; padding: 2.5rem;
                background: var(--card); border-radius: 18px;
                backdrop-filter: blur(12px);
                border: 1px solid hsla(220, 15%, 40%, 0.25);
              }
              h1 {
                font-weight: 800; font-size: 2.2rem; margin-top: 0;
                background: linear-gradient(90deg, var(--teal), var(--magenta));
                -webkit-background-clip: text; background-clip: text; color: transparent;
              }
              h2 { color: var(--teal); margin-top: 2rem; font-weight: 600; }
              h3 { color: var(--magenta); font-weight: 600; }
              pre {
                background: hsl(220, 25%, 7%); padding: 1rem 1.2rem; border-radius: 12px;
                overflow-x: auto; border: 1px solid hsla(180, 100%, 45%, 0.2);
              }
              code { font-family: 'SFMono-Regular', Consolas, monospace; font-size: 0.9rem; }
              a { color: var(--teal); }
            </style>
            </head>
            <body>
            <main>
            %%CONTENT%%
            </main>
            </body>
            </html>
            """;
}
