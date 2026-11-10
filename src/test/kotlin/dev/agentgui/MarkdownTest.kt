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

    @Test fun `renders latex locally while retaining exact formula in accessible fallback`() {
        val formula="W(p)=\\frac{Y(p)}{X(p)}=\\frac{1{,}53p^2+16{,}73p+7{,}93}{0{,}43p^4+p^3+1{,}93p^2}"
        val html=SafeMarkdown.render("\\[$formula\\] and \\(x^2\\) and ${'$'}y${'$'}")
        assertEquals(3,Regex("src='agent-math:").findAll(html).count())
        assertTrue(html.contains(SafeMarkdown.escape(formula)))
    }
    @Test fun `math leaves code currency and unsafe formulas as readable text`() {
        val html=SafeMarkdown.render("Cost ${'$'}20 and ${'$'}30. `\\(x\\)`\n\n```tex\n\\[y\\]\n```\n\n\\[\\includegraphics{https://bad.invalid/a}\\]")
        assertFalse(html.contains("src='agent-math:"))
        assertTrue(html.contains("includegraphics"))
        assertTrue(html.contains("Cost ${'$'}20 and ${'$'}30"))
    }
}
