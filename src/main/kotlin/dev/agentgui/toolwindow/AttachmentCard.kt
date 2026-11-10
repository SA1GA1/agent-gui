package dev.agentgui.toolwindow

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.border.EmptyBorder

/** A single bounded row: thumbnails never increase the height of the composer. */
internal class AttachmentRow : JPanel(FlowLayout(FlowLayout.LEFT,6,3)), Scrollable {
    init { isOpaque=false }
    override fun getPreferredSize()=Dimension(components.sumOf { it.preferredSize.width+6 }+6,78)
    override fun getPreferredScrollableViewportSize()=Dimension(300,78)
    override fun getScrollableTracksViewportWidth()=false
    override fun getScrollableTracksViewportHeight()=true
    override fun getScrollableUnitIncrement(r:Rectangle,o:Int,d:Int)=40
    override fun getScrollableBlockIncrement(r:Rectangle,o:Int,d:Int)=maxOf(40,r.width-80)
}

internal class AttachmentCard(name:String, private val preview:BufferedImage?, remove:()->Unit) : JPanel(BorderLayout(6,0)) {
    init {
        isOpaque=false; preferredSize=Dimension(if(preview==null) 132 else 72,72)
        border=EmptyBorder(5,8,5,4); toolTipText=name
        val close=RoundCloseButton("Remove $name",remove)
        if(preview==null) {
            add(JLabel(com.intellij.icons.AllIcons.FileTypes.Text).apply { border=EmptyBorder(0,0,0,1) },BorderLayout.WEST)
            val title=JLabel(if(name.lastIndexOf('.')>0) name.substringBeforeLast('.') else name).apply { font=chatFont(12f); foreground=CHAT_FG }
            val format=JLabel((if(name.lastIndexOf('.')>0) name.substringAfterLast('.').ifEmpty { "FILE" } else "FILE").uppercase()).apply { font=chatFont(11f); foreground=CHAT_MUTED }
            add(transparentPanel(GridLayout(2,1,0,1)).apply { border=EmptyBorder(15,0,15,0); add(title);add(format) })
        }
        add(transparentPanel(BorderLayout()).apply { add(close,BorderLayout.NORTH) },BorderLayout.EAST)
        getAccessibleContext().accessibleName=name
    }
    override fun paintComponent(g:Graphics) {
        val c=g.create() as Graphics2D
        try {
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            c.clip(RoundRectangle2D.Float(0f,0f,width.toFloat(),height.toFloat(),14f,14f))
            c.color=Color(0x35373B);c.fillRect(0,0,width,height)
            preview?.let {
                val scale=maxOf(width.toDouble()/it.width,height.toDouble()/it.height)
                val w=(it.width*scale).toInt();val h=(it.height*scale).toInt()
                c.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                c.drawImage(it,(width-w)/2,(height-h)/2,w,h,null)
                c.color=Color(0,0,0,85);c.fillOval(width-27,3,23,23)
            }
        } finally { c.dispose() }
        super.paintComponent(g)
    }
}
