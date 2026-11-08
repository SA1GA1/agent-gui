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
}
