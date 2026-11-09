package dev.agentgui.notifications

import dev.agentgui.codex.string
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

data class Notice(val text: String, val level: String, val threadId: String? = null, val id: String = UUID.randomUUID().toString(), val action: String? = null, val createdAt: Long = System.currentTimeMillis()) {
    val expiresAt get() = createdAt + 180_000L
    fun expired(now: Long = System.currentTimeMillis()) = now >= expiresAt
    fun json() = buildJsonObject { put("id", id); put("expiresAt", expiresAt); put("text", text); put("level", level); threadId?.let { put("threadId", it) }; action?.let { put("action", it) } }
}
/** Pure threshold crossing logic. Neither billing dates nor unknown quotas are inferred. */
class NoticeTracker {
    private val previousContext = mutableMapOf<String, Double>()
    private val previousRates = mutableMapOf<String, Double>()
    fun context(threadId: String, percent: Double?): Notice? {
        if (percent == null || !percent.isFinite()) return null
        val before = previousContext.put(threadId, percent) ?: 0.0
        val crossed = listOf(50.0, 75.0).lastOrNull { before < it && percent >= it } ?: return null
        return Notice("Context is ${percent.toInt()}% full. Compact to make room after the current task.", if (crossed >= 75) "critical" else "warning", threadId, action = "compact")
    }
    fun rates(snapshot: JsonObject): List<Notice> = buildList {
        val limit = snapshot.string("limitId") ?: "codex"
        for (kind in listOf("primary", "secondary")) {
            val window = snapshot[kind] as? JsonObject ?: continue
            val used = window["usedPercent"]?.jsonPrimitive?.doubleOrNull ?: continue
            val reset = window["resetsAt"]?.jsonPrimitive?.longOrNull
            if (reset != null && reset <= Instant.now().epochSecond) continue
            val remaining = (100.0 - used).coerceIn(0.0, 100.0)
            val key = "$limit:$kind:$reset"
            val before = previousRates.put(key, remaining) ?: 100.0
            val crossed = listOf(50.0, 30.0, 10.0).lastOrNull { before > it && remaining <= it } ?: continue
            val minutes = window["windowDurationMins"]?.jsonPrimitive?.longOrNull
            val label = when(minutes) { 300L -> "5-hour limit"; 10080L -> "Weekly limit"; else -> minutes?.let { if (it % 60 == 0L) "${it / 60}h limit" else "$it min limit" } ?: kind }
            add(Notice("$label: ${remaining.toInt()}% remaining.", when { crossed <= 10 -> "critical"; crossed <= 30 -> "warning"; else -> "info" }))
        }
        // Prevent obsolete quota reset cycles accumulating indefinitely.
        while (previousRates.size > 100) previousRates.remove(previousRates.keys.first())
    }
}
