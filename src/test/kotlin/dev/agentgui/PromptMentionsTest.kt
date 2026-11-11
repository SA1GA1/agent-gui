package dev.agentgui
import dev.agentgui.codex.*
import dev.agentgui.sessions.PromptMentions
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class PromptMentionsTest {
 private val skill=obj { put("name","review");put("path","/review");put("enabled",true) }
 private val plugin=obj { put("name","GitHub");put("path","plugin://github");put("enabled",true) }
 private fun mention(kind:String,name:String,path:String,start:Int,end:Int)=obj { put("kind",kind);put("name",name);put("path",path);put("start",start);put("end",end) }
 @Test fun `validates multiple inline identities against current catalogs`() {
  val text="Please /review /GitHub";val mentions=JsonArray(listOf(mention("skill","review","/review",7,14),mention("plugin","GitHub","plugin://github",15,22)))
  val input=PromptMentions.inputs(text,mentions,JsonArray(listOf(skill)),JsonArray(listOf(plugin)))
  assertEquals(listOf("skill","mention"),input.map { it.string("type") });assertEquals("plugin://github",input[1].string("path"))
 }
 @Test fun `edited labels lose identities and forged catalog paths fail`() {
  val stale=JsonArray(listOf(mention("plugin","GitHub","plugin://github",0,7)))
  assertTrue(PromptMentions.valid("changed",stale).isEmpty())
  assertThrows(IllegalStateException::class.java) { PromptMentions.inputs("/GitHub",JsonArray(listOf(mention("plugin","GitHub","/forged",0,7))),JsonArray(emptyList()),JsonArray(listOf(plugin))) }
 }
 @Test fun `legacy plugin drafts migrate to slash without moving their ranges`() {
  val refs=PromptMentions.valid("@GitHub",JsonArray(listOf(mention("plugin","GitHub","plugin://github",0,7))))
  assertEquals("/GitHub",PromptMentions.migrateDraft("@GitHub",refs));assertEquals(1,refs.size)
 }
}
