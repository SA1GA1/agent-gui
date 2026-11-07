package dev.agentgui

import dev.agentgui.codex.*
import dev.agentgui.context.ContextUsage
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.util.concurrent.CompletionException

class ProtocolTest {
    @Test fun `correlates out of order responses and ignores unknown events`() {
        val sent = mutableListOf<String>()
        val events = mutableListOf<String>()
        val rpc = JsonRpcClient(sent::add, { method, _ -> events.add(method) }, {})
        val a = rpc.request("first"); val b = rpc.request("second")
        val ids = sent.map { Json.parseToJsonElement(it).jsonObject["id"]!! }
        rpc.receive("""{"id":${ids[1]},"result":{"ok":true}}""")
        rpc.receive("""{"method":"future/event","params":{}}""")
        rpc.receive("""{"id":${ids[0]},"error":{"code":-1,"message":"Denied"}}""")
        assertTrue(b.join()["ok"]!!.jsonPrimitive.boolean)
        assertThrows(CompletionException::class.java) { a.join() }
        assertEquals(listOf("future/event"), events)
        rpc.close()
    }
    @Test fun `invalid JSON fails pending requests and closure never retries`() {
        val sent = mutableListOf<String>()
        val rpc = JsonRpcClient(sent::add, { _, _ -> }, {})
        val pending = rpc.request("turn/start")
        assertThrows(Exception::class.java) { rpc.receive("bad JSON") }
        rpc.close()
        assertTrue(pending.isCompletedExceptionally)
        assertEquals(1, sent.size)
        assertTrue(rpc.request("thread/list").isCompletedExceptionally)
    }
    @Test fun `JSONL handles fragmented reads multiple lines and CRLF`() {
        val lines = mutableListOf<String>()
        val text = "{\"a\":1}\r\n{\"b\":2}\n"
        val reader = object : StringReader(text) {
            override fun read(buffer: CharArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(length, 2))
        }
        readJsonLines(reader, lines::add)
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), lines)
    }
    @Test fun `approval preserves server string request ID`() {
        val sent = mutableListOf<String>()
        var request: JsonObject? = null
        val rpc = JsonRpcClient(sent::add, { _, _ -> }, { request = it })
        rpc.receive("""{"id":"approve-1","method":"item/fileChange/requestApproval","params":{"threadId":"a"}}""")
        rpc.respond(request!!["id"]!!, buildJsonObject { put("decision", "decline") })
        assertEquals("approve-1", Json.parseToJsonElement(sent.single()).jsonObject["id"]!!.jsonPrimitive.content)
    }
    @Test fun `context uses last turn not lifetime totals with strict thresholds`() {
        fun usage(tokens: Long, window: Long?) = ContextUsage.from(buildJsonObject {
            put("last", buildJsonObject { put("totalTokens", tokens) })
            put("total", buildJsonObject { put("totalTokens", 900000) })
            put("modelContextWindow", window?.let(::JsonPrimitive) ?: JsonNull)
        })
        assertEquals("normal", usage(7000, 10000).level)
        assertEquals("warning", usage(7001, 10000).level)
        assertEquals("critical", usage(9000, 10000).level)
        assertNull(usage(10, null).percent)
        assertNull(usage(10, 0).percent)
        assertEquals(70.01, usage(7001, 10000).percent!!, 0.00001)
    }
}
