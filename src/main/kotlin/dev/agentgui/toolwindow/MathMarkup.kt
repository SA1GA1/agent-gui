package dev.agentgui.toolwindow

/** Finds formulas before CommonMark treats model-style delimiters as escaped punctuation. */
internal object MathMarkup {
    private val fence=Regex(" {0,3}(`{3,}|~{3,})")
    fun replace(text: String, formula: (String, Boolean) -> String): String {
        val out=StringBuilder()
        var i=0
        while(i<text.length) {
            if(i==0 || text[i-1]=='\n') {
                val marker=fence.matchAt(text,i)
                if(marker!=null) {
                    val run=marker.groupValues[1]
                    val openingEnd=text.indexOf('\n',i).let { if(it<0) text.length else it+1 }
                    val close=Regex("(?m)^ {0,3}${Regex.escape(run[0].toString())}{${run.length},}[ \\t]*(?:\\n|$)").find(text,openingEnd)
                    val end=close?.range?.last?.plus(1) ?: text.length
                    out.append(text,i,end);i=end;continue
                }
            }
            if(text[i]=='`') {
                var runEnd=i+1;while(runEnd<text.length && text[runEnd]=='`')runEnd++
                val run=text.substring(i,runEnd)
                val end=text.indexOf(run,i+run.length)
                if(end>=0) { out.append(text,i,end+run.length);i=end+run.length;continue }
            }
            val delimiter=when {
                text.startsWith("\\[",i) -> "\\[" to "\\]"
                text.startsWith("\\(",i) -> "\\(" to "\\)"
                text.startsWith("$$",i) -> "$$" to "$$"
                text[i]=='$' && text.getOrNull(i-1)!='$' -> "$" to "$"
                else -> null
            }
            if(delimiter!=null) {
                val (open,close)=delimiter
                val start=i+open.length
                val end=text.indexOf(close,start)
                val single=open=="$"
                if(end>start && (!single || (!text[start].isWhitespace() && !text[end-1].isWhitespace() && text.getOrNull(end+1)?.isDigit()!=true && '\n' !in text.substring(start,end)))) {
                    out.append(formula(text.substring(start,end),open=="\\[" || open=="$$"));i=end+close.length;continue
                }
            }
            if(text[i]=='\\' && i+1<text.length) {out.append(text,i,i+2);i+=2;continue}
            out.append(text[i++])
        }
        return out.toString()
    }
}
