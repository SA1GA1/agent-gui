package dev.agentgui.notifications

import com.intellij.openapi.components.*

fun canWarmWindow(enabled: Boolean, resetAt: Long?, lastAttempt: Long, now: Long, busy: Boolean): Boolean =
    enabled && !busy && resetAt != null && resetAt > 0 && now >= resetAt && lastAttempt < resetAt

/** Application scope prevents two open projects making the same background request. */
@Service(Service.Level.APP)
@State(name = "AgentGuiWindowWarmup", storages = [Storage("agent-gui.xml")])
class WindowWarmupState : PersistentStateComponent<WindowWarmupState.Data> {
    class Data {
        var enabled = true
        var nextResetAt = 0L
        var lastAttemptReset = 0L
        var poolKey = ""
        var attempts: MutableMap<String, Long> = mutableMapOf()
    }
    private var data = Data()
    private val owners = mutableMapOf<String, Boolean>()
    private var warming: String? = null
    override fun getState() = data
    override fun loadState(state: Data) { data = state }
    @Synchronized fun activity(owner: String, busy: Boolean) { owners[owner] = busy }
    @Synchronized fun unregister(owner: String) { owners.remove(owner); if (warming == owner) warming = null }
    @Synchronized fun updateReset(poolKey: String, reset: Long) {
        if (data.poolKey != poolKey) { data.poolKey = poolKey; data.nextResetAt = reset; data.lastAttemptReset = data.attempts[poolKey] ?: 0 }
        if (data.nextResetAt == 0L || data.nextResetAt > java.time.Instant.now().epochSecond || data.lastAttemptReset >= data.nextResetAt) data.nextResetAt = reset
    }
    @Synchronized fun reserve(owner: String, now: Long, poolKey: String): Long? {
        if (poolKey != data.poolKey || warming != null || !canWarmWindow(data.enabled, data.nextResetAt, data.lastAttemptReset, now, owners.values.any { it })) return null
        val cycle = data.nextResetAt
        data.attempts[poolKey] = cycle
        data.lastAttemptReset = cycle // Reserve before starting, including failures; never retry automatically.
        warming = owner
        return cycle
    }
    @Synchronized fun <T> launchIfIdle(owner: String, action: () -> T): T? {
        if (warming != owner || !data.enabled || owners.filterKeys { it != owner }.values.any { it }) return null
        return action()
    }
    @Synchronized fun finish(owner: String) { if (warming == owner) warming = null }
}
