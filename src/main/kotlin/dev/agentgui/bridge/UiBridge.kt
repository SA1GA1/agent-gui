package dev.agentgui.bridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import dev.agentgui.sessions.SessionService
import kotlinx.serialization.json.*

/** Only local compiled UI receives this capability. Agent Markdown never runs HTML. */
class UiBridge(private val project: Project, private val browser: JBCefBrowser) : Disposable {
    companion object {
        // A 10 MB binary file expands to about 14 MB in base64, plus JSON metadata.
        const val MAX_REQUEST_CHARS = 15 * 1024 * 1024
    }
    private val service = project.service<SessionService>()
    private val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val allowed = setOf("promptHistory", "revealPrompt", "ready", "dismissNotice", "toggleWarmup", "connect", "login", "new", "select", "close", "rename", "history", "resume", "archive", "draft", "model", "send", "stop", "compact", "approve", "attach", "uploadText", "uploadBinary", "pasteImage", "removeAttachment", "searchFiles", "attachProjectFile", "refresh", "openUrl")
    private val sink: (JsonObject) -> Unit = { event ->
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && !browser.isDisposed) {
                val payload = JsonPrimitive(event.toString()).toString()
                browser.cefBrowser.executeJavaScript("window.dispatchEvent(new CustomEvent('agent-state',{detail:JSON.parse($payload)}));", browser.cefBrowser.url, 0)
            }
        }
    }
    init {
        query.addHandler { request ->
            try {
                require(request.length <= MAX_REQUEST_CHARS) { "Message is too large" }
                val message = Json.parseToJsonElement(request).jsonObject
                require(message["type"]?.jsonPrimitive?.content in allowed) { "Unknown command" }
                service.dispatch(message)
                JBCefJSQuery.Response("ok")
            } catch (e: Exception) { JBCefJSQuery.Response(null, 400, e.message ?: "Invalid request") }
        }
        service.listener = sink
    }
    fun bootstrap(): String = "window.agentHost=(message)=>{${query.inject("JSON.stringify(message)")}};"
    override fun dispose() { if (service.listener === sink) service.listener = null; query.dispose() }
}
