package dev.agentgui
import dev.agentgui.toolwindow.PromptFences
import dev.agentgui.toolwindow.ChatPresentation
import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class PromptFencesTest {
 @Test fun `fenced input is completed without sending and plain text remains unchanged`() {
  assertEquals("\n\n```",PromptFences.enter("```kotlin",9))
  assertEquals("\n",PromptFences.enter("```\nhello\n```",9))
  assertNull(PromptFences.enter("```\nhello\n```",13))
  assertNull(PromptFences.enter("ordinary prompt",15))
  assertEquals("\n",PromptFences.enter("```ts\nx\n```",5))
  assertEquals(1,PromptFences.ranges("before\n```\nlong code\n```\nafter").size)
 }
 @Test fun `command output is inspectable but collapsed by default`() {
  val item=obj { put("id","tool");put("type","commandExecution");put("command","cat SKILL.md");put("aggregatedOutput","PRIVATE_SKILL_BODY") }
  assertFalse(ChatPresentation.renderItem(item).contains("PRIVATE_SKILL_BODY"))
  assertTrue(ChatPresentation.renderItem(item,true).contains("PRIVATE_SKILL_BODY"))
 }
}
