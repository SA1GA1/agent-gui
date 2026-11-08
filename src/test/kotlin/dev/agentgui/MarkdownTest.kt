package dev.agentgui
import dev.agentgui.toolwindow.SafeMarkdown
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class MarkdownTest {
    @Test fun `code cards expose copy text while blocking agent supplied copy links`() {
        val code="val value = \"<script>\""
        val html=SafeMarkdown.render("```kotlin\n$code\n```\n\n[unsafe](agent-copy:YWJj)")
        assertTrue(html.contains("Kotlin"))
        assertTrue(html.contains("agent-copy:"))
        assertFalse(html.contains("href=\"agent-copy:YWJj\""))
        assertFalse(html.contains("<script>"))
    }

    @Test fun `renders headings code but never embeds active HTML or remote images`() {
        val html=SafeMarkdown.render("# Hello\n\n```kotlin\nval x = 1\n```\n\n<script>alert(1)</script>\n\n![secret](https://example.invalid/track)")
        assertTrue(html.contains("<h1>Hello</h1>")); assertTrue(html.contains("<pre><code"))
        assertFalse(html.contains("<script>"));assertFalse(html.contains("<img"));assertFalse(html.contains("src="))
    }
}
