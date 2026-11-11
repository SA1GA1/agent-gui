package dev.agentgui
import dev.agentgui.sessions.PromptTimes
import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class PromptTimesTest {
    @Test fun `live send time survives reconnect and completion without assigning fake historical times`() {
        val user=obj { put("id","a");put("type","userMessage") }
        val times=PromptTimes();times.remember(user)
        assertNull(times.decorate(user)["sentAt"])
        times.remember(user,receivedAt=1000000000000)
        times.remember(user,receivedAt=1000000005000)
        assertEquals(1000000000000,PromptTimes(times.json()).decorate(user)["sentAt"]!!.jsonPrimitive.long)
        val historical=obj { put("id","b");put("type","userMessage") }
        times.remember(historical,obj { put("startedAt",1700000000) })
        assertEquals(1700000000000,times.decorate(historical)["sentAt"]!!.jsonPrimitive.long)
    }
}
