package dev.agentgui

import dev.agentgui.toolwindow.ChatPresentation
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ChatPresentationTest {
    @Test fun `autocomplete uses the token at caret and ignores inline email`() {
        assertEquals("file", ChatPresentation.token("Read @file please", 10)?.query)
        assertEquals('@', ChatPresentation.token("Read @file please", 10)?.marker)
        assertNull(ChatPresentation.token("a@example.com", 13))
        assertEquals("com", ChatPresentation.token("/com", 4)?.query)
        assertNull(ChatPresentation.token("/compact ", 9))
    }
    @Test fun `prompt history includes only user text and attachment fallback`() {
        val items=Json.parseToJsonElement("""[{"id":"a","type":"agentMessage","text":"reply"},{"id":"u","type":"userMessage","content":[{"type":"text","text":"find <bug>"},{"type":"localImage","path":"secret"}]}]""").jsonArray
        assertEquals(listOf("u" to "find <bug>\n[Attachment]"), ChatPresentation.prompts(items))
    }
    @Test fun `message anchors and HTML never execute user content`() {
        val item=Json.parseToJsonElement("""{"id":"x\" onload=\"oops","type":"userMessage","content":[{"type":"text","text":"<script>bad</script>"}]}""").jsonObject
        val rendered=ChatPresentation.renderItem(item)
        assertFalse(rendered.contains("<script>"))
        assertFalse(rendered.contains("onload=\"oops"))
        assertTrue(rendered.contains("&lt;script&gt;"))
        assertTrue(rendered.contains(ChatPresentation.anchor(item["id"]!!.jsonPrimitive.content)))
    }
}
