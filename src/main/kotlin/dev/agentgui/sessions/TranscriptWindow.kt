package dev.agentgui.sessions

import dev.agentgui.codex.string
import kotlinx.serialization.json.JsonObject

/** Retain the selected historical turn and a live tail, without growing the transcript. */
fun transcriptWindow(revealed: List<JsonObject>?, live: List<JsonObject>): List<JsonObject> {
    if (revealed.isNullOrEmpty()) return live.takeLast(500)
    val tail=live.takeLast(400)
    val latest=tail.associateBy { it.string("id") }
    val history=revealed.take(100).map { latest[it.string("id")] ?: it }
    val ids=history.map { it.string("id") }.toSet()
    return history + live.filter { it.string("id") !in ids }.takeLast(500-history.size)
}
