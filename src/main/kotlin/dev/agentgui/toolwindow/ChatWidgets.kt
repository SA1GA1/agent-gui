package dev.agentgui.toolwindow

import com.intellij.ui.components.JBTextArea
import java.awt.*
import java.awt.geom.Path2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Arc2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

internal val CHAT_BG = Color(0x1B1C1E)
internal val CHAT_FG = Color(0xE7E8EB)
internal val CHAT_MUTED = Color(0xA8ABB1)
internal val COMPOSER_BG = Color(0x28292C)
internal class RoundedPanel(private val fill: Color, private val outline: Color? = null) : JPanel(BorderLayout()) {
    private var appearance=1f
    private var entrance: Timer?=null
    init { isOpaque=false; border=EmptyBorder(10,12,10,12) }
    fun animateIn() {
        entrance?.stop(); appearance=0f
        val started=System.nanoTime()
        entrance=Timer(16) { event ->
            appearance=((System.nanoTime()-started)/180_000_000.0).toFloat().coerceIn(0f,1f)
            repaint(); if(appearance>=1f) (event.source as Timer).stop()
        }.apply { start() }
    }
    override fun paint(g: Graphics) {
        val copy=g.create() as Graphics2D
        try { copy.composite=AlphaComposite.SrcOver.derive(appearance); copy.translate(0,((1f-appearance)*5).toInt()); super.paint(copy) } finally { copy.dispose() }
    }
    override fun paintComponent(g: Graphics) {
        val copy=g.create() as Graphics2D
        try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.color=fill; copy.fillRoundRect(0,0,width-1,height-1,22,22)
            outline?.let { copy.color=it; copy.drawRoundRect(0,0,width-1,height-1,22,22) }
        } finally { copy.dispose() }
        super.paintComponent(g)
    }
    override fun removeNotify() { entrance?.stop(); super.removeNotify() }
}
internal class ChatIcon(private val kind: String, private val color: Color = CHAT_FG, private val size: Int = 18) : Icon {
    override fun getIconWidth()=size
    override fun getIconHeight()=size
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val p=g.create() as Graphics2D
        try {
            p.translate(x,y); p.scale(size/20.0,size/20.0); p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            p.color=if(c?.isEnabled==false) CHAT_MUTED else color; p.stroke=BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
            when(kind) {
                "history" -> { p.draw(Ellipse2D.Double(2.5,2.5,15.0,15.0)); p.drawLine(10,5,10,10); p.drawLine(10,10,13,12) }
                "clip" -> { val path=Path2D.Double(); path.moveTo(17.0,10.0); path.lineTo(9.0,18.0); path.curveTo(5.0,22.0,0.0,16.0,4.0,12.0); path.lineTo(13.0,3.0); path.curveTo(17.0,-1.0,21.0,5.0,17.0,9.0); path.lineTo(9.0,17.0); path.curveTo(7.0,19.0,4.0,16.0,6.0,14.0); path.lineTo(13.0,7.0); p.scale(.86,.86); p.translate(1.0,0.0); p.draw(path) }
                "quota" -> { p.draw(Ellipse2D.Double(2.5,2.5,15.0,15.0)); p.fill(Arc2D.Double(2.5,2.5,15.0,15.0,0.0,90.0,Arc2D.PIE)) }
                "arrow" -> { p.stroke=BasicStroke(2.2f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND); p.drawLine(10,16,10,4); p.drawLine(10,4,5,9); p.drawLine(10,4,15,9) }
                "stop" -> p.fillRoundRect(5,5,10,10,2,2)
                "close" -> { p.drawLine(6,6,14,14); p.drawLine(6,14,14,6) }
                "plus" -> { p.drawLine(10,4,10,16); p.drawLine(4,10,16,10) }
                "warning" -> { val shape=Path2D.Double(); shape.moveTo(10.0,2.0); shape.lineTo(18.0,17.0); shape.quadTo(18.5,18.0,17.0,18.0); shape.lineTo(3.0,18.0); shape.quadTo(1.5,18.0,2.0,16.5); shape.closePath(); p.draw(shape); p.drawLine(10,7,10,12); p.fillOval(9,14,2,2) }
                "info" -> { p.drawRoundRect(2,2,16,16,7,7); p.drawLine(10,9,10,15); p.fillOval(9,5,2,2) }
            }
        } finally { p.dispose() }
    }
}
internal class HoverButton(text: String, icon: Icon?) : JButton(text,icon) {
    var outlined=false
    var selectedChat=false
    private var hover=0f
    private var target=0f
    private val transition=Timer(16) { event ->
        hover+=(target-hover)*0.35f
        if(kotlin.math.abs(target-hover)<0.025f) { hover=target; (event.source as Timer).stop() }; repaint()
    }
    init {
        font=font.deriveFont(Font.PLAIN,13f)
        addMouseListener(object: MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { target=1f; transition.start() }
            override fun mouseExited(e: MouseEvent) { target=0f; transition.start() }
        })
    }
    override fun paintComponent(g: Graphics) {
        if(outlined) { val copy=g.create() as Graphics2D
            try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                if(selectedChat) { copy.color=Color(0x303236); copy.fillRoundRect(0,0,width-1,height-1,12,12) }
                copy.color=if(selectedChat) Color(0x62656C) else Color(0x383B41); copy.drawRoundRect(0,0,width-1,height-1,12,12)
            } finally { copy.dispose() }
        }
        if(hover>0f && isEnabled) { val copy=g.create() as Graphics2D
            try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.color=Color(255,255,255,(hover*18).toInt()); copy.fillRoundRect(0,0,width-1,height-1,12,12) } finally { copy.dispose() }
        }; super.paintComponent(g)
    }
    override fun removeNotify() { transition.stop(); super.removeNotify() }
}
internal fun flatButton(text: String = "", icon: Icon? = null, tooltip: String = text, action: () -> Unit): JButton = HoverButton(text,icon).apply {
    isOpaque=false; isContentAreaFilled=false; isBorderPainted=false; isFocusPainted=false
    border=EmptyBorder(5,7,5,7); foreground=CHAT_FG; cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    toolTipText=tooltip; if(text.isBlank()) accessibleContext.accessibleName=tooltip; addActionListener { action() }
}
internal class SendButton(action: () -> Unit) : JButton() {
    private var phase=0
    private val activity=Timer(40) { phase=(phase+12)%360; repaint() }
    var working=false
        set(value) {
            toolTipText=if(value) "Stop" else "Send message"; getAccessibleContext().accessibleName=toolTipText
            if(field==value) return
            field=value; if(value && isShowing) activity.start() else activity.stop(); repaint()
        }
    init { preferredSize=Dimension(28,28); minimumSize=preferredSize; maximumSize=preferredSize; isContentAreaFilled=false; isBorderPainted=false; isFocusPainted=false; working=false; cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR); addActionListener { action() } }
    override fun paintComponent(g: Graphics) {
        val copy=g.create() as Graphics2D
        try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.color=if(isEnabled) Color(0xECEDEF) else Color(0x777A80)
            val diameter=minOf(width,height,28)-1; val left=(width-diameter)/2; val top=(height-diameter)/2; copy.fillOval(left,top,diameter,diameter)
            ChatIcon(if(working) "stop" else "arrow",CHAT_BG,18).paintIcon(this,copy,(width-18)/2,(height-18)/2)
            if(working) { copy.color=CHAT_MUTED; copy.stroke=BasicStroke(1.5f); copy.drawArc(left-2,top-2,diameter+4,diameter+4,phase,85) }
        } finally { copy.dispose() }
    }
    override fun addNotify() { super.addNotify(); if(working) activity.start() }
    override fun removeNotify() { activity.stop(); super.removeNotify() }
}
internal class ComposerTextArea : JBTextArea(1,25) {
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if(text.isEmpty()) { val copy=g.create(); try { copy.color=CHAT_MUTED; copy.font=font; copy.drawString("Ask Codex to do something…",insets.left+1,insets.top+getFontMetrics(font).ascent+3) } finally { copy.dispose() } }
    }
}
/** Short fill animation only while this visible popover exists. */
internal class UsageMeter(private val remaining: Double) : JComponent() {
    private var fill=0f
    private var started=0L
    private val animation=Timer(16) { event ->
        val t=((System.nanoTime()-started)/260_000_000.0).coerceIn(0.0,1.0)
        fill=(1-(1-t)*(1-t)*(1-t)).toFloat(); repaint(); if(t>=1) (event.source as Timer).stop()
    }
    init { preferredSize=Dimension(240,14); maximumSize=Dimension(Int.MAX_VALUE,14); isOpaque=false }
    override fun addNotify() { super.addNotify(); started=System.nanoTime(); fill=0f; animation.start() }
    override fun removeNotify() { animation.stop(); super.removeNotify() }
    override fun paintComponent(g: Graphics) {
        val copy=g.create() as Graphics2D
        try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.color=Color(0x3C3F44); copy.fillRoundRect(0,4,width,6,6,6)
            copy.color=when { remaining<=10 -> Color(0xFF7777); remaining<=30 -> Color(0xEDBF61); else -> Color(0xB8C2D0) }; copy.fillRoundRect(0,4,(width*remaining.coerceIn(0.0,100.0)/100*fill).toInt(),6,6,6)
        } finally { copy.dispose() }
    }
}
internal fun transparentPanel(layout: LayoutManager = BorderLayout()) = JPanel(layout).apply { isOpaque=false }

internal fun JScrollPane.subtleScrollbars() {
    horizontalScrollBarPolicy=ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBar.apply {
        preferredSize=Dimension(8,0); isOpaque=false
        setUI(object: javax.swing.plaf.basic.BasicScrollBarUI() {
            override fun createDecreaseButton(orientation: Int)=JButton().apply { preferredSize=Dimension(0,0); minimumSize=preferredSize; maximumSize=preferredSize }
            override fun createIncreaseButton(orientation: Int)=createDecreaseButton(orientation)
            override fun paintTrack(g: Graphics,c: JComponent,bounds: Rectangle) {}
            override fun paintThumb(g: Graphics,c: JComponent,bounds: Rectangle) {
                val copy=g.create() as Graphics2D
                try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.color=Color(0x55585F); copy.fillRoundRect(bounds.x+2,bounds.y+2,(bounds.width-4).coerceAtLeast(3),(bounds.height-4).coerceAtLeast(12),5,5) } finally { copy.dispose() }
            }
        })
    }
}

internal fun browserChatTab(title: String,selected: Boolean,onSelect: ()->Unit,onClose: ()->Unit): JPanel {
    val panel=object: JPanel(BorderLayout()) {
        init { isOpaque=false }
        override fun paintComponent(g: Graphics) {
            val copy=g.create() as Graphics2D
            try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                if(selected) { copy.color=Color(0x303236); copy.fillRoundRect(0,0,width-1,height-1,10,10) }
                else { copy.color=Color(0x3B3E44); copy.drawLine(width-1,8,width-1,height-8) }
            } finally { copy.dispose() }; super.paintComponent(g)
        }
    }
    panel.preferredSize=Dimension(180,32)
    panel.add(flatButton(title,tooltip=title,action=onSelect).apply { horizontalAlignment=SwingConstants.LEFT; foreground=if(selected) CHAT_FG else CHAT_MUTED; border=EmptyBorder(5,12,5,6); font=font.deriveFont(Font.PLAIN,12f) },BorderLayout.CENTER)
    if(selected) panel.add(flatButton(icon=ChatIcon("close",CHAT_MUTED,14),tooltip="Close chat",action=onClose).apply { preferredSize=Dimension(26,26); border=EmptyBorder(3,3,3,7) },BorderLayout.EAST)
    return panel
}
internal fun JSlider.roundedReasoningTrack() {
    isOpaque=false; paintTicks=false; border=EmptyBorder(0,0,0,0); preferredSize=Dimension(270,34)
    setUI(object: javax.swing.plaf.basic.BasicSliderUI(this) {
        override fun getThumbSize()=Dimension(18,18)
        override fun paintTrack(g: Graphics) {
            val copy=g.create() as Graphics2D
            try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
                val y=trackRect.y+trackRect.height/2-6
                copy.color=Color(0x44474D); copy.fillRoundRect(trackRect.x,y,trackRect.width,12,12,12)
                val current=xPositionForValue(slider.value)
                copy.color=Color(0x548CF0); copy.fillRoundRect(trackRect.x,y,(current-trackRect.x).coerceAtLeast(6),12,12,12)
                for(value in slider.minimum..slider.maximum) { copy.color=Color(0x8B8F97); copy.fillOval(xPositionForValue(value)-1,y+5,3,3) }
            } finally { copy.dispose() }
        }
        override fun paintThumb(g: Graphics) { val copy=g.create() as Graphics2D
            try { copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); copy.color=Color(0xF1F2F4); copy.fillOval(thumbRect.x,thumbRect.y,thumbRect.width-1,thumbRect.height-1) } finally { copy.dispose() }
        }
        override fun paintFocus(g: Graphics) {}
    })
}
