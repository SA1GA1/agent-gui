package dev.agentgui

import dev.agentgui.toolwindow.*
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CodeCopyFeedbackTest {
    private fun editor(text:String)=JEditorPane().apply {
        editorKit=MathEditorKit();CodeCopyFeedback.prepare(this);this.text=SafeMarkdown.render(text)
    }
    @Test fun `copy feedback preserves selectable code without input caret or visible Copy text`() {
        SwingUtilities.invokeAndWait {
            val editor=editor("```kotlin\nval answer = 42\n```")
            editor.caret.isVisible=true
            assertFalse(editor.caret.isVisible);assertFalse(editor.isEditable)
            val links=(editor.document as HTMLDocument).getIterator(HTML.Tag.A)
            CodeCopyFeedback.show(editor,links.attributes.getAttribute(HTML.Attribute.HREF).toString())
            assertTrue(editor.text.contains("agent-icon:check"))
            assertFalse(editor.document.getText(0,editor.document.length).contains("Copy"))
            editor.select(0,editor.document.length)
            assertTrue(editor.selectedText.contains("val answer = 42"))
        }
    }
    @Test fun `identical code blocks confirm only the clicked copy link`() {
        SwingUtilities.invokeAndWait {
            val editor=editor("```go\nsame\n```\n\n```go\nsame\n```")
            val links=(editor.document as HTMLDocument).getIterator(HTML.Tag.A);links.next()
            CodeCopyFeedback.show(editor,links.attributes.getAttribute(HTML.Attribute.HREF).toString(),links.startOffset)
            assertEquals(1,Regex("agent-icon:check").findAll(editor.text).count())
            assertTrue(editor.text.indexOf("agent-icon:copy")<editor.text.indexOf("agent-icon:check"))
        }
    }
    @Test fun `clicking rendered copy icon activates feedback`() {
        SwingUtilities.invokeAndWait {
            val editor=editor("```go\nfmt.Println(42)\n```");editor.setSize(400,200)
            editor.paint(java.awt.image.BufferedImage(400,200,2).graphics)
            var activated=false
            editor.addHyperlinkListener { event ->
                if(event.eventType==javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                    activated=true;CodeCopyFeedback.show(editor,event.description,event.sourceElement?.startOffset)
                }
            }
            val links=(editor.document as HTMLDocument).getIterator(HTML.Tag.A)
            val rect=editor.modelToView2D(links.startOffset)
            editor.dispatchEvent(java.awt.event.MouseEvent(editor,java.awt.event.MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,rect.x.toInt()+4,rect.y.toInt()+4,1,false,java.awt.event.MouseEvent.BUTTON1))
            assertTrue(activated);assertTrue(editor.text.contains("agent-icon:check"))
        }
    }
}
