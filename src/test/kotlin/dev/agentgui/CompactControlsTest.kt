package dev.agentgui

import dev.agentgui.toolwindow.CompactTabs
import dev.agentgui.toolwindow.chatFont
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompactControlsTest {
    @Test fun `tabs shrink to a readable minimum then overflow without wrapping`() {
        assertEquals(144,CompactTabs.tabWidth(600,2))
        assertEquals(98,CompactTabs.tabWidth(300,3))
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
            assertEquals(20,close.preferredSize.width)
            assertFalse(close.isBorderPainted)
        }
    }

    @Test fun `inactive tabs expose close on hover without selecting the chat`() {
        javax.swing.SwingUtilities.invokeAndWait {
            var selected=false;var closed=false
            val tab=dev.agentgui.toolwindow.browserChatTab("Other",false,{selected=true},{closed=true}).apply { setSize(120,30) }
            val close=tab.components.filterIsInstance<javax.swing.JButton>().first { it.accessibleContext.accessibleName=="Close chat" }
            assertFalse(close.isVisible)
            tab.dispatchEvent(java.awt.event.MouseEvent(tab,java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,5,5,0,false))
            assertTrue(close.isVisible);close.doClick();assertTrue(closed);assertFalse(selected)
        }
    }
}
