package dev.agentgui.toolwindow

internal object PromptFences {
    fun ranges(text: String): List<IntRange> {
        val result=mutableListOf<IntRange>();var start:Int?=null;var marker=""
        Regex("(?m)^ {0,3}(`{3,}|~{3,})([^\\n]*)$").findAll(text).forEach { match ->
            val fence=match.groupValues[1]
            if(start==null) { start=match.range.first;marker=fence }
            else if(fence.first()==marker.first() && fence.length>=marker.length && match.groupValues[2].isBlank()) { result.add(start!!..match.range.last);start=null }
        }
        start?.let { result.add(it..text.length) };return result
    }
    fun inside(text:String,caret:Int)=ranges(text).any { caret>it.first && caret<=it.last && text.substring(it.first,caret).contains('\n') }
    fun enter(text:String,caret:Int):String? {
        val line=text.substring(0,caret).substringAfterLast('\n')
        if(Regex(" {0,3}`{3,}[A-Za-z0-9_+-]*").matches(line) && ranges(text).any { it.first==caret-line.length }) return if(ranges(text).first { it.first==caret-line.length }.last<text.length) "\n" else "\n\n"+line.trim().takeWhile { it=='`' }
        return if(inside(text,caret)) "\n" else null
    }
}
