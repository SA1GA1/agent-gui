package dev.agentgui.toolwindow

import java.awt.Image
import java.awt.datatransfer.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.text.JTextComponent

/** Swing clipboard paste uses the legacy overload; drops use TransferSupport. */
internal class ComposerTransferHandler(
    private val input:JTextComponent,
    private val onFiles:(List<File>)->Unit,
    private val onImage:(String)->Unit,
    private val onError:(String)->Unit
):TransferHandler() {
    private val original=input.transferHandler
    override fun getSourceActions(component:JComponent)=original?.getSourceActions(component) ?: NONE
    override fun exportToClipboard(component:JComponent,clipboard:Clipboard,action:Int) { original?.exportToClipboard(component,clipboard,action) }
    override fun canImport(component:JComponent,flavors:Array<DataFlavor>)=flavors.any { it==DataFlavor.javaFileListFlavor || it==DataFlavor.imageFlavor || it==DataFlavor.stringFlavor } || original?.canImport(component,flavors)==true
    override fun canImport(support:TransferSupport)=canImport(input,support.dataFlavors)
    override fun importData(component:JComponent,value:Transferable)=importData(TransferSupport(component,value))
    override fun importData(support:TransferSupport):Boolean=try {
        val value=support.transferable
        when {
            value.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> { @Suppress("UNCHECKED_CAST") val files=value.getTransferData(DataFlavor.javaFileListFlavor) as List<File>;onFiles(files);true }
            value.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                val image=value.getTransferData(DataFlavor.imageFlavor) as Image
                val width=image.getWidth(null);val height=image.getHeight(null)
                require(width>0 && height>0 && width.toLong()*height<=16_000_000) { "Clipboard image is too large" }
                val buffer=BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);val g=buffer.createGraphics()
                try { g.drawImage(image,0,0,null) } finally { g.dispose() }
                val output=ByteArrayOutputStream();ImageIO.write(buffer,"png",output)
                onImage(Base64.getEncoder().encodeToString(output.toByteArray()));true
            }
            value.isDataFlavorSupported(DataFlavor.stringFlavor) -> { input.replaceSelection(value.getTransferData(DataFlavor.stringFlavor) as String);true }
            else -> original?.importData(support)==true
        }
    } catch(e:Exception) { onError(e.message ?: "Could not paste attachment");false }
}
