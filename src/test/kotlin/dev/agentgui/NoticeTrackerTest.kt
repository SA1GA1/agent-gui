package dev.agentgui
import dev.agentgui.notifications.Notice
import dev.agentgui.notifications.NoticeTracker
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class NoticeTrackerTest {
    @Test fun `context thresholds are per chat and do not repeat until crossing again`() {
        val tracker = NoticeTracker()
        assertNull(tracker.context("a",49.9))
        assertEquals("compact",tracker.context("a",50.0)?.json()?.get("action")?.jsonPrimitive?.content)
        assertNull(tracker.context("a",50.1))
        assertEquals("critical",tracker.context("a",75.0)?.level)
        assertNull(tracker.context("a",90.0))
        assertEquals("warning",tracker.context("b",50.0)?.level)
        assertNull(tracker.context("a",null))
        tracker.context("a",20.0)
        assertNotNull(tracker.context("a",50.0))
    }
    @Test fun `remaining limit uses used percent distinct windows and reset cycles`() {
        val tracker=NoticeTracker()
        fun snapshot(used:Int, reset:Long=2000000000L)=buildJsonObject {
            put("limitId","codex");put("limitName","Codex");put("primary",buildJsonObject {put("usedPercent",used);put("resetsAt",reset);put("windowDurationMins",300)})
        }
        assertTrue(tracker.rates(snapshot(49)).isEmpty())
        assertEquals(1,tracker.rates(snapshot(50)).size)
        assertTrue(tracker.rates(snapshot(51)).isEmpty())
        assertEquals("warning",tracker.rates(snapshot(70)).single().level)
        assertEquals("critical",tracker.rates(snapshot(90)).single().level)
        assertEquals(1,tracker.rates(snapshot(50,2000000300L)).size)
        assertTrue(tracker.rates(buildJsonObject {}).isEmpty())
    }
    @Test fun `notice expires after three minutes without extending on redraw`() {
        val notice=Notice("Warning", "warning", createdAt=1000)
        assertFalse(notice.expired(180999))
        assertTrue(notice.expired(181000))
        assertEquals(181000L,notice.expiresAt)
    }

}
