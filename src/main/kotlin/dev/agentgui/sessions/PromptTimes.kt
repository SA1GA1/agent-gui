package dev.agentgui.sessions

import dev.agentgui.codex.string
import kotlinx.serialization.json.*
import java.time.Instant

/** Keep local send times across reconnects; never invent dates for imported history. */
internal class PromptTimes(saved: JsonElement?=null) {
    private val values=linkedMapOf<String,Long>().apply {
        (saved as? JsonObject)?.entries?.toList()?.takeLast(1000)?.forEach { (id,value) ->
            (value as? JsonPrimitive)?.longOrNull?.takeIf { it>0 }?.let { put(id,it) }
        }
    }
    fun remember(item: JsonObject, parent: JsonObject?=null, receivedAt: Long?=null) {
        if(item.string("type")!="userMessage") return
        val id=item.string("id") ?: return
        val time=time(item) ?: parent?.let(::time) ?: values[id] ?: receivedAt ?: return
        values[id]=time
        while(values.size>1000) values.remove(values.keys.first())
    }
    fun decorate(item: JsonObject): JsonObject = values[item.string("id")]?.let { JsonObject(item+("sentAt" to JsonPrimitive(it))) } ?: item
    fun json()=JsonObject(values.mapValues { JsonPrimitive(it.value) })
    private fun time(value: JsonObject):Long? = sequenceOf("sentAt","createdAt","startedAt","timestamp").mapNotNull { key ->
        val raw=(value[key] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        raw.toLongOrNull()?.takeIf { it>0 }?.let { if(it<100000000000L) it*1000 else it }
            ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }.firstOrNull()
}
