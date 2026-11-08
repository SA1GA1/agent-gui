package dev.agentgui.codex

import kotlinx.serialization.json.*
import java.io.Reader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Newline-delimited JSON-RPC without a jsonrpc field, as required by Codex. */
class JsonRpcClient(
    private val writeLine: (String) -> Unit,
    private val onEvent: (String, JsonObject) -> Unit,
    private val onRequest: (JsonObject) -> Unit,
) : AutoCloseable {
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableFuture<JsonObject>>()
    @Volatile private var closed = false

    @Synchronized fun request(method: String, params: JsonObject = buildJsonObject {}): CompletableFuture<JsonObject> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Codex disconnected"))
        val id = ids.incrementAndGet()
        val future = CompletableFuture<JsonObject>().orTimeout(60, TimeUnit.SECONDS)
        pending[id.toString()] = future
        future.whenComplete { _, _ -> pending.remove(id.toString(), future) }
        try { writeLine(buildJsonObject { put("id", id); put("method", method); put("params", params) }.toString()) }
        catch (e: Exception) { future.completeExceptionally(e) }
        return future
    }
    @Synchronized fun notify(method: String) {
        check(!closed)
        writeLine(buildJsonObject { put("method", method) }.toString())
    }
    @Synchronized fun respond(id: JsonElement, result: JsonObject) {
        check(!closed)
        writeLine(buildJsonObject { put("id", id); put("result", result) }.toString())
    }
    @Synchronized fun reject(id: JsonElement, message: String) {
        if (closed) return
        writeLine(buildJsonObject { put("id", id); put("error", buildJsonObject { put("code", -32601); put("message", message) }) }.toString())
    }
    fun receive(line: String) {
        val message = Json.parseToJsonElement(line).jsonObject
        val method = message["method"]?.jsonPrimitive?.content
        val id = message["id"]
        if (method != null) {
            if (id != null) onRequest(message)
            else onEvent(method, message["params"] as? JsonObject ?: buildJsonObject {})
        } else if (id != null) {
            val future = pending.remove(id.jsonPrimitive.content) ?: return
            if (message["error"] != null) future.completeExceptionally(IllegalStateException(
                message["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Error Codex"))
            else future.complete(message["result"] as? JsonObject ?: buildJsonObject {})
        }
    }
    @Synchronized override fun close() {
        closed = true
        pending.values.toList().forEach { it.completeExceptionally(IllegalStateException("Codex exited; the request was not retried")) }
        pending.clear()
    }
}
fun readJsonLines(reader: Reader, consume: (String) -> Unit) {
    reader.buffered().useLines { lines -> lines.filter { it.isNotBlank() }.forEach(consume) }
}
fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
fun obj(block: JsonObjectBuilder.() -> Unit) = buildJsonObject(block)
