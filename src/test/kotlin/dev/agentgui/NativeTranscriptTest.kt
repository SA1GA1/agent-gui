package dev.agentgui
import dev.agentgui.toolwindow.NativeTranscript
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities
class NativeTranscriptTest {
    @Test fun `native bubbles stay bounded while history can reveal an older prompt`() {
        SwingUtilities.invokeAndWait {
            val view=NativeTranscript().apply { setSize(400,500) }
            val items=JsonArray((1..160).map { buildJsonObject { put("id","$it");put("type","agentMessage");put("text","Message $it") } })
            view.showItems("chat",items,"Ready")
            assertTrue(view.containsMessage("160"))
            assertFalse(view.containsMessage("1"))
            view.revealMessage("1")
            assertTrue(view.containsMessage("1"))
            assertTrue(view.containsMessage("160"))
            assertTrue(view.componentCount<=100)
            assertTrue(view.preferredSize.height>0)
        }
    }
    @Test fun `ordinary message glyphs use the exact composer font size and weight`() {
        SwingUtilities.invokeAndWait {
            val transcript=NativeTranscript().apply { setSize(400,500) }
            transcript.showItems("chat",buildJsonArray { add(buildJsonObject { put("id","1");put("type","agentMessage");put("text","Plain response") }) },"")
            transcript.preferredSize; transcript.doLayout()
            fun editors(c: java.awt.Component): List<javax.swing.JEditorPane> = (if(c is javax.swing.JEditorPane) listOf(c) else emptyList()) + (if(c is java.awt.Container) c.components.flatMap { editors(it) } else emptyList())
            val editor=editors(transcript).single()
            fun glyphs(v: javax.swing.text.View): List<javax.swing.text.GlyphView> = (if(v is javax.swing.text.GlyphView) listOf(v) else emptyList()) + (0 until v.viewCount).flatMap { glyphs(v.getView(it)) }
            val fonts=glyphs(editor.ui.getRootView(editor)).map { it.font }
            assertTrue(fonts.isNotEmpty())
            for(font in fonts) { assertEquals(dev.agentgui.toolwindow.chatFont().family,font.family); assertEquals(dev.agentgui.toolwindow.chatFont().size,font.size); assertFalse(font.isBold) }
        }
    }

}
