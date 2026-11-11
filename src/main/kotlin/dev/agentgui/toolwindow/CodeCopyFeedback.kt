package dev.agentgui.toolwindow

import javax.swing.JEditorPane
import javax.swing.Timer
import javax.swing.text.DefaultCaret
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

/** Keep transcript text selectable without ever presenting an insertion caret. */
internal object CodeCopyFeedback {
    fun prepare(editor: JEditorPane) {
        editor.isEditable=false
        editor.caret=object: DefaultCaret() {
            override fun setVisible(value: Boolean) = super.setVisible(false)
        }.apply { blinkRate=0 }
    }
    fun show(editor: JEditorPane, href: String, sourceOffset: Int?=null) {
        val document=editor.document as? HTMLDocument ?: return
        val candidates=document.getIterator(HTML.Tag.A)
        var occurrence=0
        while(candidates.isValid) {
            if(candidates.attributes.getAttribute(HTML.Attribute.HREF)==href) {
                if(sourceOffset==null || sourceOffset in candidates.startOffset until candidates.endOffset) break
                occurrence++
            }
            candidates.next()
        }
        if(!candidates.isValid) return
        fun icon(copied: Boolean) {
            if(editor.document!==document) return
            val links=document.getIterator(HTML.Tag.A)
            var seen=0
            while(links.isValid) {
                if(links.attributes.getAttribute(HTML.Attribute.HREF)==href && seen++==occurrence) {
                    var offset=links.startOffset
                    while(offset<links.endOffset) {
                        val element=document.getCharacterElement(offset)
                        if(element.attributes.getAttribute(javax.swing.text.StyleConstants.NameAttribute)==HTML.Tag.IMG) {
                            val attributes=SimpleAttributeSet(element.attributes)
                            attributes.addAttribute(HTML.Attribute.SRC,if(copied) "agent-icon:check" else "agent-icon:copy")
                            attributes.addAttribute(HTML.Attribute.ALT,if(copied) "Copied" else "Copy")
                            document.setCharacterAttributes(offset,1,attributes,true)
                            return
                        }
                        offset=element.endOffset
                    }
                    return
                }
                links.next()
            }
        }
        // One timer per link, so copying another code block does not strand its feedback.
        val key="agent-copy-feedback:$occurrence:$href"
        (editor.getClientProperty(key) as? Timer)?.stop()
        icon(true)
        editor.putClientProperty(key,Timer(1600) {
            icon(false)
            editor.putClientProperty(key,null)
        }.apply { isRepeats=false; start() })
    }
}
