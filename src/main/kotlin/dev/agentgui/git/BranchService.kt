package dev.agentgui.git

import java.io.File
import java.util.concurrent.TimeUnit

object BranchService {
    fun current(cwd: String): String {
        fun git(vararg args: String): String? = runCatching {
            val p = ProcessBuilder(listOf("git", "-C", cwd) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); return@runCatching null }
            if (p.exitValue() != 0) null else p.inputStream.bufferedReader().readText().trim().ifEmpty { null }
        }.getOrNull()
        if (!File(cwd).isDirectory) return "Нет каталога"
        return git("symbolic-ref", "--short", "HEAD")
            ?: git("rev-parse", "--short", "HEAD")?.let { "detached · $it" } ?: "Без Git"
    }
}
