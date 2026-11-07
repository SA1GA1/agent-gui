package dev.agentgui.context

import kotlinx.serialization.json.*

data class ContextUsage(val percent: Double?, val level: String) {
    companion object {
        fun from(usage: JsonObject): ContextUsage {
            val window = (usage["modelContextWindow"] as? JsonPrimitive)?.longOrNull
            val tokens = (usage["last"] as? JsonObject)?.get("totalTokens")?.jsonPrimitive?.longOrNull
            val percent = if (window != null && window > 0 && tokens != null && tokens >= 0)
                tokens.toDouble() / window * 100 else null
            return ContextUsage(percent, when { percent == null -> "unknown"; percent >= 90 -> "critical"; percent > 70 -> "warning"; else -> "normal" })
        }
    }
}
