package dev.agentgui.toolwindow

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Shape
import java.util.Base64
import javax.swing.Icon
import javax.swing.text.Element
import javax.swing.text.Position
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit

/** Local-only rendering. Unknown commands never reach TeX's extensible macro parser. */
internal object MathRendering {
    private val commands=("frac dfrac tfrac sqrt left right big Big bigg Bigg overline underline widehat widetilde hat bar vec dot ddot " +
        "sum prod int iint iiint oint lim min max sup inf sin cos tan cot sec csc arcsin arccos arctan sinh cosh tanh log ln exp det gcd " +
        "alpha beta gamma delta epsilon varepsilon zeta eta theta vartheta iota kappa lambda mu nu xi pi varpi rho varrho sigma varsigma tau upsilon phi varphi chi psi omega " +
        "Gamma Delta Theta Lambda Xi Pi Sigma Upsilon Phi Psi Omega " +
        "infty partial nabla pm mp times div cdot ast circ bullet le leq ge geq ne neq approx sim simeq equiv propto " +
        "in notin subset supset subseteq supseteq cup cap emptyset forall exists neg land lor to rightarrow leftarrow leftrightarrow Rightarrow Leftarrow Leftrightarrow " +
        "ldots cdots vdots ddots quad qquad text textrm textit textbf mathrm mathit mathbf mathsf mathtt mathcal mathbb operatorname " +
        "begin end cases matrix pmatrix bmatrix Bmatrix vmatrix Vmatrix aligned array displaystyle textstyle scriptstyle limits nolimits underbrace overbrace binom choose mod bmod pmod").split(' ').toSet()
    private val cache=object : LinkedHashMap<String,Icon>(32,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Icon>?)=size>128
    }
    fun allowed(source: String): Boolean = source.length<=4096 && balanced(source) &&
        Regex("\\\\([A-Za-z]+|.)").findAll(source).all { val command=it.groupValues[1]; command in commands || (command.length==1 && command[0] in " ,;:!{}_%#$&|\\") } &&
        Regex("\\\\begin\\s*\\{([^}]+)}").findAll(source).all { it.groupValues[1] in setOf("cases","matrix","pmatrix","bmatrix","Bmatrix","vmatrix","Vmatrix","aligned","array") }
    private fun balanced(source: String): Boolean {
        var depth=0
        var i=0
        while(i<source.length) {
            if(source[i]=='\\') { i+=2;continue }
            if(source[i]=='{') depth++
            if(source[i]=='}' && --depth<0) return false
            if(depth>64) return false
            i++
        }
        return depth==0
    }
    @Synchronized fun icon(source: String, display: Boolean): Icon? {
        if(!allowed(source)) return null
        val key="$display:$source"
        cache[key]?.let { return it }
        return try {
            TeXFormula(source).createTeXIcon(if(display) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT,13f).apply {
                setForeground(Color(0xE7E8EB))
            }.takeIf { it.iconWidth<=4096 && it.iconHeight<=2048 }?.also { cache[key]=it }
        } catch (_: Exception) { null }
    }
    fun html(source: String, display: Boolean): String? {
        if(icon(source,display)==null) return null
        val encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(source.toByteArray(Charsets.UTF_8))
        val image="<img src='agent-math:${if(display) "d" else "i"}:$encoded' alt='${SafeMarkdown.escape(source)}'>"
        return if(display) "<div style='margin:8px 0'>$image</div>" else image
    }
}

internal class MathEditorKit : HTMLEditorKit() {
    override fun getViewFactory(): ViewFactory = ViewFactory { element ->
        val src=element.attributes.getAttribute(HTML.Attribute.SRC)?.toString().orEmpty()
        if(element.attributes.getAttribute(javax.swing.text.StyleConstants.NameAttribute)==HTML.Tag.IMG && src.startsWith("agent-math:")) MathView(element,src)
        else super.getViewFactory().create(element)
    }
    private class MathView(element: Element, source: String): javax.swing.text.html.ImageView(element) {
        private val icon=try {
            val parts=source.split(':',limit=3)
            MathRendering.icon(String(Base64.getUrlDecoder().decode(parts[2]),Charsets.UTF_8),parts[1]=="d")
        } catch (_: Exception) { null }
        override fun getImageURL():java.net.URL?=null
        override fun getPreferredSpan(axis: Int)= (if(axis==X_AXIS) icon?.iconWidth ?: 1 else icon?.iconHeight ?: 13).toFloat()
        override fun getAlignment(axis: Int)=if(axis==Y_AXIS) .8f else super.getAlignment(axis)
        override fun paint(g: Graphics, allocation: Shape) { val r=allocation.bounds;icon?.paintIcon(container,g,r.x,r.y) }
        override fun modelToView(pos: Int, allocation: Shape, bias: Position.Bias): Shape = allocation.bounds.let { Rectangle(if(pos>=endOffset) it.x+it.width else it.x,it.y,0,it.height) }
        override fun viewToModel(x: Float,y: Float,allocation: Shape,bias: Array<Position.Bias>): Int {bias[0]=Position.Bias.Forward;return if(x<allocation.bounds.centerX) startOffset else endOffset}
    }
}
