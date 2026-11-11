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
    private val expanded=mutableSetOf<String>()
    private var items=emptyList<JsonObject>()
    private var target: String?=null
    private var empty=""
    private val views=linkedMapOf<String,Bubble>()
    private class Bubble(val panel: JPanel,val editor: JEditorPane,val user: Boolean,val actions: MessageHoverActions?=null,val more: JButton?=null,val attachments: JScrollPane?=null,var signature: String="",var measuredWidth: Int=-1,var height: Int=0)
    init { background=CHAT_BG; border=EmptyBorder(4,4,6,4) }
    fun showItems(threadId: String,values: JsonArray,emptyText: String) {
        if(thread!=threadId) { thread=threadId; target=null; expanded.clear(); views.clear(); removeAll() }
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
            views[id]?.takeIf { user && it.signature.isNotEmpty() && it.signature!=item.toString() }?.let { remove(it.panel);views.remove(id) }
            val view=views.getOrPut(id) {
                val editor=JEditorPane().apply {
                    editorKit=MathEditorKit()
                    CodeCopyFeedback.prepare(this); isOpaque=false; border=EmptyBorder(0,0,0,0); foreground=CHAT_FG
                    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,true); font=chatFont()
                    addHyperlinkListener {
                        if(it.eventType==HyperlinkEvent.EventType.ENTERED) cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        else if(it.eventType==HyperlinkEvent.EventType.EXITED) cursor=Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
                        if(it.eventType==HyperlinkEvent.EventType.ACTIVATED && it.description?.startsWith("agent-expand:")==true) {
                            val key=String(java.util.Base64.getUrlDecoder().decode(it.description.removePrefix("agent-expand:")),Charsets.UTF_8)
                            if(!expanded.add(key)) expanded.remove(key)
                            views[key]?.let { bubble -> bubble.signature="";bubble.measuredWidth=-1 };rebuild()
                        } else onLink(it)
                    }
                }
                val more=if(user) flatButton("Show more ⌄") {
                    if(!expanded.add(id)) expanded.remove(id)
                    views[id]?.let { bubble -> bubble.measuredWidth=-1 };revalidate();repaint()
                }.apply { foreground=CHAT_MUTED; horizontalAlignment=SwingConstants.LEFT; isVisible=false } else null
                val card=(if(user) RoundedPanel(Color(0x1B467E)) else transparentPanel()).apply { border=EmptyBorder(7,10,7,10); add(editor); more?.let { add(it,BorderLayout.SOUTH) } }
                val attachmentRow=if(user) AttachmentRow().apply {
                    (item["sentFiles"] as? JsonArray).orEmpty().forEach { value ->
                        val file=value.jsonObject
                        val preview=file.string("preview")?.let { runCatching { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(it.substringAfter(",")))) }.getOrNull() }
                        add(AttachmentCard(file.string("name").orEmpty(),preview,false) {})
                    }
                } else null
                val attachmentScroll=attachmentRow?.takeIf { it.componentCount>0 }?.let { row -> JScrollPane(row).apply { border=EmptyBorder(0,0,0,0);isOpaque=false;viewport.isOpaque=false;preferredSize=Dimension(240,82);hoverHorizontalScroll() } }
                val actions=if(user) MessageHoverActions() else null
                val holder=transparentPanel(BorderLayout()).apply {
                    border=EmptyBorder(4,if(user) 42 else 0,8,if(user) 0 else 18)
                    add(if(isEmpty) editor else card,BorderLayout.CENTER)
                    actions?.let { add(it,BorderLayout.SOUTH) }
                    attachmentScroll?.let { header -> add(transparentPanel(FlowLayout(FlowLayout.RIGHT,0,0)).apply { add(header) },BorderLayout.NORTH) }
                }
                actions?.install(holder)
                Bubble(holder,editor,user,actions,more,attachmentScroll).also { add(holder) }
            }
            setComponentZOrder(view.panel,index)
            val signature=item.toString()
            if(view.signature!=signature) {
                view.actions?.update(item)
                view.signature=signature; view.measuredWidth=-1
                val body=when(item.string("type")) {
                    "userMessage" -> SafeMarkdown.render(ChatPresentation.messageText(item)).replace("bgcolor='#222427'","bgcolor='#345B8A'")
                    "agentMessage","plan" -> SafeMarkdown.render(item.string("text").orEmpty())
                    "empty" -> "<p style='color:#a8abb1'>${SafeMarkdown.escape(empty)}</p>"
                    else -> ChatPresentation.renderItem(item,id in expanded)
                }
                view.editor.text="<html><head><style>body{line-height:1.45;font-weight:normal;color:#e7e8eb;margin:0}p{margin:6px 0 8px 0}pre{margin:0}div.code-block{margin:10px 0}table.code{border:1px solid #3b3e44}table.code td{padding:7px}a{color:#93bbf7}a.copy{text-decoration:none;color:#abb2be}</style></head><body>$body</body></html>"
            }
        }
        revalidate(); repaint()
    }
    private fun measured(view: Bubble,available: Int): Int {
        if(view.measuredWidth!=available) {
            view.panel.border=EmptyBorder(4,if(view.user) (available*.28).toInt() else 6,8,if(view.user) 4 else 6)
            val insets=view.panel.insets
            val inner=(available-insets.left-insets.right-20).coerceAtLeast(100)
            view.attachments?.let { it.preferredSize=Dimension(minOf(inner,it.viewport.view.preferredSize.width),82) }
            view.editor.setSize(inner,Short.MAX_VALUE.toInt())
            val full=view.editor.preferredSize.height
            val limit=view.editor.getFontMetrics(view.editor.font).height*14
            val long=view.user && full>limit
            view.more?.let { it.isVisible=long;it.text=if(views.entries.firstOrNull { entry -> entry.value===view }?.key in expanded) "Show less ⌃" else "Show more ⌄" }
            val clipped=long && views.entries.firstOrNull { it.value===view }?.key !in expanded
            view.height=(if(clipped) limit else full)+insets.top+insets.bottom+14+(view.actions?.preferredSize?.height ?: 0)+(if(long) view.more?.preferredSize?.height ?: 0 else 0)+(view.attachments?.preferredSize?.height ?: 0)
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
