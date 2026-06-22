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
}
