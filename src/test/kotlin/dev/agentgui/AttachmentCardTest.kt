package dev.agentgui

import dev.agentgui.toolwindow.AttachmentCard
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.*
import java.awt.Rectangle
import java.awt.image.BufferedImage

class AttachmentCardTest {
    @Test fun `rounded card preserves the viewport clip during horizontal scrolling`() {
        SwingUtilities.invokeAndWait {
            val card=AttachmentCard("report.docx",null) {}.apply { setSize(132,72) }
            val image=BufferedImage(132,72,BufferedImage.TYPE_INT_ARGB)
            val g=image.createGraphics();g.clip=Rectangle(12,0,25,72)
            try { card.paint(g) } finally { g.dispose() }
            assertEquals(0,image.getRGB(2,30),"A scrolled card must never paint outside its viewport")
            assertNotEquals(0,image.getRGB(20,30))
        }
    }
}
