package dev.agentgui.toolwindow

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import dev.agentgui.codex.*
import dev.agentgui.sessions.ApprovalFlow
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Local keyboard scope; no IDE-global key handlers and no focus theft on arrival. */
internal class ApprovalPanel(request: JsonObject, changes: String, private val choose: (String) -> Unit) : JPanel(BorderLayout(0, 3)) {
    val choices = ApprovalFlow.choices(request)
    val list = JList(choices.mapIndexed { i, choice -> "${i + 1}.  ${choice.label}" }.toTypedArray())
    init {
        isOpaque = false; border = EmptyBorder(0, 0, 5, 0)
        val params = request["params"]!!.jsonObject
        val top = transparentPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val title = params.string("reason")?.takeIf { it.isNotBlank() } ?: if(request.string("method")=="item/fileChange/requestApproval") "Allow file changes?" else "Run this command?"
        val reason=JBTextArea(title).apply { isEditable=false; lineWrap=true; wrapStyleWord=true; background=COMPOSER_BG; foreground=CHAT_FG; font=chatFont(); border=EmptyBorder(0,0,0,0) }
        top.add(JBScrollPane(reason).apply { border=EmptyBorder(0,0,0,0); preferredSize=Dimension(240,((title.lines().size+title.length/45).coerceIn(1,3)*18)); maximumSize=Dimension(Int.MAX_VALUE,62); horizontalScrollBarPolicy=ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER; subtleScrollbars() })
        val command = params.string("command")?.takeIf { it.isNotBlank() } ?: changes
        if(command.isNotBlank()) {
            val text=JBTextArea(command.trimEnd()).apply { isEditable=false; lineWrap=true; wrapStyleWord=true; background=Color(0x222427); foreground=CHAT_FG; font=chatFont(); border=EmptyBorder(0,0,0,0); isOpaque=false }
            val collapsed=(command.lines().size*17).coerceIn(19,52)
            val detail=JBScrollPane(text).apply { border=EmptyBorder(0,0,0,0); preferredSize=Dimension(240,collapsed); maximumSize=Dimension(Int.MAX_VALUE,120); viewport.isOpaque=false; isOpaque=false; subtleScrollbars() }
            top.add(RoundedPanel(Color(0x222427)).apply { border=EmptyBorder(5,8,5,8); add(detail) })
            var expanded=false
            top.add(transparentPanel(FlowLayout(FlowLayout.CENTER,0,0)).apply {
                add(flatButton(icon=ChatIcon("chevron",CHAT_MUTED,16),tooltip="Expand command") {
                    expanded=!expanded; detail.preferredSize=Dimension(240,if(expanded) 120 else collapsed); revalidate()
                }.apply { preferredSize=Dimension(34,14); border=EmptyBorder(0,0,0,0) })
            })
        }

        add(top,BorderLayout.NORTH)
        list.selectionMode=ListSelectionModel.SINGLE_SELECTION; list.selectedIndex=0
        list.background=COMPOSER_BG; list.foreground=CHAT_FG; list.border=EmptyBorder(0,0,0,0)
        list.accessibleContext.accessibleName="Approval options"
        list.cellRenderer=ListCellRenderer { _, value, index, selected, _ ->
            RoundedTextArea(if(selected) Color(0x254C76) else COMPOSER_BG,index==choices.lastIndex).apply {
                text=value; lineWrap=true; wrapStyleWord=true; isEditable=false; font=chatFont()
                foreground=CHAT_FG; background=if(selected) Color(0x254C76) else COMPOSER_BG
                border=EmptyBorder(if(index==choices.lastIndex) 10 else 6,10,6,10); setSize((list.width-8).coerceAtLeast(220),Int.MAX_VALUE)
            }
        }
        list.addComponentListener(object: ComponentAdapter() { override fun componentResized(e: ComponentEvent?) { list.fixedCellHeight=-1; list.revalidate() } })
        list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"choose")
        list.actionMap.put("choose",object: AbstractAction() { override fun actionPerformed(e: ActionEvent?) { choose(choices[list.selectedIndex.coerceAtLeast(0)].decision) } })
        list.addMouseListener(object: MouseAdapter() { override fun mouseClicked(e: MouseEvent) { val index=list.locationToIndex(e.point); if(index>=0 && list.getCellBounds(index,index).contains(e.point)) choose(choices[index].decision) } })
        add(JBScrollPane(list).apply { border=EmptyBorder(0,0,0,0); preferredSize=Dimension(240,96); maximumSize=Dimension(Int.MAX_VALUE,180); subtleScrollbars() },BorderLayout.CENTER)
    }
    fun focusCustom() { list.selectedIndex=choices.lastIndex; list.requestFocusInWindow() }
}
