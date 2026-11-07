package dev.agentgui.codex

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class CodexProcess(
    binary: String, cwd: String,
    onEvent: (String, JsonObject) -> Unit,
    onRequest: (JsonObject) -> Unit,
    private val onExit: (String) -> Unit,
) : AutoCloseable {
    private val process = ProcessBuilder(binary, "-c", "model_provider=\"openai\"", "app-server", "--listen", "stdio://")
        .directory(Path.of(cwd).toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
    val rpc = JsonRpcClient({ line -> synchronized(writer) { writer.write(line); writer.newLine(); writer.flush() } }, onEvent, onRequest)
    @Volatile private var disposed = false
    private val reader = Thread({
        var reason = "Codex завершился. Подключитесь снова; сообщение не отправлено повторно."
        try { readJsonLines(process.inputStream.reader(Charsets.UTF_8), rpc::receive) }
        catch (e: Exception) { reason = "Соединение с Codex прервано: ${e.javaClass.simpleName}" }
        finally { rpc.close(); if (!disposed) { close(); onExit(reason) } }
    }, "agent-gui-stdio").apply { isDaemon = true; start() }

    fun initialize(): JsonObject {
        val result = rpc.request("initialize", obj {
            put("clientInfo", obj { put("name", "agent_gui"); put("title", "Agent GUI"); put("version", "0.1.0") })
        }).join()
        rpc.notify("initialized")
        return result
    }
    override fun close() {
        disposed = true
        rpc.close()
        process.descendants().forEach { it.destroy() }
        process.destroy()
        // Never block the IDE event thread waiting for a child.
        process.onExit().orTimeout(2, TimeUnit.SECONDS).exceptionally { process.destroyForcibly(); process }
    }
    companion object {
        fun discover(configured: String): String {
            val candidates = if (configured.isNotBlank()) listOf(configured) else
                (System.getenv("PATH") ?: "").split(java.io.File.pathSeparator).map { "$it/codex" } +
                    listOf("/opt/homebrew/bin/codex", "/usr/local/bin/codex", "${System.getProperty("user.home")}/.local/bin/codex")
            return candidates.firstOrNull { Files.isExecutable(Path.of(it)) && !Files.isDirectory(Path.of(it)) }
                ?: error("Codex CLI не найден. Установите его и укажите путь в настройках панели.")
        }
        fun version(binary: String): String {
            val p = ProcessBuilder(binary, "--version").redirectErrorStream(true).start()
            if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); error("Codex не ответил на --version") }
            check(p.exitValue() == 0) { "Не удалось проверить версию Codex" }
            return p.inputStream.bufferedReader().readText().trim().take(100)
        }
    }
}
