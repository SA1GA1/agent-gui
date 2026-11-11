package dev.agentgui

import dev.agentgui.toolwindow.ApprovalDrafts
import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeApprovalTest {
    private fun request() = obj {
        put("id",42); put("method","item/commandExecution/requestApproval"); put("params",obj { put("threadId","a"); put("turnId","turn"); put("command","./gradlew test") })
    }
    @Test fun `custom text and ordinary draft stay separate through escape and resolution`() {
        val drafts = ApprovalDrafts()
        drafts.enter("a", "42", "ordinary draft")
        drafts.edit("a", "42", "review only")
        assertEquals("ordinary draft", drafts.leave("a", "42"))
        assertEquals("review only", drafts.enter("a", "42", "edited ordinary draft"))
        assertEquals("edited ordinary draft", drafts.resolve("a", "42"))
        assertFalse(drafts.isEditing("a", "42"))
        assertEquals("", drafts.enter("b", "42", "other tab"))
    }
    @Test fun `native choice navigation needs explicit activation and uses wrapped rows`() {
        javax.swing.SwingUtilities.invokeAndWait {
            val chosen=mutableListOf<String>()
            val panel=dev.agentgui.toolwindow.ApprovalPanel(request(), "") { chosen.add(it) }
            val down=panel.list.inputMap.get(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN,0))
            panel.list.actionMap.get(down).actionPerformed(java.awt.event.ActionEvent(panel.list,0,""))
            assertEquals(1,panel.list.selectedIndex)
            assertTrue(chosen.isEmpty())
            panel.list.actionMap.get("choose").actionPerformed(java.awt.event.ActionEvent(panel.list,0,""))
            assertEquals(listOf("acceptForSession"),chosen)
            val renderer=panel.list.cellRenderer.getListCellRendererComponent(panel.list,"A very long permission option that must wrap without hiding the permission scope",0,true,false) as javax.swing.JTextArea
            assertTrue(renderer.lineWrap); assertTrue(renderer.wrapStyleWord); assertEquals("A very long permission option that must wrap without hiding the permission scope",renderer.text)
        }
    }

    @Test fun `native approval reason cannot push the controls outside the panel`() {
        javax.swing.SwingUtilities.invokeAndWait {
            val longRequest=obj { put("id",42); put("method","item/commandExecution/requestApproval"); put("params",obj { put("reason",("Long permission explanation\n").repeat(1000)); put("command","echo test") }) }
            val panel=dev.agentgui.toolwindow.ApprovalPanel(longRequest, "") {}
            assertTrue(panel.preferredSize.height < 350, "Approval height must stay bounded")
        }
    }

    @Test fun `approval keys bypass focused tab scrolling but do not capture custom replies or other windows`() {
        javax.swing.SwingUtilities.invokeAndWait {
            val decisions=mutableListOf<String>()
            val root=javax.swing.JPanel()
            val tabs=javax.swing.JScrollBar();root.add(tabs)
            val panel=dev.agentgui.toolwindow.ApprovalPanel(request(), "") { decisions.add(it) };root.add(panel)
            var custom=false
            val scope=dev.agentgui.toolwindow.ApprovalKeyScope(root) { if(custom) null else panel }
            fun key(source:java.awt.Component,code:Int)=java.awt.event.KeyEvent(source,java.awt.event.KeyEvent.KEY_PRESSED,0,0,code,java.awt.event.KeyEvent.CHAR_UNDEFINED)
            assertTrue(scope.dispatchKeyEvent(key(tabs,java.awt.event.KeyEvent.VK_DOWN)))
            assertEquals(1,panel.list.selectedIndex)
            assertTrue(scope.dispatchKeyEvent(key(tabs,java.awt.event.KeyEvent.VK_ENTER)))
            assertEquals(listOf("acceptForSession"),decisions)
            tabs.putClientProperty("composing",true)
            assertFalse(scope.dispatchKeyEvent(key(tabs,java.awt.event.KeyEvent.VK_ENTER)))
            assertEquals(listOf("acceptForSession"),decisions)
            tabs.putClientProperty("composing",false)
            custom=true
            assertFalse(scope.dispatchKeyEvent(key(tabs,java.awt.event.KeyEvent.VK_DOWN)))
            custom=false
            assertFalse(scope.dispatchKeyEvent(key(javax.swing.JTextArea(),java.awt.event.KeyEvent.VK_DOWN)))
            assertEquals(1,panel.list.selectedIndex)
        }
    }

}
