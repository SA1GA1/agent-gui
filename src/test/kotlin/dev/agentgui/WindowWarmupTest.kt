package dev.agentgui
import dev.agentgui.notifications.canWarmWindow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class WindowWarmupTest {
    @Test fun `warmup waits for reset idle state and reserves each cycle only once`() {
        assertFalse(canWarmWindow(true,100L,0,99,false))
        assertFalse(canWarmWindow(true,100L,0,100,true))
        assertTrue(canWarmWindow(true,100L,0,100,false))
        assertFalse(canWarmWindow(true,100L,100,101,false))
        assertFalse(canWarmWindow(true,null,0,101,false))
        assertFalse(canWarmWindow(false,100L,0,101,false))
    }
}
