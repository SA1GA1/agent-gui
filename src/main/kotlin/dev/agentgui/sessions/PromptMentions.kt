package dev.agentgui.sessions

import dev.agentgui.codex.*
import kotlinx.serialization.json.*

/** Only complete, selected inline labels carry capability identities. Typed lookalikes are plain text. */
internal object PromptMentions {
    fun valid(text:String,value:JsonElement?):JsonArray {
        var end=0
        val entries=(value as? JsonArray).orEmpty().take(100).mapNotNull { it as? JsonObject }.sortedBy { it["start"]?.jsonPrimitive?.intOrNull ?: -1 }
        return JsonArray(entries.filter { m ->
            val start=m["start"]?.jsonPrimitive?.intOrNull ?: return@filter false
            val until=m["end"]?.jsonPrimitive?.intOrNull ?: return@filter false
            val kind=m.string("kind"); val name=m.string("name") ?: return@filter false
            val label=(if(kind=="file") "@" else "/")+name.removePrefix("/").removePrefix("@")
            val matches=kind in setOf("skill","plugin","file") && start>=end && until>start && until<=text.length && name.length<=1024 && (text.substring(start,until)==label || kind=="plugin" && text.substring(start,until)=="@"+name)
            if(matches)end=until
            matches
        })
    }
    fun migrateDraft(text:String,mentions:JsonArray):String {
        val chars=text.toCharArray()
        mentions.forEach { value -> val mention=value.jsonObject
            if(mention.string("kind")=="plugin") mention["start"]?.jsonPrimitive?.intOrNull?.let { if(it in chars.indices && chars[it]=='@') chars[it]='/' }
        }
        return chars.concatToString()
    }
    fun inputs(text:String,value:JsonElement?,skills:JsonArray,plugins:JsonArray):List<JsonObject> = valid(text,value).mapNotNull { v ->
        val m=v.jsonObject;val name=m.string("name").orEmpty()
        when(m.string("kind")) {
            "skill" -> {
                val selected=skills.map { it.jsonObject }.firstOrNull { it.string("name")==name && it["enabled"]?.jsonPrimitive?.booleanOrNull!=false && (m.string("path")==null || m.string("path")==it.string("path")) } ?: error("Skill unavailable: $name")
                obj { put("type","skill");put("name",name);put("path",selected.string("path")) }
            }
            "plugin" -> {
                val selected=plugins.map { it.jsonObject }.firstOrNull { it.string("name")==name && it.string("path")==m.string("path") && it["enabled"]?.jsonPrimitive?.booleanOrNull!=false } ?: error("Plugin unavailable: $name")
                obj { put("type","mention");put("name",name);put("path",selected.string("path")) }
            }
            else -> null
        }
    }.distinctBy { it.toString() }
}
