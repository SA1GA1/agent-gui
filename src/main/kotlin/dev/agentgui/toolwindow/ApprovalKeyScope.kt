package dev.agentgui.toolwindow

import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Route approval keys only from this chat; editor and dialog keys remain untouched. */
internal class ApprovalKeyScope(private val root: JComponent, private val active: () -> ApprovalPanel?) : KeyEventDispatcher {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val source=event.component ?: return false
        if(event.isConsumed || !SwingUtilities.isDescendingFrom(source,root)) return false
        if((source as? JComponent)?.getClientProperty("composing")==true) return false
        if(event.modifiersEx!=0 || event.id!=KeyEvent.KEY_PRESSED) return false
        val panel=active() ?: return false
        when(event.keyCode) {
            KeyEvent.VK_UP -> panel.moveSelection(-1)
            KeyEvent.VK_DOWN -> panel.moveSelection(1)
            KeyEvent.VK_ENTER -> panel.chooseSelection()
            else -> return false
        }
        event.consume()
        return true
    }
}
