package dev.agentgui.sessions

import dev.agentgui.codex.string
import kotlinx.serialization.json.*

/** Permission responses and follow-up instructions are deliberately different protocol operations. */
internal class ApprovalFlow {
    data class Choice(val label: String, val decision: String)
    private data class Reply(val turn: String, val text: String)
    private val replies = mutableMapOf<String, Reply>()
    fun defer(thread: String, turn: String, text: String) { replies[thread] = Reply(turn, text) }
    fun completed(thread: String, turn: String?): String? {
        val reply = replies[thread]?.takeIf { it.turn == turn } ?: return null
        replies.remove(thread); return reply.text
    }
    fun clear(thread: String? = null) { if (thread == null) replies.clear() else replies.remove(thread) }
    companion object {
        fun choices(request: JsonObject): List<Choice> = listOf(
            Choice("Allow once", "accept"),
            Choice(if (request.string("method") == "item/fileChange/requestApproval") "Allow changes to these files for this session" else "Allow for this session", "acceptForSession"),
            Choice("Decline or write a reply", "cancel")
        )
        fun validate(request: JsonObject, thread: String, turn: String?, decision: String?, text: String) {
            val params = request["params"]!!.jsonObject
            require(params.string("threadId") == thread && params.string("turnId") == turn) { "This approval request is no longer active" }
            require(decision in choices(request).map { it.decision }) { "Unsupported approval decision" }
            require(text.length <= 200000 && (text.isEmpty() || decision == "cancel")) { "A custom reply must decline the pending action" }
        }
    }
}
