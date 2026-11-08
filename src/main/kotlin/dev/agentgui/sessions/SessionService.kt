package dev.agentgui.sessions

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.agentgui.attachments.AttachmentService
import dev.agentgui.codex.*
import dev.agentgui.context.ContextUsage
import dev.agentgui.git.BranchService
import dev.agentgui.notifications.WindowWarmupState
import dev.agentgui.notifications.NoticeTracker
import dev.agentgui.notifications.Notice
import kotlinx.serialization.json.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "agent-gui-state").apply { isDaemon = true } }
    private val store get() = project.service<SessionUiState>().state
    private var process: CodexProcess? = null
    private val attachments = AttachmentService()
    private val sessions = linkedMapOf<String, Chat>()
    private val warmup get() = service<WindowWarmupState>()
    private val owner = project.locationHash
    private var warmThread: String? = null
    private var warmTurn: String? = null
    private val noticeTracker = NoticeTracker()
    private val notices = ArrayDeque<Notice>()
    private var rateLimits = obj {}
    private var lastRatesRefresh = 0L
    private val quotaPools = linkedMapOf<String, JsonObject>()
    private var quotaAccount = "local"
    private var models = JsonArray(emptyList())
    private var history = JsonArray(emptyList())
    private var historyCursor: String? = null
    private var extensions = JsonArray(emptyList())
    private var plugins = JsonArray(emptyList())
    private var generation = 0
    private var connected = false
    private var signedIn = false
    private var status = "Connect your installed Codex CLI"
    private var version = ""
    private var branch = "Unavailable"
    @Volatile private var disposed = false
    @Volatile var listener: ((JsonObject) -> Unit)? = null
    private var publishPending = false
    private val queue = ArrayDeque<Pair<String, JsonObject>>()
    private val approvals = linkedMapOf<String, JsonObject>()
    private val cwd get() = project.basePath ?: error("Open a project directory")

    private class Chat(val id: String, var title: String = "New chat") {
        var draft = ""
        var model = ""
        var effort = ""
        var turnId: String? = null
        var state = "idle"
        var resumed = false
        var open = true
        var usage = ContextUsage(null, "unknown")
        var stale = true
        val items = linkedMapOf<String, JsonObject>()
        var revealedItems: List<JsonObject>? = null
        val promptTurns = linkedMapOf<String, String>()
        var promptRequest: String? = null
        val files = mutableListOf<JsonObject>()
    }
    init {
        worker.execute {
            runCatching { Json.parseToJsonElement(store.sessions).jsonArray }.getOrNull()?.forEach { value ->
                val s = value.jsonObject
                val id = s.string("id") ?: return@forEach
                sessions[id] = Chat(id, s.string("title") ?: "Chat").apply {
                    draft = s.string("draft") ?: ""; model = s.string("model") ?: ""; effort = s.string("effort") ?: ""
                    open = s["open"]?.jsonPrimitive?.booleanOrNull ?: true
                    files.addAll((s["files"] as? JsonArray).orEmpty().map { it.jsonObject })
                }
            }
            publish()
        }
        worker.scheduleWithFixedDelay({ if (!disposed) runCatching { warmWindow() } }, 30, 30, TimeUnit.SECONDS)
    }
    fun dispatch(message: JsonObject) {
        if (disposed) return
        worker.execute {
            try { handle(message) } catch (e: Exception) { status = e.cause?.message ?: e.message ?: "Error"; addError(status); publish() }
        }
    }
    private fun rpc(method: String, params: JsonObject = obj {}): JsonObject =
        (process ?: error("Connect Codex first")).rpc.request(method, params).join()
    private fun handle(m: JsonObject) {
        val id = m.string("threadId") ?: store.activeId
        when (m.string("type")) {
            "ready" -> publish()
            "connect" -> connect(m.string("binary") ?: store.binary)
            "login" -> {
                val result = rpc("account/login/start", obj { put("type", "chatgpt") })
                result.string("authUrl")?.let { url -> require(url.startsWith("https://")); BrowserUtil.browse(url) }
                status = "Complete sign-in in your browser"; publish()
            }
            "new" -> newChat()
            "select" -> { require(sessions.containsKey(id)); store.activeId = id; ensureResumed(sessions.getValue(id)); sessions.getValue(id).revealedItems = null; refreshBranch(); publish() }
            "close" -> { val chat = sessions[id] ?: return; chat.open = false; if (store.activeId == id) store.activeId = sessions.values.lastOrNull { it.open }?.id ?: ""; persist(); publish() }
            "rename" -> { val title = m.string("title")?.trim()?.take(100).orEmpty(); require(title.isNotEmpty()); rpc("thread/name/set", obj { put("threadId", id); put("name", title) }); sessions[id]?.title = title; persist(); publish() }
            "history" -> { val page = rpc("thread/list", obj { put("cwd", cwd); put("limit", 50); put("modelProviders", JsonArray(listOf(JsonPrimitive("openai")))); put("searchTerm", m.string("query") ?: ""); m.string("cursor")?.let { put("cursor", it) } }); history = page["data"]?.jsonArray ?: JsonArray(emptyList()); historyCursor = page.string("nextCursor"); listener?.invoke(obj { put("type", "historyResult"); put("entries", history); put("cursor", historyCursor) }); publish() }
            "promptHistory" -> promptHistory(id, m)
            "revealPrompt" -> revealPrompt(id, m.string("itemId").orEmpty())
            "resume" -> { val chat = sessions.getOrPut(id) { Chat(id) }; chat.open = true; store.activeId = id; ensureResumed(chat); persist(); publish() }
            "archive" -> { require(sessions[id]?.state.let { it == null || it == "idle" }); rpc("thread/archive", obj { put("threadId", id) }); sessions.remove(id); if (store.activeId == id) store.activeId = sessions.keys.lastOrNull() ?: ""; persist(); publish() }
            "draft" -> { sessions[id]?.draft = m.string("text")?.take(200000).orEmpty(); persist() }
            "model" -> {
                val chat = sessions[id] ?: return
                val model = models.firstOrNull { it.jsonObject.string("model") == m.string("model") }?.jsonObject ?: error("Model unavailable")
                val effort = m.string("effort") ?: model.string("defaultReasoningEffort").orEmpty()
                require(model["supportedReasoningEfforts"]!!.jsonArray.any { it.jsonObject.string("reasoningEffort") == effort })
                chat.model = model.string("model")!!; chat.effort = effort; chat.stale = true; persist(); publish()
            }
            "send" -> send(id, m)
            "stop" -> { val chat = sessions[id] ?: return; if (chat.state == "queued") { queue.removeAll { it.first == id }; chat.state = "idle" } else chat.turnId?.let { turn -> rpc("turn/interrupt", obj { put("threadId", id); put("turnId", turn) }) }; publish() }
            "compact" -> {
                val chat = sessions[id] ?: return; require(chat.state == "idle") { "Compact is available after the task finishes" }; ensureResumed(chat)
                require(sessions.values.count { it.state in setOf("running", "compacting") } < 2) { "Two tasks are already running. Please wait for a free slot" }; chat.state = "compacting"; warmup.activity(owner, true); publish()
                try { rpc("thread/compact/start", obj { put("threadId", id) }) } catch (e: Exception) { chat.state = "idle"; throw e }
            }
            "approve" -> {
                val key = m.string("requestId") ?: return; val request = approvals[key] ?: error("This approval request is no longer active")
                require(request["params"]?.jsonObject?.string("threadId") == id)
                val decision = m.string("decision"); require(decision in setOf("accept", "decline", "cancel"))
                process!!.rpc.respond(request["id"]!!, obj { put("decision", decision) }); approvals.remove(key); publish()
            }
            "attach" -> chooseFiles(id)
            "uploadText" -> {
                val chat = sessions[id] ?: return; require(chat.files.size < 8) { "Up to 8 attachments are supported" }
                chat.files.add(attachments.text(m.string("name") ?: "File", m.string("text").orEmpty())); persist(); publish()
            }
            "pasteImage" -> {
                val chat = sessions[id] ?: return; require(chat.files.size < 8) { "Up to 8 attachments are supported" }
                chat.files.add(attachments.paste(m.string("name") ?: "Image", m.string("mime").orEmpty(), m.string("data").orEmpty())); persist(); publish()
            }
            "removeAttachment" -> { val chat = sessions[id] ?: return; val file = chat.files.firstOrNull { it.string("id") == m.string("id") } ?: return; attachments.remove(file.string("id")!!); chat.files.remove(file); persist(); publish() }
            "searchFiles" -> searchFiles(m.string("query").orEmpty(), m.string("requestId").orEmpty())
            "attachProjectFile" -> {
                val path = Path.of(cwd).resolve(m.string("path").orEmpty()).toRealPath()
                require(path.startsWith(Path.of(cwd).toRealPath())) { "File is outside the project" }
                ApplicationManager.getApplication().invokeLater {
                    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                    if (file != null) saveAndAdd(id, listOf(file))
                }
            }
            "refresh" -> { refreshBranch(); if (connected && System.currentTimeMillis() - lastRatesRefresh > 30000) runCatching { rates(rpc("account/rateLimits/read")) }; publish() }
            "toggleWarmup" -> { warmup.state.enabled = !warmup.state.enabled; publish() }
            "dismissNotice" -> { notices.removeAll { it.id == m.string("id") }; publish() }
            "nativeAttachPaths" -> { val paths = m["paths"]!!.jsonArray.map { Path.of(it.jsonPrimitive.content) }; ApplicationManager.getApplication().invokeLater { saveAndAdd(id, paths.mapNotNull { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }) } }
            "openUrl" -> { val url = m.string("url").orEmpty(); require(url.startsWith("https://") || url.startsWith("http://")); BrowserUtil.browse(url) }
            else -> error("Unsupported interface command")
        }
    }
    private fun connect(binary: String) {
        require(sessions.values.none { it.state != "idle" }) { "Wait for running tasks before reconnecting" }
        generation++; val connectionGeneration = generation
        quotaPools.clear()
        process?.close(); process = null; warmThread = null; warmTurn = null; warmup.finish(owner); connected = false; signedIn = false; approvals.clear()
        sessions.values.forEach { it.resumed = false; it.stale = true }
        store.binary = binary; status = "Connecting…"; publish()
        val executable = CodexProcess.discover(binary)
        version = CodexProcess.version(executable)
        process = CodexProcess(executable, cwd, { method, params -> if (!disposed) worker.execute { if (connectionGeneration == generation) event(method, params) } },
            { request -> if (!disposed) worker.execute { if (connectionGeneration == generation) serverRequest(request) } }, { reason -> if (!disposed) worker.execute { if (connectionGeneration == generation) disconnected(reason) } })
        try {
            process!!.initialize()
            val catalog = mutableListOf<JsonElement>(); var cursor: String? = null
            do { val page = rpc("model/list", obj { cursor?.let { put("cursor", it) } }); catalog.addAll(page["data"]!!.jsonArray); cursor = page.string("nextCursor") } while (cursor != null)
            models = JsonArray(catalog.filter { it.jsonObject["hidden"]?.jsonPrimitive?.booleanOrNull != true })
            signedIn = rpc("account/read", obj { put("refreshToken", false) })["account"].let { it != null && it != JsonNull }
            runCatching { rates(rpc("account/rateLimits/read")) }
            plugins = runCatching { installedPlugins(rpc("plugin/list", obj { put("cwds", JsonArray(listOf(JsonPrimitive(cwd)))); put("forceRefetch", false) })) }.getOrDefault(JsonArray(emptyList()))
            connected = true; status = if (signedIn) "Ready" else "Sign in to Codex"
            extensions = runCatching { rpc("skills/list", obj { put("cwds", JsonArray(listOf(JsonPrimitive(cwd)))) })["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("skills")?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
            refreshBranch()
            if (sessions.isEmpty()) newChat() else sessions[store.activeId]?.let { ensureResumed(it) }
            publish()
        } catch (e: Exception) { generation++
        process?.close(); process = null; connected = false; throw e }
    }
    private fun newChat() {
        check(connected)
        val thread = rpc("thread/start", obj { put("cwd", cwd); put("modelProvider", "openai");  })["thread"]!!.jsonObject
        val chat = Chat(thread.string("id")!!).apply {
            resumed = true
            val default = models.firstOrNull { it.jsonObject["isDefault"]?.jsonPrimitive?.booleanOrNull == true }?.jsonObject ?: models.firstOrNull()?.jsonObject
            model = default?.string("model").orEmpty(); effort = default?.string("defaultReasoningEffort").orEmpty()
        }
        sessions[chat.id] = chat; store.activeId = chat.id; persist(); publish()
    }
    private fun ensureResumed(chat: Chat) {
        if (chat.resumed) return
        val data = rpc("thread/resume", obj { put("threadId", chat.id); put("cwd", cwd);  })
        val thread = data["thread"]!!.jsonObject
        require(thread.string("modelProvider") == "openai") { "Only OpenAI conversations are supported" }
        chat.title = thread.string("name") ?: thread.string("preview")?.take(60) ?: chat.title
        chat.model = data.string("model").orEmpty(); chat.effort = data.string("reasoningEffort").orEmpty()
        chat.items.clear()
        thread["turns"]?.jsonArray?.forEach { turn -> turn.jsonObject["items"]?.jsonArray?.forEach { item -> chat.items[item.jsonObject.string("id")!!] = item.jsonObject } }
        chat.resumed = true
    }
    private fun send(id: String, message: JsonObject) {
        val chat = sessions[id] ?: error("Create a chat")
        require(chat.state == "idle") { "A task is already running" }; require(signedIn) { "Sign in to Codex" }
        val text = message.string("text")?.trim().orEmpty(); require(text.length <= 200000)
        if (text.startsWith('/')) { command(chat, text); return }
        require(text.isNotEmpty() || chat.files.isNotEmpty()) { "Enter a message or add an attachment" }
        ensureResumed(chat)
        chat.revealedItems = null
        val input = buildJsonArray {
            if (text.isNotEmpty()) add(obj { put("type", "text"); put("text", text) })
            chat.files.forEach { add(attachments.input(it)) }
            message.string("skill")?.let { name ->
                val skill = extensions.firstOrNull { it.jsonObject.string("name") == name && it.jsonObject["enabled"]?.jsonPrimitive?.booleanOrNull != false }?.jsonObject ?: error("Skill unavailable")
                add(obj { put("type", "skill"); put("name", name); put("path", skill.string("path")) })
            }
            (message["plugin"] as? JsonObject)?.let { selected ->
                val plugin = plugins.firstOrNull { it.jsonObject.string("name") == selected.string("name") && it.jsonObject.string("path") == selected.string("path") }?.jsonObject ?: error("Plugin unavailable")
                add(obj { put("type", "mention"); put("name", plugin.string("name")); put("path", plugin.string("path")) })
            }
        }
        val params = obj { put("threadId", id); put("input", input); if (chat.model.isNotEmpty()) put("model", chat.model); if (chat.effort.isNotEmpty()) put("effort", chat.effort) }
        chat.state = "queued"; chat.draft = ""; chat.files.clear(); queue.add(id to params); persist(); pump(); publish()
    }
    private fun command(chat: Chat, text: String) {
        when (text.substringBefore(' ')) {
            "/new" -> newChat()
            "/compact" -> handle(obj { put("type", "compact"); put("threadId", chat.id) })
            "/status" -> { refreshBranch(); status = "$version · $branch · ${chat.state}"; notices.add(Notice(status,"info",chat.id)) }
            "/model", "/resume" -> listener?.invoke(obj { put("type", "show"); put("panel", if (text == "/model") "models" else "history") })
            else -> error("This terminal command is unavailable here. Use the / menu.")
        }
        chat.draft = ""; persist(); publish()
    }
    private fun pump() {
        while (queue.isNotEmpty() && sessions.values.count { it.state in setOf("running", "compacting") } + (if (warmThread != null) 1 else 0) < 2) {
            val (id, params) = queue.removeFirst(); val chat = sessions[id] ?: continue
            chat.state = "running"
            warmup.activity(owner, true)
            try { val turn = rpc("turn/start", params)["turn"]!!.jsonObject; chat.turnId = turn.string("id") }
            catch (e: Exception) { chat.state = "idle"; status = "Request was not retried: ${e.cause?.message ?: e.message}"; addError(status) }
        }
    }
    private fun event(method: String, p: JsonObject) {
        if (disposed) return
        if (p.string("threadId") == warmThread && warmThread != null) {
            if (method == "turn/completed") { process?.rpc?.request("thread/unsubscribe", obj { put("threadId", warmThread) }); warmThread = null; warmTurn = null; warmup.finish(owner); runCatching { rates(rpc("account/rateLimits/read")) }; pump(); publish() }
            return
        }
        val chat = sessions[p.string("threadId")]
        when (method) {
            "account/login/completed", "account/updated" -> { signedIn = runCatching { rpc("account/read", obj { put("refreshToken", false) })["account"].let { it != null && it != JsonNull } }.getOrDefault(false); status = if (signedIn) "Ready" else "Sign-in incomplete" }
            "turn/started" -> { chat?.state = "running"; chat?.turnId = (p["turn"] as? JsonObject)?.string("id") }
            "item/started", "item/completed" -> { val item = p["item"] as? JsonObject; if (chat != null && item != null) chat.items[item.string("id") ?: return] = item; if (method == "item/completed" && item?.string("type") == "contextCompaction") { chat?.let { it.state = afterCompaction(it.state); it.stale = true }; pump() } }
            "item/agentMessage/delta" -> if (chat != null) {
                val itemId = p.string("itemId") ?: return
                val old = chat.items[itemId]
                chat.items[itemId] = obj { put("id", itemId); put("type", "agentMessage"); put("text", old?.string("text").orEmpty() + p.string("delta").orEmpty()) }
            }
            "turn/completed" -> { chat?.state = "idle"; chat?.turnId = null; val turn = p["turn"] as? JsonObject; if (turn?.get("error") != null && turn["error"] != JsonNull) { status = turn["error"]!!.jsonObject.string("message") ?: "Task failed"; addError(status) }; approvals.entries.removeIf { it.value["params"]?.jsonObject?.string("threadId") == chat?.id }; pump(); refreshBranch() }
            "thread/tokenUsage/updated" -> if (chat != null) { chat.usage = ContextUsage.from(p["tokenUsage"]!!.jsonObject); chat.stale = false; noticeTracker.context(chat.id, chat.usage.percent)?.let { notices.add(it) } }
            "account/rateLimits/updated" -> rates(p)
            "thread/compacted" -> { chat?.let { it.stale = true; it.state = afterCompaction(it.state) }; pump() }
            "error" -> { status = (p["error"] as? JsonObject)?.string("message") ?: "Codex error"; addError(status) }
        }
        while (notices.size > 40) notices.removeFirst()
        chat?.let { while (it.items.size > 500) it.items.remove(it.items.keys.first()) }
        publishBatched()
    }
    private fun rates(data: JsonObject) {
        lastRatesRefresh = System.currentTimeMillis()
        rateLimits = data
        val snapshots = (data["rateLimitsByLimitId"] as? JsonObject)?.values?.mapNotNull { it as? JsonObject }
            ?.takeIf { it.isNotEmpty() } ?: listOfNotNull(data["rateLimits"] as? JsonObject)
        data.string("accountId")?.let { quotaAccount = it }
        snapshots.forEach { snapshot ->
            quotaPools[snapshot.string("limitId") ?: "codex"] = snapshot
            notices.addAll(noticeTracker.rates(snapshot))
        }
        selectedQuotaPool()?.let { (key, snapshot) ->
            for (name in listOf("primary", "secondary")) {
                val window = snapshot[name] as? JsonObject ?: continue
                if (window["windowDurationMins"]?.jsonPrimitive?.longOrNull == 300L) window["resetsAt"]?.jsonPrimitive?.longOrNull?.let { warmup.updateReset("$quotaAccount/$key", it) }
            }
        }
        while (notices.size > 40) notices.removeFirst()
    }
    private fun quotaWindows(): JsonArray {
        val snapshots = quotaPools.values
        return buildJsonArray { snapshots.forEach { snapshot -> for (key in listOf("primary", "secondary")) {
            val window = snapshot[key] as? JsonObject ?: continue
            val reset = window["resetsAt"]?.jsonPrimitive?.longOrNull
            val used = window["usedPercent"]?.jsonPrimitive?.doubleOrNull
            add(obj {
                put("name", snapshot.string("limitName") ?: snapshot.string("limitId") ?: "Codex")
                put("minutes", window["windowDurationMins"] ?: JsonNull); put("resetsAt", reset?.let(::JsonPrimitive) ?: JsonNull)
                put("remaining", if (used != null && (reset == null || reset > java.time.Instant.now().epochSecond)) JsonPrimitive((100.0 - used).coerceIn(0.0,100.0)) else JsonNull)
            })
        } } }
    }
    private fun selectedQuotaPool(): Pair<String, JsonObject>? {
        val defaultModel = models.firstOrNull { it.jsonObject["isDefault"]?.jsonPrimitive?.booleanOrNull == true }?.jsonObject?.string("model")
        val entry = quotaPools.entries.firstOrNull { defaultModel != null && it.value.string("normalModelSlug") == defaultModel }
            ?: quotaPools.entries.firstOrNull { it.key == "codex" }
        return entry?.let { it.key to it.value }
    }
    private fun warmWindow() {
        if (!connected || !signedIn || warmThread != null) return
        val selectedKey = selectedQuotaPool()?.first ?: return
        if (warmup.reserve(owner, java.time.Instant.now().epochSecond, "$quotaAccount/$selectedKey") == null) return
        try {
            rates(rpc("account/rateLimits/read"))
            val freshData = rateLimits
            val freshPools = (freshData["rateLimitsByLimitId"] as? JsonObject)?.values?.mapNotNull { it as? JsonObject }?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(freshData["rateLimits"] as? JsonObject)
            val selected = freshPools.firstOrNull { (it.string("limitId") ?: "codex") == selectedKey }
                ?: run { warmup.finish(owner); return }
            val window = listOf("primary", "secondary").mapNotNull { selected[it] as? JsonObject }
                .firstOrNull { it["windowDurationMins"]?.jsonPrimitive?.longOrNull == 300L } ?: run { warmup.finish(owner); return }
            val used = window["usedPercent"]?.jsonPrimitive?.doubleOrNull ?: run { warmup.finish(owner); return }
            val reset = window["resetsAt"]?.jsonPrimitive?.longOrNull ?: run { warmup.finish(owner); return }
            if (reset > java.time.Instant.now().epochSecond && used > 0) { warmup.finish(owner); publish(); return }
            val directory = Path.of(com.intellij.openapi.application.PathManager.getSystemPath(), "agent-gui", "window-warmup")
            Files.createDirectories(directory)
            val model = models.firstOrNull { it.jsonObject["isDefault"]?.jsonPrimitive?.booleanOrNull == true }?.jsonObject ?: models.firstOrNull()?.jsonObject ?: run { warmup.finish(owner); return }
            val efforts = model["supportedReasoningEfforts"]!!.jsonArray.mapNotNull { it.jsonObject.string("reasoningEffort") }
            val effort = listOf("none", "minimal", "low").firstOrNull { it in efforts } ?: model.string("defaultReasoningEffort")
            val thread = rpc("thread/start", obj { put("cwd", directory.toString()); put("modelProvider", "openai"); put("model", model.string("model")); put("ephemeral", true); put("sandbox", "read-only"); put("approvalPolicy", "never") })["thread"]!!.jsonObject
            warmThread = thread.string("id")
            warmup.activity(owner, true)
            val request = warmup.launchIfIdle(owner) { process!!.rpc.request("turn/start", obj { put("threadId", warmThread); put("model", model.string("model")); put("effort", effort); put("input", buildJsonArray { add(obj { put("type", "text"); put("text", "Reply exactly OK. Do not use tools or read files.") }) }) }) } ?: run { warmThread = null; warmup.finish(owner); publish(); return }
            warmTurn = request.join()["turn"]!!.jsonObject.string("id")
            // Hidden housekeeping thread: no conversation message, tab change or success toast.
        } catch (_: Exception) { warmThread = null; warmTurn = null; warmup.finish(owner); warmup.activity(owner, queue.isNotEmpty() || sessions.values.any { it.state != "idle" }) }
    }
    private fun serverRequest(request: JsonObject) {
        val method = request.string("method")
        val thread = request["params"]?.jsonObject?.string("threadId")
        if (method in setOf("item/commandExecution/requestApproval", "item/fileChange/requestApproval") && sessions.containsKey(thread))
            approvals[request["id"]!!.jsonPrimitive.content] = request
        else { process?.rpc?.reject(request["id"]!!, "Agent GUI does not support this server request"); status = "Codex requested an unsupported interaction: $method" }
        publish()
    }
    private fun chooseFiles(id: String) {
        ApplicationManager.getApplication().invokeLater {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, true).withTitle("Attach to Codex")
            val files = FileChooser.chooseFiles(descriptor, project, null)
            saveAndAdd(id, files.toList())
        }
    }
    private fun saveAndAdd(id: String, files: List<VirtualFile>) {
        val dirty = files.any { FileDocumentManager.getInstance().isFileModified(it) }
        if (dirty && Messages.showYesNoDialog(project, "Save changes before attaching?", "Unsaved files", Messages.getQuestionIcon()) != Messages.YES) return
        files.forEach { FileDocumentManager.getInstance().getDocument(it)?.let { doc -> FileDocumentManager.getInstance().saveDocument(doc) } }
        worker.execute { runCatching { addFiles(id, files.map { Path.of(it.path) }) }.onFailure { status = it.message.orEmpty(); addError(status); publish() } }
    }

    private fun addFiles(id: String, paths: List<Path>) {
        val chat = sessions[id] ?: return
        require(chat.files.size + paths.size <= 8) { "Up to 8 attachments are supported" }
        paths.forEach { chat.files.add(attachments.snapshot(it)) }; persist(); publish()
    }
    private fun searchFiles(query: String, requestId: String) {
        val results = mutableListOf<JsonElement>(); var visited = 0
        val root = Path.of(cwd)
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && (dir.fileName.toString().startsWith('.') || dir.fileName.toString() in setOf("build", "node_modules", "dist", "target", "out"))) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = root.relativize(file).toString()
                if (attrs.isRegularFile && relative.contains(query, ignoreCase = true)) results.add(JsonPrimitive(relative))
                return if (++visited >= 10000 || results.size >= 30) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: java.io.IOException) = FileVisitResult.CONTINUE
        })
        listener?.invoke(obj { put("type", "files"); put("requestId", requestId); put("files", JsonArray(results)) })
    }
    private fun addError(text: String) {
        if (notices.lastOrNull()?.text != text) notices.add(Notice(text, "critical"))
        while (notices.size > 40) notices.removeFirst()
    }
    private fun promptHistory(id: String, request: JsonObject) {
        val chat = sessions[id] ?: return
        ensureResumed(chat)
        val result = linkedMapOf<String, String>()
        val query = request.string("query").orEmpty()
        val requestId = request.string("requestId").orEmpty()
        chat.promptRequest = requestId
        chat.promptTurns.clear()
        val connection = generation
        fun finish() {
            dev.agentgui.toolwindow.ChatPresentation.prompts(JsonArray(chat.items.values.toList())).forEach { (key, text) -> result[key] = text.take(2000) }
            listener?.invoke(obj { put("type", "promptHistoryResult"); put("threadId", id); put("requestId", request["requestId"] ?: JsonNull)
                put("prompts", buildJsonArray { result.entries.filter { it.value.contains(query, true) }.forEach { (key, text) -> add(obj { put("id", key); put("text", text) }) } }) })
        }
        fun page(cursor: String?, pages: Int) {
            if (disposed || connection != generation || !sessions.containsKey(id) || chat.promptRequest != requestId || store.activeId != id) return
            try {
                val response = rpc("thread/items/list", obj { put("threadId", id); put("limit", 100); put("sortDirection", "asc"); cursor?.let { put("cursor", it) } })
                response["data"]?.jsonArray?.forEach { value ->
                    val entry = value.jsonObject; val item = entry["item"]?.jsonObject ?: return@forEach
                    if (item.string("type") == "userMessage") {
                        val key = item.string("id") ?: return@forEach
                        chat.promptTurns[key] = entry.string("turnId").orEmpty()
                        result[key] = dev.agentgui.toolwindow.ChatPresentation.userText(item).take(2000)
                    }
                }
                val next = response.string("nextCursor")
                if (next != null && next != cursor && pages < 1000 && result.size < 10000) worker.execute { page(next, pages + 1) } else finish()
            } catch (_: Exception) { finish() }
        }
        page(null, 0)
    }
    private fun revealPrompt(id: String, itemId: String) {
        val chat = sessions[id] ?: return
        if (!chat.items.containsKey(itemId)) {
            val turn = chat.promptTurns[itemId] ?: error("Prompt is no longer available. Open history again.")
            val page = rpc("thread/items/list", obj { put("threadId", id); put("turnId", turn); put("limit", 100); put("sortDirection", "asc") })
            val items = page["data"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["item"] as? JsonObject }
            require(items.any { it.string("id") == itemId }) { "Prompt is no longer available" }
            chat.revealedItems = items
        } else chat.revealedItems = null
        publish()
        listener?.invoke(obj { put("type", "revealPrompt"); put("threadId", id); put("itemId", itemId) })
    }
    private fun installedPlugins(response: JsonObject): JsonArray = buildJsonArray {
        val seen = mutableSetOf<String>()
        response["marketplaces"]?.jsonArray?.forEach { marketplace -> marketplace.jsonObject["plugins"]?.jsonArray?.forEach { value ->
            val plugin = value.jsonObject
            val id = plugin.string("id") ?: return@forEach
            if (plugin["installed"]?.jsonPrimitive?.booleanOrNull == true && plugin["enabled"]?.jsonPrimitive?.booleanOrNull == true && plugin.string("availability") != "DISABLED_BY_ADMIN" && seen.add(id)) add(obj {
                put("id", id); put("name", plugin.string("name")); put("path", "plugin://$id"); put("enabled", true)
                put("description", (plugin["interface"] as? JsonObject)?.string("shortDescription").orEmpty())
            })
        } }
    }
    private fun refreshBranch() { branch = BranchService.current(cwd) }
    private fun persist() { store.sessions = JsonArray(sessions.values.map { chat -> obj { put("id", chat.id); put("title", chat.title); put("draft", chat.draft); put("open", chat.open); put("model", chat.model); put("effort", chat.effort); put("files", JsonArray(chat.files)) } }).toString() }
    private fun disconnected(reason: String) {
        addError(reason); connected = false; process = null; warmThread = null; warmTurn = null; warmup.finish(owner); status = reason; queue.clear(); approvals.clear()
        sessions.values.forEach { it.state = "idle"; it.turnId = null; it.resumed = false; it.stale = true }; publish()
    }
    private fun publishBatched() {
        if (publishPending) return
        publishPending = true
        worker.schedule({ publishPending = false; publish() }, 40, TimeUnit.MILLISECONDS)
    }
    private fun publish() {
        if (disposed) return
        warmup.activity(owner, warmThread != null || queue.isNotEmpty() || sessions.values.any { it.state != "idle" })
        listener?.invoke(obj {
            put("notices", JsonArray(notices.toList().takeLast(40).map { it.json() })); put("quotaWindows", quotaWindows()); put("autoWindowEnabled", warmup.state.enabled)
            put("type", "state"); put("connected", connected); put("signedIn", signedIn); put("status", status); put("version", version); put("binary", store.binary)
            put("cwd", cwd); put("branch", branch); put("activeId", store.activeId); put("models", models); put("history", history); put("historyCursor", historyCursor); put("skills", extensions); put("plugins", plugins)
            put("sessions", buildJsonArray { sessions.values.forEach { chat -> add(obj {
                put("id", chat.id); put("title", chat.title); put("draft", chat.draft); put("open", chat.open); put("model", chat.model); put("effort", chat.effort); put("state", chat.state)
                put("items", JsonArray(if (chat.id == store.activeId) transcriptWindow(chat.revealedItems, chat.items.values.toList()) else emptyList())); put("contextPercent", chat.usage.percent?.let(::JsonPrimitive) ?: JsonNull); put("contextLevel", chat.usage.level); put("contextStale", chat.stale)
                put("files", buildJsonArray { chat.files.forEach { f -> add(JsonObject(f + ("preview" to (runCatching { if (chat.id == store.activeId) attachments.preview(f) else null }.getOrNull()?.let(::JsonPrimitive) ?: JsonNull)))) } })
                put("approvals", JsonArray(approvals.values.filter { it["params"]?.jsonObject?.string("threadId") == chat.id }))
            }) } })
        })
    }
    override fun dispose() { disposed = true; listener = null; process?.close(); warmup.unregister(owner); worker.shutdownNow() }
}
