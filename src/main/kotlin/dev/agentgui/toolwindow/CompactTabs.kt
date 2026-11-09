package dev.agentgui.toolwindow

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/** One row: shrink to 80px, then scroll; the new-chat control lives outside the viewport. */
internal class CompactTabs : JPanel(null), Scrollable {
    init { isOpaque=false }
    companion object {
        fun tabWidth(available: Int, count: Int)=if(count<=0) 144 else ((available-(count-1)*6)/count).coerceIn(80,144)
    }
    private fun itemWidth()=tabWidth((parent?.width ?: width).coerceAtLeast(80),componentCount)
    override fun getPreferredSize()=Dimension(componentCount*itemWidth()+(componentCount-1).coerceAtLeast(0)*6,30)
    override fun doLayout() { val w=itemWidth(); components.forEachIndexed { i,c -> c.setBounds(i*(w+6),0,w,30) } }
    override fun getPreferredScrollableViewportSize()=Dimension(300,30)
    override fun getScrollableTracksViewportWidth()=preferredSize.width<=(parent?.width ?: width)
    override fun getScrollableTracksViewportHeight()=true
    override fun getScrollableUnitIncrement(r: Rectangle,o: Int,d: Int)=80
    override fun getScrollableBlockIncrement(r: Rectangle,o: Int,d: Int)=(r.width-80).coerceAtLeast(80)
}

internal fun JScrollPane.hoverHorizontalScroll() {
    horizontalScrollBarPolicy=ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    verticalScrollBarPolicy=ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
    horizontalScrollBar.preferredSize=Dimension(0,5)
    horizontalScrollBar.isOpaque=false
    var hovering=false
    horizontalScrollBar.setUI(object: javax.swing.plaf.basic.BasicScrollBarUI() {
        override fun createDecreaseButton(o: Int)=JButton().apply { preferredSize=Dimension(0,0) }
        override fun createIncreaseButton(o: Int)=createDecreaseButton(o)
        override fun paintTrack(g: Graphics,c: JComponent,r: Rectangle) {}
        override fun paintThumb(g: Graphics,c: JComponent,r: Rectangle) { if(hovering) { g.color=CHAT_MUTED; (g as Graphics2D).setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.fillRoundRect(r.x,r.y,r.width,r.height,5,5) } }
    })
    val listener=object: MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) { hovering=true; horizontalScrollBar.repaint() }
        override fun mouseExited(e: MouseEvent) {
            val point=SwingUtilities.convertPoint(e.component,e.point,this@hoverHorizontalScroll)
            if(!contains(point)) { hovering=false; horizontalScrollBar.repaint() }
        }
        override fun mouseWheelMoved(e: java.awt.event.MouseWheelEvent) {
            if(viewport.view.preferredSize.width>viewport.width) { horizontalScrollBar.value+=e.wheelRotation*40; e.consume() }
        }
    }
    fun register(c: Component) { c.addMouseListener(listener); if(c is Container) { c.components.forEach { register(it) }; c.addContainerListener(object: java.awt.event.ContainerAdapter() { override fun componentAdded(e: java.awt.event.ContainerEvent) { register(e.child) } }) } }
    register(this); addMouseWheelListener(listener)
    // Track entering the viewport itself after tabs are replaced, too.
    viewport.addMouseListener(listener)
    putClientProperty("tabHoverListener",listener)
}
