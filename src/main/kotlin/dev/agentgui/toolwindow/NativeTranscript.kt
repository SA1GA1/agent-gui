package dev.agentgui.toolwindow

import dev.agentgui.codex.string
import kotlinx.serialization.json.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.HyperlinkEvent

/** At most 100 cached native bubbles; streaming re-renders only the changed item. */
internal class NativeTranscript : JPanel(null), Scrollable {
    var onLink: (HyperlinkEvent)->Unit = {}
    private var thread=""
    private var items=emptyList<JsonObject>()
    private var target: String?=null
    private var empty=""
    private val views=linkedMapOf<String,Bubble>()
    private class Bubble(val panel: JPanel,val editor: JEditorPane,val user: Boolean,var signature: String="",var measuredWidth: Int=-1,var height: Int=0)
    init { background=CHAT_BG; border=EmptyBorder(8,4,12,4) }
    fun showItems(threadId: String,values: JsonArray,emptyText: String) {
        if(thread!=threadId) { thread=threadId; target=null; views.clear(); removeAll() }
        items=values.map { it.jsonObject }.filter { it.string("type") in setOf("userMessage","agentMessage","plan","commandExecution","fileChange","mcpToolCall") }
        empty=emptyText
        rebuild()
    }
    fun containsMessage(id: String)=views.containsKey(id)
    fun revealMessage(id: String) {
        if(!views.containsKey(id)) { target=id; rebuild() }
        revalidate(); doLayout()
        views[id]?.panel?.let { scrollRectToVisible(Rectangle(0,it.y,width,minOf(it.height,180))) }
    }
    private fun rebuild() {
        val live=items.takeLast(100)
        val selected=target?.let { key -> items.indexOfFirst { it.string("id")==key }.takeIf { it>=0 } }
        val visible=if(selected!=null && live.none { it.string("id")==target }) {
            (items.drop(selected).take(10)+live.takeLast(90)).distinctBy { it.string("id") }
        } else live
        val content=if(visible.isEmpty()) listOf(buildJsonObject { put("id","empty");put("type","empty");put("text",empty) }) else visible
        val ids=content.map { it.string("id").orEmpty() }.toSet()
        views.keys.filter { it !in ids }.forEach { key -> views.remove(key)?.let { remove(it.panel) } }
        content.forEachIndexed { index,item ->
            val id=item.string("id").orEmpty(); val user=item.string("type")=="userMessage"; val isEmpty=item.string("type")=="empty"
            val view=views.getOrPut(id) {
                val editor=JEditorPane("text/html","").apply {
                    isEditable=false; isOpaque=false; border=EmptyBorder(0,0,0,0); foreground=CHAT_FG
                    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true); font=Font(Font.SANS_SERIF,Font.PLAIN,13)
                    addHyperlinkListener { onLink(it) }
                }
                val card=RoundedPanel(if(user) Color(0x1B467E) else Color(0x232528)).apply { border=EmptyBorder(10,14,10,14); add(editor) }
                val holder=transparentPanel(BorderLayout()).apply { border=EmptyBorder(4,if(user) 42 else 0,8,if(user) 0 else 18); add(if(isEmpty) editor else card) }
                Bubble(holder,editor,user).also { add(holder) }
            }
            setComponentZOrder(view.panel,index)
            val signature=item.toString()
            if(view.signature!=signature) {
                view.signature=signature; view.measuredWidth=-1
                val body=when(item.string("type")) {
                    "userMessage" -> SafeMarkdown.escape(ChatPresentation.userText(item)).replace("\n","<br>")
                    "agentMessage","plan" -> SafeMarkdown.render(item.string("text").orEmpty())
                    "empty" -> "<p style='color:#a8abb1'>${SafeMarkdown.escape(empty)}</p>"
                    else -> ChatPresentation.renderItem(item)
                }
                view.editor.text="<html><head><style>body{font-family:sans-serif;font-size:11px;line-height:1.5;font-weight:normal;color:#e7e8eb;margin:0}p{margin:0 0 9px 0}pre{font-family:monospace;font-size:10px}table.code{border:1px solid #3b3e44}table.code td{padding:7px}a{color:#93bbf7}a.copy{text-decoration:none;color:#abb2be}</style></head><body>$body</body></html>"
            }
        }
        revalidate(); repaint()
    }
    private fun measured(view: Bubble,available: Int): Int {
        if(view.measuredWidth!=available) {
            view.panel.border=EmptyBorder(if(view.user) 10 else 8,if(view.user) (available*.28).toInt() else 12,if(view.user) 24 else 16,if(view.user) 8 else 12)
            val insets=view.panel.insets
            val inner=(available-insets.left-insets.right-28).coerceAtLeast(100)
            view.editor.setSize(inner,Short.MAX_VALUE.toInt())
            view.height=view.editor.preferredSize.height+insets.top+insets.bottom+20
            view.measuredWidth=available
        }
        return view.height
    }
    override fun getPreferredSize(): Dimension {
        val available=(parent?.width ?: width).coerceAtLeast(280)-insets.left-insets.right
        return Dimension(available,views.values.sumOf { measured(it,available) }+insets.top+insets.bottom)
    }
    override fun doLayout() {
        val available=(width-insets.left-insets.right).coerceAtLeast(100)
        var y=insets.top
        components.forEach { component -> val view=views.values.firstOrNull { it.panel===component } ?: return@forEach
            val h=measured(view,available); component.setBounds(insets.left,y,available,h); y+=h
        }
    }
    override fun getPreferredScrollableViewportSize()=Dimension(360,500)
    override fun getScrollableTracksViewportWidth()=true
    override fun getScrollableTracksViewportHeight()=false
    override fun getScrollableUnitIncrement(visibleRect: Rectangle,orientation: Int,direction: Int)=22
    override fun getScrollableBlockIncrement(visibleRect: Rectangle,orientation: Int,direction: Int)=maxOf(22,visibleRect.height-30)
}
