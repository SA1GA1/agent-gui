package dev.agentgui.toolwindow

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlRenderer
import java.util.Base64

object SafeMarkdown {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true)
        .nodeRendererFactory { context -> object : NodeRenderer {
            override fun getNodeTypes(): Set<Class<out Node>> = setOf(Image::class.java,FencedCodeBlock::class.java)
            override fun render(node: Node) {
                if(node is Image) { context.writer.text("[Image]"); return }
                val code=node as FencedCodeBlock
                val language=escape(code.info.orEmpty().substringBefore(' ').take(30).replaceFirstChar { it.uppercase() })
                val copy=Base64.getUrlEncoder().withoutPadding().encodeToString(code.literal.toByteArray(Charsets.UTF_8))
                val escaped=escape(code.literal)
                val highlighted=Regex("\\b(fun|val|var|class|return|if|else|when|true|false|null|import|def|const|let|function)\\b").replace(escaped) { "<font color='#bf9cf1'>${it.value}</font>" }
                context.writer.raw("<table class='code' width='100%' cellspacing='0' cellpadding='8' bgcolor='#222427'><tr><td><font color='#b2b7c0'>$language</font></td><td align='right'><a class='copy' href='agent-copy:$copy'>Copy ▢</a></td></tr><tr><td colspan='2'><pre><code>$highlighted</code></pre></td></tr></table>")
            }
        } }.build()
    fun render(text: String): String = renderer.render(parser.parse(text))
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'","&#39;")
}
