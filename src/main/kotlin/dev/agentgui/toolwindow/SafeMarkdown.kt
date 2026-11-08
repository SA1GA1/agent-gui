package dev.agentgui.toolwindow

import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlRenderer

object SafeMarkdown {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true)
        .nodeRendererFactory { context -> object : NodeRenderer {
            override fun getNodeTypes(): Set<Class<out Node>> = setOf(Image::class.java)
            override fun render(node: Node) { context.writer.text("[Изображение]") }
        } }.build()
    fun render(text: String): String = renderer.render(parser.parse(text))
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
