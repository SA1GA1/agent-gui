package dev.agentgui

import dev.agentgui.toolwindow.MathMarkup
import dev.agentgui.toolwindow.MathRendering
import dev.agentgui.toolwindow.MathEditorKit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import java.awt.image.BufferedImage

class MathRenderingTest {
    @Test fun `transfer function renders to a local icon`() {
        val source="W(p)=\\frac{Y(p)}{X(p)}=\\frac{1{,}53p^2+16{,}73p+7{,}93}{0{,}43p^4+p^3+1{,}93p^2}"
        val icon=MathRendering.icon(source,true)
        assertNotNull(icon)
        assertTrue(icon!!.iconWidth>100)
        assertTrue(icon.iconHeight>20)
    }
    @Test fun `files images macro definitions and excessive formulas are rejected`() {
        for(source in listOf("\\includegraphics{https://bad.invalid/a}","\\input{/etc/passwd}","\\newcommand{\\x}{a}","\\def\\x{a}","\\jlmDynamic{java.lang.System}","x".repeat(4097))) {
            assertFalse(MathRendering.allowed(source),source)
            assertNull(MathRendering.icon(source,true),source)
        }
        assertNull(MathRendering.icon("\\frac{",true))
    }
    @Test fun `scanner handles multiline math and leaves fenced and inline code intact`() {
        val text="\\[\nx^2\n\\]\n`\\(code\\)`\n~~~latex\n${'$'}x${'$'}\n~~~\n${'$'}y${'$'}"
        val found=mutableListOf<String>()
        val output=MathMarkup.replace(text) { source,_ -> found+=source;"FORMULA" }
        assertEquals(listOf("\nx^2\n","y"),found)
        assertTrue(output.contains("`\\(code\\)`"))
        assertTrue(output.contains("${'$'}x${'$'}"))
    }
    @Test fun `native HTML view measures and paints math without remote image loading`() {
        SwingUtilities.invokeAndWait {
            val editor=JEditorPane().apply { editorKit=MathEditorKit();text="<html><body>${MathRendering.html("\\frac{1}{2}",true)}</body></html>";setSize(400,400) }
            assertTrue(editor.preferredSize.height>20)
            fun inspect(context:javax.accessibility.AccessibleContext) {
                context.accessibleIcon?.forEach { assertFalse(it.accessibleIconDescription.isNullOrBlank()) }
                for(index in 0 until context.accessibleChildrenCount) context.getAccessibleChild(index)?.accessibleContext?.let(::inspect)
            }
            inspect(editor.accessibleContext)
            val image=BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB)
            val graphics=image.createGraphics()
            try { editor.paint(graphics) } finally { graphics.dispose() }
            assertTrue((0 until 100).any { y -> (0 until 100).any { x -> image.getRGB(x,y)!=0 } })
        }
    }
}
