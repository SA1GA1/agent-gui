package dev.agentgui.toolwindow

import dev.agentgui.codex.string
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.event.*
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Reserved height keeps messages still when their actions fade into view. */
internal class MessageHoverActions : JPanel(FlowLayout(FlowLayout.RIGHT,7,0)) {
    private var text=""
    private val time=JLabel().apply { foreground=CHAT_MUTED; font=chatFont(11f) }
    private val copy=flatButton(icon=ChatIcon("copy",CHAT_MUTED,16),tooltip="Copy message") {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text),null) }.onSuccess {
            copyFeedback()
        }
    }.apply { preferredSize=Dimension(22,20); border=EmptyBorder(0,0,0,0) }
    private val reset=Timer(1600) { copy.icon=ChatIcon("copy",CHAT_MUTED,16);copy.toolTipText="Copy message" }.apply { isRepeats=false }
    init { isOpaque=false; preferredSize=Dimension(100,22); add(time);add(copy);time.isVisible=false;copy.isVisible=false }
    private fun copyFeedback() { copy.icon=ChatIcon("check",CHAT_MUTED,16); copy.toolTipText="Copied";reset.restart() }
    fun update(item: JsonObject) {
        text=ChatPresentation.messageText(item)
        time.text=(item["sentAt"] as? JsonPrimitive)?.longOrNull?.let { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) }.orEmpty()
    }
    fun install(owner: JComponent) {
        val listener=object: MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { time.isVisible=true; copy.isVisible=true }
            override fun mouseExited(e: MouseEvent) {
                if(!owner.contains(SwingUtilities.convertPoint(e.component,e.point,owner))) { time.isVisible=false;copy.isVisible=false }
            }
        }
        fun attach(c: Component) { c.addMouseListener(listener); if(c is Container) c.components.forEach(::attach) }
        attach(owner)
        copy.addFocusListener(object: FocusAdapter() { override fun focusGained(e: FocusEvent?) { time.isVisible=true;copy.isVisible=true } })
    }
}
