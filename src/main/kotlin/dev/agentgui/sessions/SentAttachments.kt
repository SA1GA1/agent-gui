package dev.agentgui.sessions

import dev.agentgui.codex.*
import kotlinx.serialization.json.*
import java.nio.file.Path

internal object SentAttachments {
    fun files(item:JsonObject):List<JsonObject> = (item["content"] as? JsonArray).orEmpty().mapNotNull { value ->
        val part=value as? JsonObject ?: return@mapNotNull null
        val text=part.string("text").orEmpty()
        val image=part.string("type")=="localImage"
        val path=if(image) part.string("path") else if(text.startsWith("Attached file: ")) text.lineSequence().drop(1).firstOrNull()?.removePrefix("Immutable local snapshot: ") else null
        val id=path?.let { runCatching { Path.of(it).fileName.toString() }.getOrNull() } ?: return@mapNotNull null
        if(!Regex("[a-fA-F0-9-]{36}-.+").matches(id)) return@mapNotNull null
        obj { put("id",id);put("name",if(image) id.drop(37) else text.lineSequence().first().removePrefix("Attached file: "));put("image",image) }
    }.take(10)
}
