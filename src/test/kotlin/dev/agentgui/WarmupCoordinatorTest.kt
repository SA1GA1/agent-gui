package dev.agentgui
import dev.agentgui.notifications.WindowWarmupState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WarmupCoordinatorTest {
    @Test fun `user task starting after reservation prevents hidden inference`() {
        val coordinator = WindowWarmupState()
        coordinator.updateReset("account/codex", 100)
        assertNotNull(coordinator.reserve("a", 100, "account/codex"))
        coordinator.activity("b", true)
        var called = false
        assertNull(coordinator.launchIfIdle("a") { called = true; "request" })
        assertFalse(called)
    }
    @Test fun `reservation is specific to the quota pool and survives switching pools`() {
        val coordinator = WindowWarmupState()
        coordinator.updateReset("account/codex", 100)
        assertNull(coordinator.reserve("a", 100, "account/other"))
        assertNotNull(coordinator.reserve("a", 100, "account/codex")); coordinator.finish("a")
        coordinator.updateReset("account/other", 100)
        coordinator.updateReset("account/codex", 100)
        assertNull(coordinator.reserve("a", 101, "account/codex"))
    }
    @Test fun `disabling while metadata loads prevents an automatic request`() {
        val coordinator = WindowWarmupState()
        coordinator.updateReset("account/codex", 100)
        assertNotNull(coordinator.reserve("a", 100, "account/codex"))
        coordinator.state.enabled = false
        assertNull(coordinator.launchIfIdle("a") { "request" })
    }
}
