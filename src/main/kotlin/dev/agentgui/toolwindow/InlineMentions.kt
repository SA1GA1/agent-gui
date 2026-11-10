package dev.agentgui.toolwindow

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.UUID
import javax.swing.*
import javax.swing.text.*
import javax.swing.undo.CompoundEdit
import javax.swing.undo.UndoManager

internal data class InlineMention(val kind: String, val name: String, val path: String?, val start: Int, val end: Int)

/** The identity lives with the text, so edits and undo move both together. */
internal class ComposerTextArea : JTextPane() {
    // Retain the small text-area API used by the composer and its callers.
    var lineWrap=true
    var wrapStyleWord=true
    private data class Reference(val kind: String,val name: String,val path: String?,val id: String=UUID.randomUUID().toString())
    private val referenceKey="agentgui.mention"
    private val undo=UndoManager()
    private var compound: CompoundEdit?=null
    private var pendingReference: Reference?=null
    private var restoring=false
    init {
        font=chatFont(); foreground=CHAT_FG; background=COMPOSER_BG
        (document as AbstractDocument).documentFilter=object: DocumentFilter() {
            override fun insertString(fb: FilterBypass,offset: Int,string: String?,attr: AttributeSet?)=replace(fb,offset,0,string,attr)
            override fun remove(fb: FilterBypass,offset: Int,length: Int)=replace(fb,offset,length,"",null)
            override fun replace(fb: FilterBypass,offset: Int,length: Int,text: String?,attrs: AttributeSet?) {
                var start=offset; var end=offset+length
                for(mention in mentions()) {
                    if((length>0 && start<mention.end && end>mention.start) || (length==0 && offset>mention.start && offset<mention.end)) {
                        start=minOf(start,mention.start); end=maxOf(end,mention.end)
                    }
                }
                val style=SimpleAttributeSet().apply {
                    attrs?.getAttribute(StyleConstants.ComposedTextAttribute)?.let { addAttribute(StyleConstants.ComposedTextAttribute,it) }
                    StyleConstants.setForeground(this,if(pendingReference==null) foreground else Color(0x79B8FF))
                    pendingReference?.let { addAttribute(referenceKey,it) }
                }
                val own=compound==null
                if(own) compound=CompoundEdit()
                try { fb.replace(start,end-start,text,style) }
                finally { if(own) { val edit=compound!!; compound=null; edit.end(); if(!restoring) undo.addEdit(edit) } }
            }
        }
        document.addUndoableEditListener { event -> if(!restoring) { compound?.addEdit(event.edit) ?: undo.addEdit(event.edit) } }
        for((key,action) in listOf(KeyEvent.VK_Z to "draftUndo",KeyEvent.VK_Y to "draftRedo")) {
            for(mask in listOf(InputEvent.CTRL_DOWN_MASK,InputEvent.META_DOWN_MASK)) inputMap.put(KeyStroke.getKeyStroke(key,mask),action)
        }
        for(mask in listOf(InputEvent.CTRL_DOWN_MASK,InputEvent.META_DOWN_MASK)) inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z,mask or InputEvent.SHIFT_DOWN_MASK),"draftRedo")
        actionMap.put("draftUndo",object: AbstractAction() { override fun actionPerformed(e: ActionEvent?)=undoDraft() })
        actionMap.put("draftRedo",object: AbstractAction() { override fun actionPerformed(e: ActionEvent?)=redoDraft() })
    }
    fun mentions(): List<InlineMention> {
        val result=mutableListOf<InlineMention>(); var offset=0
        while(offset<document.length) {
            val element=styledDocument.getCharacterElement(offset)
            val reference=element.attributes.getAttribute(referenceKey) as? Reference
            val end=minOf(element.endOffset,document.length)
            if(reference!=null) {
                val previous=result.lastOrNull()
                if(previous!=null && previous.end==offset && styledDocument.getCharacterElement(offset-1).attributes.getAttribute(referenceKey)==reference)
                    result[result.lastIndex]=previous.copy(end=end)
                else result.add(InlineMention(reference.kind,reference.name,reference.path,offset,end))
            }
            offset=end
        }
        return result
    }
    fun isMentionAt(caret: Int)=mentions().any { caret>it.start && caret<=it.end }
    fun insertMention(kind: String,name: String,path: String?,start: Int,end: Int) {
        val label=(if(kind=="skill") "/" else "@")+name.removePrefix("/").removePrefix("@")
        pendingReference=Reference(kind,name,path)
        try { replaceRange(label,start,end) } finally { pendingReference=null }
        caretPosition=(start+label.length).coerceAtMost(document.length)
    }
    fun replaceRange(text: String,start: Int,end: Int) { (document as AbstractDocument).replace(start,end-start,text,null) }
    fun restoreDraft(text: String,mentions: List<InlineMention>) {
        restoring=true
        try {
            this.text=text
            for(mention in mentions.sortedBy { it.start }) {
                if(mention.kind !in setOf("skill","plugin","file") || mention.start<0 || mention.end>text.length || mention.end<=mention.start) continue
                val expected=(if(mention.kind=="skill") "/" else "@")+mention.name.removePrefix("/").removePrefix("@")
                if(text.substring(mention.start,mention.end)!=expected) continue
                val attrs=SimpleAttributeSet().apply { addAttribute(referenceKey,Reference(mention.kind,mention.name,mention.path)); StyleConstants.setForeground(this,Color(0x79B8FF)) }
                styledDocument.setCharacterAttributes(mention.start,mention.end-mention.start,attrs,true)
            }
        } finally { restoring=false; undo.discardAllEdits() }
    }
    fun undoDraft() { if(undo.canUndo()) undo.undo() }
    fun redoDraft() { if(undo.canRedo()) undo.redo() }
    override fun getScrollableTracksViewportWidth()=true
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if(document.length==0 && !hasFocus()) {
            val copy=g.create() as java.awt.Graphics2D
            try {
                copy.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                copy.color=CHAT_MUTED; copy.font=font
                copy.drawString("/ commands and skills · @ files and plugins",insets.left,insets.top+getFontMetrics(font).ascent)
            } finally { copy.dispose() }
        }
    }
}

internal fun centeredPopupLocation(anchor: Rectangle,size: Dimension,bounds: Rectangle): Point {
    val left=bounds.x+8; val top=bounds.y+8
    return Point((anchor.x+(anchor.width-size.width)/2).coerceIn(left,maxOf(left,bounds.x+bounds.width-size.width-8)),
        (anchor.y-size.height-4).coerceIn(top,maxOf(top,bounds.y+bounds.height-size.height-8)))
}
