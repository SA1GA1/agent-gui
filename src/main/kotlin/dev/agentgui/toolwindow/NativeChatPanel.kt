package dev.agentgui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import dev.agentgui.codex.*
import dev.agentgui.sessions.SessionService
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Lightweight UI: no Chromium, HTTP server, extra agent process or project index. */
class NativeChatPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val service = project.service<SessionService>()
    private val tabs = JComboBox<Choice>()
    private val model = JComboBox<Choice>()
    private val effort = JComboBox<String>()
    private val skill = JComboBox<Choice>()
    private val input = JBTextArea(4, 25)
    private val transcript = JEditorPane("text/html", "").apply { isEditable = false; putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true) }
    private val scroll = JBScrollPane(transcript)
    private val status = JBLabel("Подключите установленный Codex CLI")
    private val context = JBLabel("Контекст: нет данных")
    private val branch = JBLabel("")
    private val files = JPanel(FlowLayout(FlowLayout.LEFT, 3, 0))
    private val notices = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val approval = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val send = JButton("Отправить")
    private val stop = JButton("Стоп")
    private val compact = JButton("/compact")
    private var draftThread = ""
    private var draftText = ""
    private val drafts = Timer(250) { dispatch("draft", "threadId" to JsonPrimitive(draftThread), "text" to JsonPrimitive(draftText)) }.apply { isRepeats = false }
    private var updating = false
    private var disposed = false
    private var state = obj {}
    private var activeId = ""
    private var rendered = ""
    private var latestDraft = ""
    private var pendingFileRequest: String? = null
    private var pendingHistory = false
    private var controlsSignature = ""
    private var filesSignature = ""
    private var incoming: JsonObject? = null
    private val updates = Timer(120) { incoming?.let { incoming = null; applyState(it) } }.apply { start() }
    private val quota = JButton("5 ч: нет данных").apply { addActionListener { quotaPopup() } }
    private data class Choice(val id: String, val label: String) { override fun toString() = label }
    private val sink: (JsonObject) -> Unit = { value -> ApplicationManager.getApplication().invokeLater {
        if (!disposed) when (value.string("type")) {
            "state" -> incoming = value
            "files" -> if (value.string("requestId") == pendingFileRequest) showFiles(value)
            "historyResult" -> if (pendingHistory) showHistory(value)
        }
    } }

    init {
        border = JBUI.Borders.empty(10)
        minimumSize = Dimension(300, 300)
        val toolbar = JPanel(BorderLayout())
        val tabbar = JPanel(FlowLayout(FlowLayout.LEFT, 3, 0))
        tabbar.add(tabs.apply { preferredSize = Dimension(155, 29) })
        tabbar.add(button("＋") { flushDraft(); dispatch("new") })
        tabbar.add(button("История") { history() })
        tabbar.add(button("⋯") { settings() })
        val accountbar = JPanel(FlowLayout(FlowLayout.LEFT, 3, 2))
        accountbar.add(button("Подключить Codex") { dispatch("connect", "binary" to JsonPrimitive(state.string("binary").orEmpty())) })
        accountbar.add(quota)
        toolbar.add(tabbar, BorderLayout.NORTH); toolbar.add(accountbar, BorderLayout.SOUTH)
        add(toolbar, BorderLayout.NORTH)
        val center = JPanel(BorderLayout(0, 7)).apply { border = JBUI.Borders.emptyTop(8); add(scroll, BorderLayout.CENTER) }
        add(center, BorderLayout.CENTER)
        val composer = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val alerts = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); add(notices); add(approval) }
        composer.add(alerts)
        composer.add(files)
        input.lineWrap = true; input.wrapStyleWord = true; input.toolTipText = "Enter — отправить, Shift+Enter — новая строка. / — команды, @ — файл."
        composer.add(JBScrollPane(input).apply { maximumSize = Dimension(Int.MAX_VALUE, 160) })
        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 3, 3))
        actions.add(button("＋ файл") { dispatch("attach") }); actions.add(button("@") { findFile() }); actions.add(button("/") { commandMenu() })
        actions.add(send); actions.add(stop)
        composer.add(actions)
        val models = JPanel(FlowLayout(FlowLayout.LEFT, 3, 0))
        model.preferredSize = Dimension(165, 26); effort.preferredSize = Dimension(90, 26); skill.preferredSize = Dimension(120, 26)
        models.add(model); models.add(effort); models.add(skill); composer.add(models)
        val usage = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { add(context); add(compact) }
        composer.add(usage)
        composer.add(JBLabel("Enter — отправить · Shift+Enter — новая строка").apply { foreground = JBColor.GRAY; font = font.deriveFont(10f) })
        composer.add(branch); composer.add(status)
        center.add(composer, BorderLayout.SOUTH)
        tabs.addActionListener { if (!updating) (tabs.selectedItem as? Choice)?.let { flushDraft(); dispatch("select", "threadId" to JsonPrimitive(it.id)) } }
        model.addActionListener { if (!updating) (model.selectedItem as? Choice)?.let { dispatch("model", "model" to JsonPrimitive(it.id)) } }
        effort.addActionListener { if (!updating) (model.selectedItem as? Choice)?.let { dispatch("model", "model" to JsonPrimitive(it.id), "effort" to JsonPrimitive(effort.selectedItem?.toString().orEmpty())) } }
        send.addActionListener { submit() }; stop.addActionListener { dispatch("stop") }; compact.addActionListener { dispatch("compact") }
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send")
        input.actionMap.put("send", object : AbstractAction() { override fun actionPerformed(e: ActionEvent?) {
            if (input.getClientProperty("composing") == true) return
            when { input.text == "/" -> commandMenu(); input.text.trimEnd().endsWith("@") -> findFile(); else -> submit() }
        } })
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break")
        input.addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(e: InputMethodEvent) { input.putClientProperty("composing", (e.text?.endIndex ?: 0) > e.committedCharacterCount) }
            override fun caretPositionChanged(e: InputMethodEvent) {}
        })
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { changed() }; override fun removeUpdate(e: DocumentEvent) { changed() }; override fun changedUpdate(e: DocumentEvent) { changed() }
            private fun changed() { if (!updating) { draftThread = activeId; draftText = input.text; drafts.restart() } }
        })
        input.addFocusListener(object : FocusAdapter() { override fun focusGained(e: FocusEvent?) { dispatch("refresh") } })
        val textTransfer = input.transferHandler
        input.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || support.isDataFlavorSupported(DataFlavor.imageFlavor) || textTransfer.canImport(support)
            override fun importData(support: TransferSupport): Boolean = try {
                when {
                    support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                        @Suppress("UNCHECKED_CAST") val dropped = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        dispatch("nativeAttachPaths", "paths" to JsonArray(dropped.map { JsonPrimitive(it.absolutePath) })); true
                    }
                    support.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                        val image = support.transferable.getTransferData(DataFlavor.imageFlavor) as Image
                        val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
                        val graphics = buffered.createGraphics(); try { graphics.drawImage(image, 0, 0, null) } finally { graphics.dispose() }
                        val bytes = ByteArrayOutputStream(); ImageIO.write(buffered, "png", bytes)
                        dispatch("pasteImage", "name" to JsonPrimitive("Изображение.png"), "mime" to JsonPrimitive("image/png"), "data" to JsonPrimitive(Base64.getEncoder().encodeToString(bytes.toByteArray()))); true
                    }
                    else -> textTransfer.importData(support)
                }
            } catch (e: Exception) { status.text = e.message ?: "Не удалось добавить вложение"; false }
        }
        transcript.addHyperlinkListener { e -> if (e.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) e.url?.toString()?.let { if (it.startsWith("https://") || it.startsWith("http://")) BrowserUtil.browse(it) } }
        service.listener = sink; dispatch("ready")
    }
    private fun button(text: String, action: () -> Unit) = JButton(text).apply { addActionListener { action() } }
    private fun dispatch(type: String, vararg values: Pair<String, JsonElement>) = service.dispatch(obj { put("type", type); put("threadId", activeId); values.forEach { (key, value) -> put(key, value) } })
    private fun flushDraft() { if (drafts.isRunning) { drafts.stop(); dispatch("draft", "threadId" to JsonPrimitive(draftThread), "text" to JsonPrimitive(draftText)) } }
    private fun submit() {
        if (!send.isEnabled) return
        val text = input.text.trim()
        when (text) { "/model" -> { model.requestFocusInWindow(); return }; "/resume" -> { history(); return } }
        send.isEnabled = false; drafts.stop()
        dispatch("send", "text" to JsonPrimitive(text), "skill" to ((skill.selectedItem as? Choice)?.id?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull))
    }
    private fun applyState(value: JsonObject) {
        updating = true
        try {
            state = value
            val oldId = activeId; activeId = value.string("activeId").orEmpty()
            val chats = value["sessions"]!!.jsonArray.map { it.jsonObject }
            val chat = chats.firstOrNull { it.string("id") == activeId }
            val nextControls = chats.map { it.string("id") to it.string("title") }.toString() + activeId + value["models"].toString() + value["skills"].toString() + chat?.string("model") + chat?.string("effort") + chats.map { it["open"] }.toString()
            if (nextControls != controlsSignature) {
            controlsSignature = nextControls
            tabs.removeAllItems(); chats.filter { it["open"]?.jsonPrimitive?.booleanOrNull != false }.forEach { tabs.addItem(Choice(it.string("id")!!, it.string("title") ?: "Чат")) }
            tabs.selectedItem = (0 until tabs.itemCount).map { tabs.getItemAt(it) }.firstOrNull { it.id == activeId }
            val catalog = value["models"]!!.jsonArray.map { it.jsonObject }
            val selected = chat?.string("model").orEmpty()
            model.removeAllItems(); catalog.forEach { model.addItem(Choice(it.string("model")!!, it.string("displayName")!!)) }
            model.selectedItem = (0 until model.itemCount).map { model.getItemAt(it) }.firstOrNull { it.id == selected }
            effort.removeAllItems(); catalog.firstOrNull { it.string("model") == selected }?.get("supportedReasoningEfforts")?.jsonArray?.forEach { effort.addItem(it.jsonObject.string("reasoningEffort")) }; effort.selectedItem = chat?.string("effort")
            val selectedSkill = (skill.selectedItem as? Choice)?.id
            skill.removeAllItems(); skill.addItem(Choice("", "Без skill")); value["skills"]!!.jsonArray.forEach { val s = it.jsonObject; if (s["enabled"]?.jsonPrimitive?.booleanOrNull != false) skill.addItem(Choice(s.string("name")!!, s.string("name")!!)) }
            skill.selectedItem = (0 until skill.itemCount).map { skill.getItemAt(it) }.firstOrNull { it.id == selectedSkill } ?: skill.getItemAt(0)
            }
            val busy = chat?.string("state")?.let { it != "idle" } ?: false
            val connected = value["connected"]?.jsonPrimitive?.booleanOrNull == true
            val signedIn = value["signedIn"]?.jsonPrimitive?.booleanOrNull == true
            send.isEnabled = connected && signedIn && chat != null && !busy
            stop.isEnabled = busy; compact.isEnabled = chat != null && !busy && connected
            input.isEnabled = connected && signedIn && chat != null
            val draft = chat?.string("draft").orEmpty()
            if (oldId != activeId || (draft != latestDraft && (!input.hasFocus() || busy && draft.isEmpty()))) input.text = draft
            latestDraft = draft
            status.text = value.string("status").orEmpty(); status.toolTipText = status.text
            branch.text = "⑂ ${value.string("branch")} · ${chat?.string("state") ?: ""}"; branch.toolTipText = value.string("cwd")
            val percent = chat?.get("contextPercent")?.jsonPrimitive?.doubleOrNull
            val stale = chat?.get("contextStale")?.jsonPrimitive?.booleanOrNull == true
            context.text = percent?.let { "Контекст: ${"%.1f".format(it)}%${if (stale) " · устарел" else ""}" } ?: "Контекст: нет данных"
            context.foreground = when { stale || percent == null -> JBColor.GRAY; percent >= 90 -> JBColor(0xB52727,0xF08282); percent > 70 -> JBColor(0x936000,0xE5AB5E); else -> JBColor.foreground() }
            quota.text = quotaLabel()
            val nextFiles = chat?.get("files").toString()
            if (nextFiles != filesSignature) { filesSignature = nextFiles
            files.removeAll(); chat?.get("files")?.jsonArray?.forEach { f -> val file = f.jsonObject; files.add(button("${file.string("name")} ×") { dispatch("removeAttachment", "id" to file["id"]!!) }) }
            }
            notices.removeAll(); value["notices"]?.jsonArray?.map { it.jsonObject }?.filter { it.string("threadId").let { id -> id == null || id == activeId } }?.takeLast(3)?.forEach { n ->
                val row = JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(4); background = JBColor(0xF2E5C9,0x393328) }
                row.add(JBLabel("<html>${SafeMarkdown.escape(n.string("text").orEmpty())}</html>"), BorderLayout.CENTER)
                row.add(button("×") { dispatch("dismissNotice", "id" to n["id"]!!) }, BorderLayout.EAST); notices.add(row)
            }
            approval.removeAll(); chat?.get("approvals")?.jsonArray?.forEach { a -> val request = a.jsonObject; val params = request["params"]!!.jsonObject
                approval.add(JBLabel("<html>Разрешить: ${SafeMarkdown.escape(params.string("command") ?: params.string("reason") ?: "изменение файлов")}</html>"))
                approval.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(button("Разрешить один раз") { dispatch("approve", "requestId" to JsonPrimitive(request["id"]!!.jsonPrimitive.content), "decision" to JsonPrimitive("accept")) }); add(button("Отклонить") { dispatch("approve", "requestId" to JsonPrimitive(request["id"]!!.jsonPrimitive.content), "decision" to JsonPrimitive("decline")) }) })
            }
            val items = chat?.get("items")?.jsonArray ?: JsonArray(emptyList())
            val signature = "$activeId:$items:$connected:$signedIn"
            if (signature != rendered) {
                rendered = signature
                val nearBottom = scroll.verticalScrollBar.let { it.value + it.visibleAmount >= it.maximum - 50 }
                val body = if (!connected) "<h2>Codex в вашем проекте</h2><p>Нажмите ⋯ → Подключить Codex.</p>" else if (!signedIn) "<h2>Войдите в Codex</h2><p>Нажмите ⋯ → Войти через ChatGPT.</p>" else if (items.isEmpty()) "<h2>Что сделаем?</h2><p>Опишите задачу, прикрепите файлы или изображение.</p>" else items.joinToString("") { item -> renderItem(item.jsonObject) }
                val color = JBColor.foreground(); val background = JBColor.background()
                fun hex(c: Color) = "#%02x%02x%02x".format(c.red,c.green,c.blue)
                transcript.text = "<html><head><style>body{font-family:sans-serif;font-size:11px;color:${hex(color)};background:${hex(background)};margin:12px}pre{font-family:monospace;font-size:10px}h3{font-size:10px;color:#888888}p{margin-bottom:10px}</style></head><body>$body</body></html>"
                if (nearBottom || oldId != activeId) SwingUtilities.invokeLater { if (!disposed) transcript.caretPosition = transcript.document.length }
            }
            revalidate(); repaint()
        } finally { updating = false }
    }
    private fun renderItem(item: JsonObject): String = when (item.string("type")) {
        "agentMessage", "plan" -> "<h3>Codex</h3>" + SafeMarkdown.render(item.string("text").orEmpty())
        "userMessage" -> "<h3>Вы</h3>" + (item["content"] as? JsonArray).orEmpty().joinToString("") { c -> "<p>" + SafeMarkdown.escape(c.jsonObject.string("text") ?: "[Вложение]").replace("\n", "<br>") + "</p>" }
        "reasoning", "hookPrompt" -> ""
        else -> "<p><b>${SafeMarkdown.escape(item.string("command") ?: item.string("tool") ?: item.string("type").orEmpty())}</b> · ${SafeMarkdown.escape(item.string("status").orEmpty())}</p>" + item.string("aggregatedOutput")?.let { "<pre>${SafeMarkdown.escape(it.takeLast(16000))}</pre>" }.orEmpty()
    }
    private fun commandMenu() { val popup = JPopupMenu(); listOf("/new", "/model", "/status", "/compact", "/resume").forEach { command -> popup.add(JMenuItem(command).apply { addActionListener { input.text = command; input.requestFocusInWindow() } }) }; popup.show(input, 0, 0) }
    private fun findFile() {
        val query = JOptionPane.showInputDialog(this, "Часть пути файла проекта", "@ Файл", JOptionPane.PLAIN_MESSAGE) ?: return
        pendingFileRequest = java.util.UUID.randomUUID().toString()
        dispatch("searchFiles", "query" to JsonPrimitive(query), "requestId" to JsonPrimitive(pendingFileRequest))
    }
    private fun showFiles(event: JsonObject) {
        pendingFileRequest = null
        val options = event["files"]!!.jsonArray.map { it.jsonPrimitive.content }.toTypedArray()
        if (options.isEmpty()) JOptionPane.showMessageDialog(this, "Файлы не найдены") else {
            val selected = JOptionPane.showInputDialog(this, "Выберите файл", "@ Файл", JOptionPane.PLAIN_MESSAGE, null, options, options[0]) as? String
            selected?.let { dispatch("attachProjectFile", "path" to JsonPrimitive(it)); if (input.text.trimEnd().endsWith("@")) input.text = input.text.trimEnd().dropLast(1) }
        }
    }
    private fun settings() {
        val actions = arrayOf("Подключить Codex", "Войти через ChatGPT", "Переименовать чат", "Закрыть вкладку", "Архивировать чат", "Автостарт 5 ч: " + (if (state["autoWindowEnabled"]?.jsonPrimitive?.booleanOrNull == true) "вкл" else "выкл"), "Отмена")
        when (JOptionPane.showOptionDialog(this, "Локальный Codex · ${state.string("version").orEmpty()}", "Agent GUI", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[0])) {
            0 -> { val path = JOptionPane.showInputDialog(this, "Путь к Codex CLI (пусто — автоопределение)", state.string("binary").orEmpty()) ?: return; dispatch("connect", "binary" to JsonPrimitive(path)) }
            1 -> dispatch("login")
            2 -> JOptionPane.showInputDialog(this, "Название чата")?.let { dispatch("rename", "title" to JsonPrimitive(it)) }
            3 -> dispatch("close")
            4 -> dispatch("archive")
            5 -> dispatch("toggleWarmup")
        }
    }
    private fun history() {
        val query = JOptionPane.showInputDialog(this, "Поиск по истории проекта", "") ?: return
        pendingHistory = true
        dispatch("history", "query" to JsonPrimitive(query))
    }
    private fun showHistory(event: JsonObject) {
        pendingHistory = false
        val entries = event["entries"]!!.jsonArray.map { val h = it.jsonObject; Choice(h.string("id")!!, h.string("name") ?: h.string("preview")?.take(100) ?: "Разговор") }.toTypedArray()
        if (entries.isEmpty()) JOptionPane.showMessageDialog(this, "Разговоры не найдены") else {
            val choice = JOptionPane.showInputDialog(this, "Выберите разговор", "История", JOptionPane.PLAIN_MESSAGE, null, entries, entries[0]) as? Choice
            choice?.let { dispatch("resume", "threadId" to JsonPrimitive(it.id)) }
        }
    }
    private fun quotaLabel(): String {
        val window = state["quotaWindows"]?.jsonArray?.firstOrNull { it.jsonObject["minutes"]?.jsonPrimitive?.longOrNull == 300L }?.jsonObject
        val remaining = window?.get("remaining")?.jsonPrimitive?.doubleOrNull
        return "5 ч: " + (remaining?.let { "${it.toInt()}%" } ?: "нет данных")
    }
    private fun quotaPopup() {
        dispatch("refresh")
        val popup = JPopupMenu()
        val windows = state["quotaWindows"]?.jsonArray.orEmpty().map { it.jsonObject }
        for ((minutes, label) in listOf(300L to "5 часов",10080L to "Неделя")) {
            val matches = windows.filter { it["minutes"]?.jsonPrimitive?.longOrNull == minutes }
            if (matches.isEmpty()) popup.add(JMenuItem("$label: нет данных").apply { isEnabled = false })
            matches.forEach { window ->
                val remaining = window["remaining"]?.jsonPrimitive?.doubleOrNull
                val reset = window["resetsAt"]?.jsonPrimitive?.longOrNull
                val date = reset?.let { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm z")) } ?: "нет данных"
                popup.add(JMenuItem("$label · ${window.string("name").orEmpty()}: ${remaining?.toInt()?.toString() ?: "нет данных"}% · сброс $date").apply { isEnabled = false })
            }
        }
        popup.show(quota, 0, quota.height)
    }
    override fun dispose() { disposed = true; drafts.stop(); updates.stop(); if (service.listener === sink) service.listener = null }
}
