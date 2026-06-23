package se.deversity.skill3.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WebPreviewGeneratorTest {

    private final WebPreviewGenerator gen = new WebPreviewGenerator();

    @Test
    void stripsFrontmatter() {
        assertEquals("# H", WebPreviewGenerator.stripFrontmatter("---\nname: x\n---\n# H").strip());
    }

    @Test
    void rendersHeadingsAndCode() {
        String html = WebPreviewGenerator.toHtml("# Title\n\nSome text.\n\n```\ncode();\n```");
        assertTrue(html.contains("<h1>Title</h1>"));
        assertTrue(html.contains("<p>Some text.</p>"));
        assertTrue(html.contains("<pre><code>"));
    }

    @Test
    void escapesHtmlInProse() {
        String html = WebPreviewGenerator.toHtml("a <b>tag</b> here");
        assertTrue(html.contains("&lt;b&gt;"));
        assertFalse(html.contains("<b>"));
    }

    @Test
    void rendersFullDocument() {
        String html = gen.render("---\nname: x\n---\n# Title\n\nbody");
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<h1>Title</h1>"));
    }

    @Test
    void rendersBulletLists() {
        String html = WebPreviewGenerator.toHtml("Intro\n\n- one\n- two\n");
        assertTrue(html.contains("<ul>"));
        assertTrue(html.contains("<li>one</li>"));
        assertTrue(html.contains("</ul>"));
    }

    @Test
    void rendersInlineLinksCodeBoldAndItalic() {
        String html = WebPreviewGenerator.toHtml(
                "See [the spec](https://example.com/spec) for **big** `_meta` and _emphasis_.");
        assertTrue(html.contains("<a href=\"https://example.com/spec\" target=\"_blank\" rel=\"noopener noreferrer\">the spec</a>"));
        assertTrue(html.contains("<strong>big</strong>"));
        assertTrue(html.contains("<code>_meta</code>"));
        assertTrue(html.contains("<em>emphasis</em>"));
    }

    @Test
    void rendersTheItalicProvenanceFooterAsALink() {
        String html = WebPreviewGenerator.toHtml(
                "_Created with [skill3](https://github.com/PIsberg/skill3)._");
        assertTrue(html.contains("<a href=\"https://github.com/PIsberg/skill3\""));
        assertTrue(html.contains("<em>"));
    }

    @Test
    void doesNotItalicizeLoneLeadingUnderscoreIdentifiers() {
        // `_meta` appears all over MCP docs — a single leading underscore must stay literal.
        String html = WebPreviewGenerator.toHtml("The _meta field is new and the _internal flag too.");
        assertFalse(html.contains("<em>"));
        assertTrue(html.contains("_meta field"));
    }

    @Test
    void doesNotRenderMarkdownInsideCodeBlocks() {
        String html = WebPreviewGenerator.toHtml("```\n[x](y) **b**\n```");
        assertFalse(html.contains("<a href"));
        assertFalse(html.contains("<strong>"));
    }
}
