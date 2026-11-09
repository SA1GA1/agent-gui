package dev.agentgui

import dev.agentgui.toolwindow.CompactTabs
import dev.agentgui.toolwindow.chatFont
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompactControlsTest {
    @Test fun `tabs shrink to a readable minimum then overflow without wrapping`() {
        assertEquals(144,CompactTabs.tabWidth(600,2))
        assertEquals(96,CompactTabs.tabWidth(300,3))
        assertEquals(80,CompactTabs.tabWidth(200,8))
    }
    @Test fun `chat typography is regular system text`() {
        assertFalse(chatFont().isBold)
        assertEquals(13,chatFont().size)
    }
    @Test fun `active tab initializes a circular accessible close control`() {
        javax.swing.SwingUtilities.invokeAndWait {
            val tab=dev.agentgui.toolwindow.browserChatTab("Login fix",true,{}, {})
            val close=tab.components.filterIsInstance<javax.swing.JButton>().first { it.accessibleContext.accessibleName=="Close chat" }
            assertEquals(22,close.preferredSize.width)
            assertFalse(close.isBorderPainted)
        }
    }

}
