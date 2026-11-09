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
            panel.list.selectedIndex=1
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

}
