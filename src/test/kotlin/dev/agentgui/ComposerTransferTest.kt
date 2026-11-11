package dev.agentgui
import dev.agentgui.toolwindow.ComposerTransferHandler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.datatransfer.*
import java.awt.image.BufferedImage
import javax.swing.JTextPane

class ComposerTransferTest {
    @Test fun `legacy clipboard entry point imports images like modern drops`() {
        val image=BufferedImage(24,16,BufferedImage.TYPE_INT_RGB)
        val transferable=object:Transferable {
            override fun getTransferDataFlavors()=arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor:DataFlavor)=flavor==DataFlavor.imageFlavor
            override fun getTransferData(flavor:DataFlavor):Any=image
        }
        val input=JTextPane();val images=mutableListOf<String>()
        val handler=ComposerTransferHandler(input,{}, { images.add(it) }, { throw AssertionError(it) })
        assertTrue(handler.canImport(input,transferable.transferDataFlavors))
        assertTrue(handler.importData(input,transferable));assertEquals(1,images.size)
        val decoded=javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(images.single())))
        assertEquals(24,decoded.width);assertEquals(16,decoded.height)
        assertTrue(handler.importData(javax.swing.TransferHandler.TransferSupport(input,StringSelection("plain paste"))));assertEquals("plain paste",input.text)
    }
}
