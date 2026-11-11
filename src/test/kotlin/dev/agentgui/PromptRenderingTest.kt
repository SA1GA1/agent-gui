package dev.agentgui
import dev.agentgui.toolwindow.*
import dev.agentgui.sessions.SentAttachments
import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.*
class PromptRenderingTest {
 @Test fun `long user prompts expand while retaining their code and attachments`() {
  SwingUtilities.invokeAndWait {
   val view=NativeTranscript(); view.setSize(420,800)
   val text="Prompt\n\n```kotlin\n"+(1..40).joinToString("\n") { "val value$it = $it" }+"\n```"
   val item=obj { put("id","u");put("type","userMessage");put("content",buildJsonArray { add(obj { put("type","text");put("text",text) }) });put("sentFiles",buildJsonArray { add(obj { put("id","f");put("name","report.pdf") }) }) }
   view.showItems("t",JsonArray(listOf(item)),"")
   val folded=view.preferredSize.height
   fun buttons(c:java.awt.Container):List<JButton> = c.components.flatMap { if(it is JButton) listOf(it) else if(it is java.awt.Container) buttons(it) else emptyList() }
   val more=buttons(view).single { it.text=="Show more ⌄" };assertTrue(more.isVisible)
   more.doClick();assertTrue(view.preferredSize.height>folded)
   more.doClick();assertEquals(folded,view.preferredSize.height)
   assertFalse(buttons(view).any { it.accessibleContext.accessibleName?.startsWith("Remove ")==true })
  }
 }
 @Test fun `sent file presentation recovers immutable attachments without exposing their tool preamble`() {
  val file="11111111-1111-1111-1111-111111111111-report.pdf"
  val item=obj { put("type","userMessage");put("content",buildJsonArray {
   add(obj { put("type","text");put("text","Review this") })
   add(obj { put("type","text");put("text","Attached file: report.pdf\nImmutable local snapshot: /cache/$file\nUse tools") })
  }) }
  val files=SentAttachments.files(item);assertEquals("report.pdf",files.single().string("name"))
  assertEquals("Review this",ChatPresentation.messageText(JsonObject(item+("sentFiles" to JsonArray(files)))))
 }
}
