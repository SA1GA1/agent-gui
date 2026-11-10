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
    @Test fun `composer grows for wrapped text and stops at seven visual rows`() {
        SwingUtilities.invokeAndWait {
            val input=ComposerTextArea().apply { font=chatFont(); lineWrap=true; wrapStyleWord=true; border=javax.swing.border.EmptyBorder(0,0,0,0) }
            input.text="Short"; val small=composerHeight(input,220)
            input.text=("a long wrapped prompt ").repeat(100)
            val tall=composerHeight(input,220)
            assertTrue(tall>small); assertEquals(input.getFontMetrics(input.font).height*7+4,tall)
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
    @Test fun `inline mentions retain surrounding text and delete as one undoable unit`() {
        SwingUtilities.invokeAndWait {
            val input=ComposerTextArea()
            input.text="Before /rev after"
            input.insertMention("skill","review","/skills/review",7,11)
            assertEquals("Before /review after",input.text)
            assertEquals(listOf(InlineMention("skill","review","/skills/review",7,14)),input.mentions())
            input.caretPosition=14
            input.actionMap.get("delete-previous").actionPerformed(java.awt.event.ActionEvent(input,0,""))
            assertEquals("Before  after",input.text); assertTrue(input.mentions().isEmpty())
            input.undoDraft()
            assertEquals("Before /review after",input.text); assertEquals(1,input.mentions().size)
            input.redoDraft(); assertEquals("Before  after",input.text)
        }
    }
    @Test fun `partial mention replacement removes whole reference and shifts following mentions`() {
        SwingUtilities.invokeAndWait {
            val input=ComposerTextArea()
            input.text="/a @b tail"
            input.insertMention("skill","alpha","/alpha",0,2)
            input.insertMention("plugin","beta","plugin:beta",7,9)
            assertEquals("/alpha @beta tail",input.text)
            input.replaceRange("x",2,4)
            assertEquals("x @beta tail",input.text)
            assertEquals(listOf(InlineMention("plugin","beta","plugin:beta",2,7)),input.mentions())
            assertEquals(java.awt.Color(0x79B8FF),javax.swing.text.StyleConstants.getForeground(input.styledDocument.getCharacterElement(2).attributes))
            assertNotEquals(java.awt.Color(0x79B8FF),javax.swing.text.StyleConstants.getForeground(input.styledDocument.getCharacterElement(1).attributes))
        }
    }
    @Test fun `restored mentions keep identity while plain commands and typed text stay ordinary`() {
        SwingUtilities.invokeAndWait {
            val input=ComposerTextArea()
            input.restoreDraft("/review now @file.kt",listOf(InlineMention("skill","review","/s/review",0,7),InlineMention("file","file.kt","src/file.kt",12,20)))
            assertEquals(2,input.mentions().size)
            assertTrue(input.isMentionAt(7)); assertFalse(input.isMentionAt(11))
            input.replaceRange(" please",7,7)
            assertEquals("/review please now @file.kt",input.text)
            assertEquals(19,input.mentions().last().start)
            input.restoreDraft("/model",emptyList()); assertTrue(input.mentions().isEmpty())
        }
    }
    @Test fun `model popup centers on anchor and stays within window`() {
        assertEquals(java.awt.Point(200,166),centeredPopupLocation(java.awt.Rectangle(300,400,40,24),java.awt.Dimension(240,230),java.awt.Rectangle(0,0,700,600)))
        assertEquals(java.awt.Point(8,8),centeredPopupLocation(java.awt.Rectangle(5,15,30,20),java.awt.Dimension(240,230),java.awt.Rectangle(0,0,300,260)))
    }

}
