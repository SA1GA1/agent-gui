package dev.agentgui.sessions
import dev.agentgui.codex.string
import kotlinx.serialization.json.JsonObject
/** Only the native transcript's bounded visible window owns thumbnails. */
internal class SentPreviewCache(private val load:(JsonObject)->String?) {
 private val values=mutableMapOf<String,String?>()
 private var visible=emptySet<String>()
 fun retain(files:List<JsonObject>) { visible=files.mapNotNull { it.string("id") }.takeLast(1100).toSet();values.keys.retainAll(visible) }
 fun preview(file:JsonObject):String? {
  val id=file.string("id") ?: return null
  if(id !in visible)return null
  if(!values.containsKey(id))values[id]=runCatching { load(file) }.getOrNull()
  return values[id]
 }
}
