package dev.agentgui
import dev.agentgui.sessions.SentPreviewCache
import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
class SentPreviewCacheTest {
 @Test fun `streaming reuses seventeen previews and releases thumbnails outside the visible window`() {
  var reads=0;val cache=SentPreviewCache { reads++;"image" }
  val files=(1..17).map { id -> obj { put("id",id.toString()) } }
  cache.retain(files);repeat(5) { files.forEach(cache::preview) };assertEquals(17,reads)
  cache.retain(emptyList());assertNull(cache.preview(files.first()));assertEquals(17,reads)
  cache.retain(files);files.forEach(cache::preview);assertEquals(34,reads)
 }
}
