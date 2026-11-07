package dev.agentgui.sessions

import com.intellij.openapi.components.*

@Service(Service.Level.PROJECT)
@State(name = "AgentGuiUiState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class SessionUiState : PersistentStateComponent<SessionUiState.Data> {
    class Data {
        var binary: String = ""
        var activeId: String = ""
        var sessions: String = "[]"
    }
    private var data = Data()
    override fun getState() = data
    override fun loadState(state: Data) { data = state }
}
