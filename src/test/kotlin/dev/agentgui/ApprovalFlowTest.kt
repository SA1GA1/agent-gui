package dev.agentgui

import dev.agentgui.sessions.ApprovalFlow
import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApprovalFlowTest {
    private fun request(method: String = "item/commandExecution/requestApproval") = obj {
        put("id", 42); put("method", method); put("params", obj { put("threadId", "a"); put("turnId", "turn"); put("command", "./gradlew test") })
    }
    @Test fun `session decision is supported while custom text can only cancel`() {
        val request = request()
        assertEquals(listOf("accept", "acceptForSession", "cancel"), ApprovalFlow.choices(request).map { it.decision })
        assertDoesNotThrow { ApprovalFlow.validate(request, "a", "turn", "acceptForSession", "") }
        assertThrows(IllegalArgumentException::class.java) { ApprovalFlow.validate(request, "b", "turn", "accept", "") }
        assertThrows(IllegalArgumentException::class.java) { ApprovalFlow.validate(request, "a", "old", "accept", "") }
        assertThrows(IllegalArgumentException::class.java) { ApprovalFlow.validate(request, "a", "turn", "accept", "do something else") }
        assertThrows(IllegalArgumentException::class.java) { ApprovalFlow.validate(request, "a", "turn", "unknown", "") }
    }
    @Test fun `reply is released only by matching completed turn and once`() {
        val flow = ApprovalFlow()
        flow.defer("a", "turn", "Review only")
        assertNull(flow.completed("a", "old"))
        assertNull(flow.completed("b", "turn"))
        assertEquals("Review only", flow.completed("a", "turn"))
        assertNull(flow.completed("a", "turn"))
        flow.defer("a", "next", "Never replay")
        flow.clear()
        assertNull(flow.completed("a", "next"))
    }
}
