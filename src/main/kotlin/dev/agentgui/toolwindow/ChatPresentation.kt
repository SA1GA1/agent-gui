package dev.agentgui.toolwindow

import dev.agentgui.codex.string
import kotlinx.serialization.json.*
import java.util.Base64

/** Small pure presentation rules shared by the native renderer and prompt navigation. */
object ChatPresentation {
    data class Token(val marker: Char, val query: String, val start: Int, val end: Int)
    fun token(text: String, caret: Int): Token? {
        val end = caret.coerceIn(0, text.length)
        val match = Regex("(?:^|\\s)([/@])([^\\s/@]*)$").find(text.take(end)) ?: return null
        return Token(match.groupValues[1][0], match.groupValues[2], match.range.last - match.groupValues[2].length, end)
    }
    fun userText(item: JsonObject): String = (item["content"] as? JsonArray).orEmpty().joinToString("\n") {
        (it as? JsonObject)?.string("text") ?: "[Attachment]"
    }
    fun prompts(items: JsonArray): List<Pair<String,String>> = items.mapNotNull { value ->
        val item=value.jsonObject
        if(item.string("type")=="userMessage") item.string("id")?.let { it to userText(item) } else null
    }
    fun anchor(id: String) = "message-" + Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
    fun renderItem(item: JsonObject): String {
        val anchor="<a name='${anchor(item.string("id").orEmpty())}'></a>"
        return anchor + when(item.string("type")) {
            "userMessage" -> "<table width='100%' cellspacing='0' cellpadding='0'><tr><td width='14%'></td><td><table width='100%' cellspacing='0' cellpadding='12' bgcolor='#303236'><tr><td>" + SafeMarkdown.escape(userText(item)).replace("\n","<br>") + "</td></tr></table></td></tr></table><br>"
            "agentMessage", "plan" -> SafeMarkdown.render(item.string("text").orEmpty()) + "<br>"
            "reasoning", "hookPrompt", "contextCompaction" -> ""
            "commandExecution" -> "<p style='color:#a8abb1'>${SafeMarkdown.escape(item.string("command") ?: "Command")} · ${SafeMarkdown.escape(item.string("status").orEmpty())}</p>" + item.string("aggregatedOutput")?.let { "<pre>${SafeMarkdown.escape(it.takeLast(16000))}</pre>" }.orEmpty()
            "fileChange" -> "<p style='color:#a8abb1'>File changes · ${SafeMarkdown.escape(item.string("status").orEmpty())}</p>"
            "mcpToolCall" -> "<p style='color:#a8abb1'>${SafeMarkdown.escape(item.string("tool") ?: "Tool")} · ${SafeMarkdown.escape(item.string("status").orEmpty())}</p>"
            else -> ""
        }
    }
}
