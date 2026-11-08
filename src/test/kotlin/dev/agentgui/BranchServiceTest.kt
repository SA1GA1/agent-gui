package dev.agentgui

import dev.agentgui.git.BranchService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class BranchServiceTest {
    @TempDir lateinit var root: Path
    private fun git(vararg args: String) {
        val p = ProcessBuilder(listOf("git", "-C", root.toString()) + args).redirectErrorStream(true).start()
        assertTrue(p.waitFor(5, TimeUnit.SECONDS)); assertEquals(0, p.exitValue(), p.inputStream.bufferedReader().readText())
    }
    @Test fun `detects branch changes and detached head in nested working directory`() {
        git("init", "-b", "начало")
        val child = root.resolve("папка с пробелом").toFile().apply { mkdirs() }
        assertEquals("начало", BranchService.current(child.path))
        git("-c", "user.name=Test", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "fixture")
        git("switch", "-c", "next")
        assertEquals("next", BranchService.current(child.path))
        git("checkout", "--detach")
        assertTrue(BranchService.current(child.path).startsWith("detached · "))
    }
    @Test fun `unknown Git state is not represented as a branch`() {
        assertEquals("No Git repository", BranchService.current(root.toString()))
    }
}
