package dev.agentgui
import dev.agentgui.sessions.transcriptWindow
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class TranscriptWindowTest {
    private fun item(id: String)=buildJsonObject { put("id",id); put("type","agentMessage"); put("text",id) }
    @Test fun `revealing an old prompt preserves new streamed output within bounded window`() {
        val window=transcriptWindow(listOf(item("old")),(1..600).map { item("live$it") })
        assertEquals(500,window.size)
        assertEquals("old",window.first()["id"]!!.jsonPrimitive.content)
        assertEquals("live600",window.last()["id"]!!.jsonPrimitive.content)
    }
    @Test fun `overlapping historical and live items keep latest content once`() {
        val window=transcriptWindow(listOf(item("a"),item("b")),listOf(item("b"),item("c")))
        assertEquals(listOf("a","b","c"),window.map { it["id"]!!.jsonPrimitive.content })
    }
}
