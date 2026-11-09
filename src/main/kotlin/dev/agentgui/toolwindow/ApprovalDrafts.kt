package dev.agentgui.toolwindow

/** Ephemeral request drafts never replace the persisted ordinary chat draft. */
internal class ApprovalDrafts {
    private data class Draft(var ordinary: String, var reply: String = "", var editing: Boolean = false)
    private val values = mutableMapOf<Pair<String, String>, Draft>()
    fun isEditing(thread: String, request: String) = values[thread to request]?.editing == true
    fun enter(thread: String, request: String, ordinary: String): String {
        val draft = values.getOrPut(thread to request) { Draft(ordinary) }
        if (!draft.editing) draft.ordinary = ordinary
        draft.editing = true; return draft.reply
    }
    fun edit(thread: String, request: String, reply: String) { values[thread to request]?.reply = reply }
    fun leave(thread: String, request: String): String? = values[thread to request]?.let { it.editing = false; it.ordinary }
    fun resolve(thread: String, request: String): String? = values.remove(thread to request)?.takeIf { it.editing }?.ordinary
    fun retain(active: Set<Pair<String, String>>) { values.keys.retainAll(active) }
}
