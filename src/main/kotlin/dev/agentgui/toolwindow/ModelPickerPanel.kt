package dev.agentgui.toolwindow

import dev.agentgui.codex.string
import kotlinx.serialization.json.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** The picker stays open; server state updates its checkmark and effort controls in place. */
internal class ModelPickerPanel(
    private val models: List<JsonObject>, initialModel: String, initialEffort: String,
    private val chooseModel: (String) -> Unit, private val chooseEffort: (String, String) -> Unit
) : JPanel() {
    private val buttons=linkedMapOf<String,JButton>()
    private val reasoning=JPanel().apply { isOpaque=false; layout=BoxLayout(this,BoxLayout.Y_AXIS) }
    private var current=""
    private var slider: JSlider?=null
    private var label=JLabel()
    private var efforts=emptyList<String>()
    private var syncing=false
    init {
        isOpaque=false; layout=BoxLayout(this,BoxLayout.Y_AXIS)
        models.forEach { model -> val id=model.string("model").orEmpty()
            val button=flatButton(model.string("displayName") ?: id) { chooseModel(id) }.apply {
                horizontalAlignment=SwingConstants.CENTER; alignmentX=Component.CENTER_ALIGNMENT
                preferredSize=Dimension(200,28); minimumSize=Dimension(0,28); maximumSize=Dimension(Int.MAX_VALUE,28)
            }; buttons[id]=button; add(button)
        }
        add(JSeparator().apply { maximumSize=Dimension(Int.MAX_VALUE,1); foreground=Color(0x45484E) })
        add(Box.createVerticalStrut(6)); add(reasoning); updateSelection(initialModel,initialEffort)
    }
    fun updateSelection(model: String, effort: String) {
        models.forEach { item -> val id=item.string("model").orEmpty(); buttons[id]?.text=(item.string("displayName") ?: id)+if(id==model) "  ✓" else "" }
        if(current!=model || slider==null) {
            current=model; reasoning.removeAll()
            efforts=models.firstOrNull { it.string("model")==model }?.get("supportedReasoningEfforts")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.string("reasoningEffort") }
            slider=null
            if(efforts.isNotEmpty()) {
                label=JLabel().apply { font=chatFont(12f); foreground=CHAT_FG; alignmentX=Component.CENTER_ALIGNMENT }; reasoning.add(label)
                slider=JSlider(0,efforts.lastIndex,efforts.indexOf(effort).coerceAtLeast(0)).apply {
                    isEnabled=efforts.size>1; snapToTicks=true; majorTickSpacing=1; roundedReasoningTrack(); accessibleContext.accessibleName="Reasoning effort"
                    addChangeListener { label.text=efforts[value].replaceFirstChar { it.uppercase() }; if(!syncing && !valueIsAdjusting) chooseEffort(current,efforts[value]) }
                }; reasoning.add(slider)
            }
            revalidate(); repaint()
        }
        slider?.let { if(!it.valueIsAdjusting) { syncing=true; try { it.value=efforts.indexOf(effort).coerceAtLeast(0); label.text=efforts[it.value].replaceFirstChar { c -> c.uppercase() } } finally { syncing=false } } }
    }
}
