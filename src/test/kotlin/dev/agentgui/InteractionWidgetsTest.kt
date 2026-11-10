package dev.agentgui

import dev.agentgui.codex.*
import dev.agentgui.toolwindow.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.*

class InteractionWidgetsTest {
    @Test fun `model picker refreshes the reasoning range without replacing the picker`() {
        SwingUtilities.invokeAndWait {
            val choices=mutableListOf<String>(); val efforts=mutableListOf<Pair<String,String>>()
            fun model(id:String, levels:List<String>)=obj { put("model",id);put("displayName",id);put("supportedReasoningEfforts",JsonArray(levels.map { obj { put("reasoningEffort",it) } })) }
            val picker=ModelPickerPanel(listOf(model("a",listOf("low","high")),model("b",listOf("low"))),"a","low",{choices.add(it)},{m,e->efforts.add(m to e)})
            picker.components.filterIsInstance<JButton>()[1].doClick()
            assertEquals(listOf("b"),choices)
            picker.updateSelection("b","low")
            fun sliders(c:java.awt.Container):List<JSlider> = c.components.flatMap { if(it is JSlider) listOf(it) else if(it is java.awt.Container) sliders(it) else emptyList() }
            assertEquals(0,sliders(picker).single().maximum)
            picker.updateSelection("a","high"); val slider=sliders(picker).single()
            assertEquals(1,slider.value); assertTrue(efforts.isEmpty())
            slider.valueIsAdjusting=true; slider.value=0; assertTrue(efforts.isEmpty())
            slider.valueIsAdjusting=false; assertEquals(listOf("a" to "low"),efforts)
        }
    }
    @Test fun `composer grows for wrapped text and stops at five visual rows`() {
        SwingUtilities.invokeAndWait {
            val input=ComposerTextArea().apply { font=chatFont(); lineWrap=true; wrapStyleWord=true; border=javax.swing.border.EmptyBorder(0,0,0,0) }
            input.text="Short"; val small=composerHeight(input,220)
            input.text=("a long wrapped prompt ").repeat(100)
            val tall=composerHeight(input,220)
            assertTrue(tall>small); assertEquals(input.getFontMetrics(input.font).height*5+4,tall)
            input.text=("line\n").repeat(30); assertEquals(tall,composerHeight(input,220))
        }
    }
    @Test fun `moving reasoning thumb repaints the full track`() {
        SwingUtilities.invokeAndWait {
            val slider=object:JSlider(0,5,2) { var fullRepaints=0; override fun repaint() { fullRepaints++ } }
            slider.roundedReasoningTrack(); slider.setSize(220,30)
            val image=java.awt.image.BufferedImage(220,30,java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g=image.createGraphics(); slider.paint(g);g.dispose()
            val before=slider.fullRepaints
            (slider.ui as javax.swing.plaf.basic.BasicSliderUI).setThumbLocation(100,5)
            assertTrue(slider.fullRepaints>before)
        }
    }
}
