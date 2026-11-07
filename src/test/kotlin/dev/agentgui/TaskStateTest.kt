package dev.agentgui
import dev.agentgui.sessions.afterCompaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class TaskStateTest {
    @Test fun `automatic compaction must not finish a running turn`() { assertEquals("running", afterCompaction("running")) }
    @Test fun `manual compaction finishes only on completion notification`() { assertEquals("idle", afterCompaction("compacting")) }
}
