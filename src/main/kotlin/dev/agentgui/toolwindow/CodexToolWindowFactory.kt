package dev.agentgui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import dev.agentgui.bridge.UiBridge
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.util.UUID
import javax.swing.JPanel
import java.awt.BorderLayout

class CodexToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!java.lang.Boolean.getBoolean("agent.gui.webview") || !JBCefApp.isSupported()) {
            val panel = NativeChatPanel(project)
            val content = ContentFactory.getInstance().createContent(panel, "", false)
            content.setDisposer(panel)
            toolWindow.contentManager.addContent(content)
            return
        }
        val browser = JBCefBrowser()
        val bridge = UiBridge(project, browser)
        Disposer.register(browser, bridge)
        val panel = JPanel(BorderLayout()).apply { add(browser.component, BorderLayout.CENTER) }
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(browser)
        toolWindow.contentManager.addContent(content)
        // Never allow a remote document to inherit the privileged JS bridge.
        browser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(b: CefBrowser?, frame: CefFrame?, request: CefRequest?, userGesture: Boolean, isRedirect: Boolean): Boolean =
                request?.url?.let { it != "about:blank" && !it.startsWith("http://agent-gui.local/") } ?: true
        }, browser.cefBrowser)
        val nonce = UUID.randomUUID().toString()
        val loader = javaClass.classLoader
        fun resource(name: String): String = loader.getResourceAsStream("webview/$name")?.bufferedReader()?.use { it.readText() } ?: error("Missing webview/$name; rebuild the plugin")
        val js = resource("app.js").replace("</script", "<\\/script", ignoreCase = true)
        val css = resource("style.css").replace("</style", "<\\/style", ignoreCase = true)
        val background = if (JBColor.isBright()) "#f6f7f9" else "#1e1f22"
        val foreground = if (JBColor.isBright()) "#20242d" else "#e5e7ec"
        browser.loadHTML("""<!doctype html><html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-$nonce'; style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'"><style>:root{--bg:$background;--fg:$foreground;color-scheme:${if (JBColor.isBright()) "light" else "dark"}}</style><style>$css</style></head><body><div id="root"></div><script nonce="$nonce">${bridge.bootstrap()}</script><script nonce="$nonce" type="module">$js</script></body></html>""", "http://agent-gui.local/")
    }
}
