package dev.agentgui
import dev.agentgui.toolwindow.SendButton
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities
class ChatWidgetsTest {
    @Test fun `send control initializes accessibility and switches to stop`() {
        SwingUtilities.invokeAndWait {
            val button=SendButton {}
            assertEquals("Send message",button.accessibleContext.accessibleName)
            assertEquals(28,button.preferredSize.width)
            button.working=true
            assertEquals("Stop",button.accessibleContext.accessibleName)
        }
    }
}
