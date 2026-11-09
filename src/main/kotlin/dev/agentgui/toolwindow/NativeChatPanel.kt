package dev.agentgui.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.*
import dev.agentgui.codex.*
import dev.agentgui.sessions.SessionService
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Native controls, bounded transcript and batched updates; no browser process or project index. */
class NativeChatPanel(private val project: Project) : JPanel(BorderLayout(0,5)), Disposable {
    private val service=project.service<SessionService>()
    private val tabs=CompactTabs()
    private val newChat=flatButton(icon=ChatIcon("plus"),tooltip="New chat") { flushDraft(); dispatch("new") }
    private val input=ComposerTextArea()
    private val transcript=NativeTranscript()
    private lateinit var inputScroll: JBScrollPane
    private val inputResize=Timer(30) { resizeComposer() }.apply { isRepeats=false }
    private val scroll=JBScrollPane(transcript).apply { border=EmptyBorder(0,0,0,0); viewport.background=CHAT_BG; subtleScrollbars() }
    private val notices=vertical()
    private val approval=vertical()
    private val approvalDrafts=ApprovalDrafts()
    private var approvalView: ApprovalPanel?=null
    private var approvalId: String?=null
    private var decisionPending=false
    private fun customReply()=approvalId?.let { approvalDrafts.isEditing(activeId,it) }==true
    private val files=transparentPanel(FlowLayout(FlowLayout.LEFT,3,0))
    private val selections=transparentPanel(FlowLayout(FlowLayout.LEFT,3,0))
    private val bottom=transparentPanel()
    private val connectedBottom=vertical()
    private val model=flatButton("Model ▾") { modelPopup() }
    private val context=JBLabel("Context —").apply { foreground=CHAT_MUTED; font=chatFont(12f) }
    private val quota=flatButton("5h —",ChatIcon("quota",CHAT_MUTED,16)) { quotaPopup() }
    private val history=flatButton("History",icon=ChatIcon("history",CHAT_FG,16),tooltip="Prompt history") { promptHistory() }
    private val send=SendButton { if(customReply()) submitApproval() else if(busy()) dispatch("stop") else submit() }
    private val connect=flatButton("Connect Codex") { dispatch("connect","binary" to JsonPrimitive(state.string("binary").orEmpty())) }
    private var state=obj {}
    private var activeId=""
    private var incoming: JsonObject?=null
    private var disposed=false
    private var updating=false
    private var latestDraft=""
    private var draftThread=""
    private var draftText=""
    private var rendered=""
    private var controlsSignature=""
    private var noticesSignature=""
    private var filesSignature=""
    private var approvalsSignature=""
    private var bottomMode=""
    private val animatedNotices=linkedSetOf<String>()
    private var selectedSkill: String?=null
    private var selectedPlugin: JsonObject?=null
    private var popup: JBPopup?=null
    private var completionPopup: JBPopup?=null
    private val completionOpen get()=completionPopup?.isVisible==true
    private var quotaSignature=""
    private var completionList: JList<Suggestion>?=null
    private var pendingFileRequest: String?=null
    private var pendingPromptRequest: String?=null
    private var pendingReveal: String?=null
    private var completionToken: ChatPresentation.Token?=null
    private var completionFiles=emptyList<String>()
    private var historyPrompts=emptyList<Pair<String,String>>()
    private var historyQuery: JTextField?=null
    private var historyList: JList<Prompt>?=null
    private val drafts=Timer(250) { dispatch("draft","threadId" to JsonPrimitive(draftThread),"text" to JsonPrimitive(draftText)) }.apply { isRepeats=false }
    private val autocomplete=Timer(180) { complete() }.apply { isRepeats=false }
    private val updates=Timer(80) { incoming?.let { incoming=null; applyState(it) } }.apply { isRepeats=false }
    private data class Prompt(val id: String,val text: String) { override fun toString()=text.replace('\n',' ').take(130) }
    private data class Suggestion(val title: String,val detail: String,val action: () -> Unit) { override fun toString()=title }
    private fun vertical()=transparentPanel().apply { layout=BoxLayout(this,BoxLayout.Y_AXIS) }
    private val sink: (JsonObject)->Unit = { value -> ApplicationManager.getApplication().invokeLater {
        if(!disposed) when(value.string("type")) {
            "state" -> { incoming=value; if(!updates.isRunning) updates.start() }
            "files" -> if(value.string("requestId")==pendingFileRequest && ChatPresentation.token(input.text,input.caretPosition)==completionToken) {
                completionFiles=value["files"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }; showCompletions()
            }
            "promptHistoryResult" -> if(value.string("requestId")==pendingPromptRequest && value.string("threadId")==activeId) {
                historyPrompts=value["prompts"]?.jsonArray.orEmpty().map { it.jsonObject.string("id").orEmpty() to it.jsonObject.string("text").orEmpty() }; filterHistory()
            }
            "revealPrompt" -> if(value.string("threadId")==activeId) { pendingReveal=value.string("itemId"); incoming?.let { incoming=null; applyState(it) }; reveal() }
            "historyResult" -> resumeResults(value)
            "show" -> if(value.string("panel")=="models") modelPopup() else resumeHistory()
        }
    } }
    init {
        background=CHAT_BG; foreground=CHAT_FG; border=EmptyBorder(8,8,8,8); minimumSize=Dimension(300,300)
        val tabScroll=JBScrollPane(tabs).apply { border=EmptyBorder(0,0,0,0); preferredSize=Dimension(240,35); viewport.background=CHAT_BG; hoverHorizontalScroll() }
        val header=transparentPanel(BorderLayout(6,0)).apply { border=BorderFactory.createMatteBorder(0,0,1,0,Color(0x303236)); add(tabScroll,BorderLayout.CENTER); add(newChat.apply { preferredSize=Dimension(26,30) },BorderLayout.EAST) }
        add(header,BorderLayout.NORTH)
        val center=transparentPanel(BorderLayout(0,5)).apply { add(notices,BorderLayout.NORTH); add(scroll,BorderLayout.CENTER); add(bottom,BorderLayout.SOUTH) }; add(center,BorderLayout.CENTER)
        val composerShell=RoundedPanel(COMPOSER_BG,Color(0x3C3E42))
        val composer=transparentPanel(BorderLayout())
        composerShell.add(approval,BorderLayout.NORTH); composerShell.add(composer,BorderLayout.CENTER)
        input.lineWrap=true; input.wrapStyleWord=true; input.background=COMPOSER_BG; input.foreground=CHAT_FG; input.caretColor=CHAT_FG; input.border=EmptyBorder(0,0,0,0)
        input.toolTipText=null; input.accessibleContext.accessibleName="Message Codex"
        input.font=chatFont()
        inputScroll=JBScrollPane(input).apply { border=EmptyBorder(0,0,0,0); viewport.background=COMPOSER_BG; preferredSize=Dimension(260,24); minimumSize=Dimension(0,24); maximumSize=Dimension(Int.MAX_VALUE,140); horizontalScrollBarPolicy=ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER; subtleScrollbars() }
        val composeTop=vertical().apply { add(files); add(selections); add(inputScroll) }; composer.add(composeTop,BorderLayout.CENTER)
        composer.add(transparentPanel(BorderLayout(4,0)).apply {
            border=EmptyBorder(3,0,0,0)
            add(flatButton(icon=ChatIcon("clip",CHAT_FG,19),tooltip="Attach file") { dispatch("attach") }.apply { preferredSize=Dimension(26,26); border=EmptyBorder(2,2,2,2) },BorderLayout.WEST)
            add(transparentPanel(FlowLayout(FlowLayout.CENTER,0,0)).apply { add(model.apply { font=chatFont(12f); border=EmptyBorder(4,3,4,3) }) },BorderLayout.CENTER)
            add(send,BorderLayout.EAST)
        },BorderLayout.SOUTH)
        connectedBottom.add(composerShell)
        connectedBottom.add(transparentPanel(BorderLayout()).apply {
            border=EmptyBorder(5,0,0,0)
            add(transparentPanel(FlowLayout(FlowLayout.LEFT,8,0)).apply { add(history); add(quota) },BorderLayout.WEST); add(context,BorderLayout.EAST)
        })
        applyChatFont(connectedBottom)
        input.document.addDocumentListener(object: DocumentListener {
            override fun insertUpdate(e: DocumentEvent)=changed(); override fun removeUpdate(e: DocumentEvent)=changed(); override fun changedUpdate(e: DocumentEvent)=changed()
            private fun changed() { if(!updating) { if(customReply()) { approvalDrafts.edit(activeId,approvalId!!,input.text) } else { draftThread=activeId; draftText=input.text; drafts.restart(); autocomplete.restart() }; inputResize.restart(); updateSend() } }
        })
        input.addCaretListener { if(!updating && !customReply()) autocomplete.restart() }
        input.addFocusListener(object: FocusAdapter() { override fun focusGained(e: FocusEvent?) { dispatch("refresh") } })
        input.addInputMethodListener(object: InputMethodListener {
            override fun inputMethodTextChanged(e: InputMethodEvent) { input.putClientProperty("composing",(e.text?.endIndex ?: 0)>e.committedCharacterCount) }
            override fun caretPositionChanged(e: InputMethodEvent) {}
        })
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"send") {
            if(input.getClientProperty("composing")!=true) { if(completionOpen) completionList?.selectedValue?.action?.invoke() else if(customReply()) submitApproval() else if(!busy() && approvalId==null) submit() }
        }
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,InputEvent.SHIFT_DOWN_MASK),"insert-break")
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"dismiss") {
            hideCompletions()
            if(customReply()) { val text=approvalDrafts.leave(activeId,approvalId!!); replaceInput(text.orEmpty()); updateSend(); approvalView?.focusCustom() }
        }
        for((key,delta) in listOf(KeyEvent.VK_DOWN to 1,KeyEvent.VK_UP to -1)) {
            val stroke=KeyStroke.getKeyStroke(key,0); val previous=input.inputMap.get(stroke)
            bind(stroke,"completion$key") { val list=completionList
                if(completionOpen && list!=null) { list.selectedIndex=(list.selectedIndex+delta).coerceIn(0,(list.model.size-1).coerceAtLeast(0)); list.ensureIndexIsVisible(list.selectedIndex) }
                else input.actionMap.get(previous)?.actionPerformed(ActionEvent(input,ActionEvent.ACTION_PERFORMED,""))
            }
        }
        addComponentListener(object: ComponentAdapter() { override fun componentResized(e: ComponentEvent?) { inputResize.restart() } })
        setupTransfers()
        transcript.onLink = { e -> if(e.eventType==javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) if(e.description?.startsWith("agent-copy:")==true) {
                runCatching { val text=String(Base64.getUrlDecoder().decode(e.description.removePrefix("agent-copy:")),Charsets.UTF_8); Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text),null) }
            } else e.url?.toString()?.let { if(it.startsWith("https://")||it.startsWith("http://")) BrowserUtil.browse(it) } }
        connect.componentPopupMenu=JPopupMenu().apply { add(JMenuItem("Settings").apply { addActionListener { settings() } }) }
        service.listener=sink; dispatch("ready")
    }
    private fun bind(key: KeyStroke,name: String,action: ()->Unit) { input.inputMap.put(key,name); input.actionMap.put(name,object: AbstractAction() { override fun actionPerformed(e: ActionEvent?)=action() }) }
    private fun dispatch(type: String,vararg values: Pair<String,JsonElement>)=service.dispatch(obj { put("type",type); put("threadId",activeId); values.forEach { (key,value)->put(key,value) } })
    private fun chat()=state["sessions"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it.string("id")==activeId }
    private fun busy()=chat()?.string("state")?.let { it!="idle" } ?: false
    private fun flushDraft() { if(drafts.isRunning) { drafts.stop(); dispatch("draft","threadId" to JsonPrimitive(draftThread),"text" to JsonPrimitive(draftText)) } }
    private fun resizeComposer() {
        if(disposed || !::inputScroll.isInitialized) return
        val metrics=input.getFontMetrics(input.font)
        val available=(input.width.takeIf { it>0 } ?: (width-52)).coerceAtLeast(100)
        var rows=0
        for(line in input.text.lineSequence()) {
            rows+=maxOf(1,kotlin.math.ceil(metrics.stringWidth(line.take(2000)).toDouble()/available).toInt())
            if(rows>=8) break
        }
        val height=(rows*metrics.height+6).coerceIn(24,140)
        if(inputScroll.preferredSize.height!=height) { inputScroll.preferredSize=Dimension(260,height); connectedBottom.revalidate() }
    }
    private fun updateSend() {
        send.working=busy() && !customReply()
        send.isEnabled=!decisionPending && state["connected"]?.jsonPrimitive?.booleanOrNull==true && (customReply() || busy() || approvalId==null && state["signedIn"]?.jsonPrimitive?.booleanOrNull==true && chat()!=null && (input.text.isNotBlank() || chat()?.get("files")?.jsonArray?.isNotEmpty()==true))
    }
    private fun replaceInput(text: String) { val previous=updating; updating=true; input.text=text; updating=previous; inputResize.restart() }
    private fun submitApproval() {
        val id=approvalId ?: return
        if(decisionPending) return
        decisionPending=true; updateSend()
        dispatch("approve","requestId" to JsonPrimitive(id),"decision" to JsonPrimitive("cancel"),"text" to JsonPrimitive(input.text.trim()))
    }
    private fun submit() {
        if(!send.isEnabled || busy() || approvalId!=null) return
        when(input.text.trim()) {
            "/model" -> { modelPopup(); return }; "/resume" -> { resumeHistory(); return }; "/settings" -> { settings(); return }
        }
        drafts.stop(); hideCompletions(); send.isEnabled=false
        dispatch("send","text" to JsonPrimitive(input.text.trim()),"skill" to (selectedSkill?.let(::JsonPrimitive) ?: JsonNull),"plugin" to (selectedPlugin ?: JsonNull))
    }
    private fun applyState(value: JsonObject) {
        updating=true
        try {
            val old=activeId; state=value; activeId=value.string("activeId").orEmpty()
            val chat=chat(); val switched=old!=activeId
            if(switched) { approvalId=null; approvalsSignature=""; popup?.cancel(); hideCompletions(); pendingPromptRequest=null; pendingReveal=null; selectedSkill=null; selectedPlugin=null; renderSelections() }
            val connected=value["connected"]?.jsonPrimitive?.booleanOrNull==true
            val signedIn=value["signedIn"]?.jsonPrimitive?.booleanOrNull==true
            val sessions=value["sessions"]?.jsonArray.orEmpty().map { it.jsonObject }
            val signature=sessions.map { listOf(it["id"],it["title"],it["open"],it["state"]) }.toString()+activeId+connected
            if(signature!=controlsSignature) {
                controlsSignature=signature; tabs.removeAll()
                sessions.filter { it["open"]?.jsonPrimitive?.booleanOrNull!=false || it.string("state")!="idle" }.forEach { session ->
                    val id=session.string("id").orEmpty(); val title=session.string("title") ?: "New chat"
                    val titleText=(if(session.string("state")!="idle") "• " else "")+title
                    val tab=browserChatTab(titleText,id==activeId,
                        { flushDraft(); dispatch("select","threadId" to JsonPrimitive(id)) },
                        { flushDraft(); dispatch("close","threadId" to JsonPrimitive(id)) })
                    val menu=tabMenu(id,title); tab.componentPopupMenu=menu; tab.components.filterIsInstance<JComponent>().forEach { it.componentPopupMenu=menu }
                    tabs.add(tab)
                }
                tabs.revalidate()
                SwingUtilities.invokeLater { tabs.doLayout(); tabs.components.getOrNull(sessions.filter { it["open"]?.jsonPrimitive?.booleanOrNull!=false || it.string("state")!="idle" }.indexOfFirst { it.string("id")==activeId })?.let { (it as JComponent).scrollRectToVisible(Rectangle(0,0,it.width,it.height)) } }
            }
            newChat.isEnabled=connected
            val mode=if(!connected) "connect" else if(!signedIn) "login" else if(chat==null) "new" else "composer"
            if(mode!=bottomMode) { bottomMode=mode; bottom.removeAll(); bottom.add(when(mode) { "connect" -> connect; "new" -> flatButton("New chat") { dispatch("new") }; "login" -> flatButton("Sign in to Codex") { dispatch("login") }; else -> connectedBottom }) }
            input.isEnabled=connected && signedIn && chat!=null
            renderApprovals(chat)
            val draft=chat?.string("draft").orEmpty()
            if(!customReply() && (switched || (draft!=latestDraft && (!input.hasFocus() || busy() && draft.isEmpty())))) { input.text=draft; if(draft.isEmpty()) { selectedSkill=null; selectedPlugin=null; renderSelections() } }
            latestDraft=draft; inputResize.restart()
            val catalog=value["models"]?.jsonArray.orEmpty().map { it.jsonObject }
            val modelName=catalog.firstOrNull { it.string("model")==chat?.string("model") }?.string("displayName") ?: chat?.string("model") ?: "Model"
            model.text=modelName+" · "+(chat?.string("effort")?.replaceFirstChar { it.uppercase() } ?: "Reasoning")+" ▾"; model.isEnabled=!busy()
            val percent=chat?.get("contextPercent")?.jsonPrimitive?.doubleOrNull
            val stale=chat?.get("contextStale")?.jsonPrimitive?.booleanOrNull==true
            context.text=percent?.let { "Context ${it.toInt()}%" } ?: "Context —"
            context.foreground=when { percent==null || stale -> CHAT_MUTED; percent>75 -> Color(0xFF8989); percent>50 -> Color(0xF2C56E); else -> CHAT_MUTED }
            quota.text="5h "+(quotaWindows().firstOrNull { it["minutes"]?.jsonPrimitive?.longOrNull==300L }?.get("remaining")?.jsonPrimitive?.doubleOrNull?.let { "${it.toInt()}%" } ?: "—")
            if(popup?.isVisible==true && popup?.content?.getClientProperty("quota")==true && quotaSignature!=value["quotaWindows"].toString()) {
                quotaSignature=value["quotaWindows"].toString()
                (popup?.content?.getComponent(0) as? JPanel)?.let { panel -> val replacement=quotaContent(); panel.removeAll(); replacement.components.forEach { panel.add(it) }; panel.revalidate(); panel.repaint() }
            }
            updateSend(); renderFiles(chat); renderNotices(value)
            val items=chat?.get("items")?.jsonArray ?: JsonArray(emptyList())
            val next="$activeId:$items:$mode"
            if(next!=rendered) {
                rendered=next
                val nearBottom=scroll.verticalScrollBar.let { it.value+it.visibleAmount>=it.maximum-50 }
                val position=scroll.verticalScrollBar.value
                val empty=when { !connected -> "Connect Codex to start a conversation."; !signedIn -> "Sign in to continue."; else -> "What would you like to build?" }
                transcript.showItems(activeId,items,empty)
                SwingUtilities.invokeLater { if(!disposed && activeId==chat?.string("id")) { if(pendingReveal!=null) reveal() else if(nearBottom || switched) scroll.verticalScrollBar.value=scroll.verticalScrollBar.maximum else scroll.verticalScrollBar.value=position } }
            }
            revalidate(); repaint()
        } finally { updating=false }
    }
    private fun renderFiles(chat: JsonObject?) {
        val next=chat?.get("files").toString(); if(next==filesSignature) return; filesSignature=next; files.removeAll()
        chat?.get("files")?.jsonArray?.forEach { value -> val file=value.jsonObject
            files.add(flatButton(file.string("name").orEmpty()+" ×",tooltip="Remove ${file.string("name")}") { dispatch("removeAttachment","id" to file["id"]!!) }.apply {
                file.string("preview")?.substringAfter(',')?.let { encoded -> runCatching { ImageIO.read(java.io.ByteArrayInputStream(Base64.getDecoder().decode(encoded))) }.getOrNull()?.let { icon=ImageIcon(it.getScaledInstance(28,28,Image.SCALE_SMOOTH)) } }
            })
        }; files.isVisible=files.componentCount>0
    }
    private fun renderNotices(value: JsonObject) {
        val list=value["notices"]?.jsonArray.orEmpty().map { it.jsonObject }.filter { it.string("threadId").let { id -> id==null || id==activeId } }.takeLast(3)
        val next=list.toString()+busy(); if(next==noticesSignature) return; noticesSignature=next; notices.removeAll()
        list.forEach { notice ->
            val fill=when(notice.string("level")) { "critical" -> Color(0x57282C); "warning" -> Color(0x71531D); else -> Color(0x233D5B) }
            val accent=when(notice.string("level")) { "critical" -> Color(0xED6270); "warning" -> Color(0xFFD34F); else -> Color(0x62B5EF) }
            val card=RoundedPanel(fill,accent.darker()).apply { border=EmptyBorder(6,10,6,4) }
            card.add(JBLabel(ChatIcon(if(notice.string("level")=="info") "info" else "warning",accent,23)).apply { border=EmptyBorder(0,0,0,10) },BorderLayout.WEST)
            val text=JBLabel("<html>${SafeMarkdown.escape(notice.string("text").orEmpty())}</html>").apply { foreground=Color(0xFFF2D7); font=chatFont(12f) }; card.add(text,BorderLayout.CENTER)
            card.add(flatButton(icon=ChatIcon("close"),tooltip="Dismiss notification") { dispatch("dismissNotice","id" to notice["id"]!!) }.apply { preferredSize=Dimension(20,20); minimumSize=preferredSize; border=EmptyBorder(0,0,0,0) },BorderLayout.EAST)
            if(notice.string("action")=="compact") card.add(transparentPanel(FlowLayout(FlowLayout.LEFT,0,0)).apply { add(flatButton("Compact context") { dispatch("compact") }.apply { isEnabled=!busy() && state["connected"]?.jsonPrimitive?.booleanOrNull==true }) },BorderLayout.SOUTH)
            applyChatFont(card); notices.add(card); if(animatedNotices.add(notice.string("id").orEmpty())) card.animateIn(); while(animatedNotices.size>40) animatedNotices.remove(animatedNotices.first()); notices.add(Box.createVerticalStrut(4))
        }; notices.isVisible=list.isNotEmpty()
    }
    private fun renderApprovals(chat: JsonObject?) {
        val requests=chat?.get("approvals")?.jsonArray.orEmpty()
        val request=requests.firstOrNull()?.jsonObject
        val id=request?.get("id")?.jsonPrimitive?.content
        val next=activeId+":"+requests.toString()
        if(next==approvalsSignature) { decisionPending=false; return }
        val previous=approvalId
        if(previous!=null && previous!=id) approvalDrafts.resolve(activeId,previous)?.let { replaceInput(it) }
        approvalsSignature=next; approvalId=id; decisionPending=false; approval.removeAll(); approvalView=null
        if(request!=null) {
            val itemId=request["params"]?.jsonObject?.string("itemId")
            val changes=chat?.get("items")?.jsonArray.orEmpty().firstOrNull { it.jsonObject.string("id")==itemId }?.jsonObject?.get("changes")?.jsonArray.orEmpty().joinToString("\n") { it.jsonObject.string("path").orEmpty() }
            approvalView=ApprovalPanel(request,changes) { decision ->
                if(!decisionPending && approvalId==id) {
                    if(decision=="cancel") {
                        flushDraft(); hideCompletions()
                        replaceInput(approvalDrafts.enter(activeId,id!!,input.text)); input.requestFocusInWindow(); updateSend()
                    } else {
                        decisionPending=true; updateSend()
                        dispatch("approve","requestId" to JsonPrimitive(id!!),"decision" to JsonPrimitive(decision))
                    }
                }
            }; approval.add(approvalView); applyChatFont(approval)
            if(approvalDrafts.isEditing(activeId,id!!)) replaceInput(approvalDrafts.enter(activeId,id,chat?.string("draft").orEmpty()))
        }
        val live=state["sessions"]?.jsonArray.orEmpty().flatMap { session -> session.jsonObject["approvals"]?.jsonArray.orEmpty().map { session.jsonObject.string("id").orEmpty() to it.jsonObject["id"]!!.jsonPrimitive.content } }.toSet()
        approvalDrafts.retain(live)
        approval.isVisible=request!=null
    }
    private fun tabMenu(id: String,title: String)=JPopupMenu().apply {
        add(JMenuItem("Rename chat").apply { addActionListener { JOptionPane.showInputDialog(this@NativeChatPanel,"Chat title",title)?.let { dispatch("rename","threadId" to JsonPrimitive(id),"title" to JsonPrimitive(it)) } } })
        for((label,action) in listOf("Close tab" to "close","Archive chat" to "archive")) add(JMenuItem(label).apply { addActionListener { flushDraft(); dispatch(action,"threadId" to JsonPrimitive(id)) } })
        addSeparator(); add(JMenuItem("Settings").apply { addActionListener { settings() } })
    }
    private fun showPopup(owner: JComponent,content: JComponent,width: Int=300) {
        popup?.cancel()
        applyChatFont(content)
        val container=RoundedPanel(COMPOSER_BG,Color(0x44464B)).apply {
            add(content); preferredSize=Dimension(minOf(width,this@NativeChatPanel.width.coerceAtLeast(280)-16),content.preferredSize.height+20)
        }
        content.isFocusable=true
        val window=JBPopupFactory.getInstance().createComponentPopupBuilder(container,content)
            .setRequestFocus(true).setFocusable(true).setCancelOnClickOutside(true).setCancelOnOtherWindowOpen(true).createPopup()
        popup=window
        if(owner.isShowing) window.show(RelativePoint(owner,Point(0,-container.preferredSize.height-4)))
    }
    private fun modelPopup() {
        val panel=vertical()
        state["models"]?.jsonArray?.forEach { value -> val item=value.jsonObject
            panel.add(flatButton((item.string("displayName") ?: item.string("model").orEmpty())+if(item.string("model")==chat()?.string("model")) "  ✓" else "") {
                dispatch("model","model" to item["model"]!!); popup?.cancel()
            }.apply { horizontalAlignment=SwingConstants.CENTER; alignmentX=Component.CENTER_ALIGNMENT; preferredSize=Dimension(200,28); minimumSize=Dimension(0,28); maximumSize=Dimension(Int.MAX_VALUE,28) })
        }
        panel.add(JSeparator().apply { maximumSize=Dimension(Int.MAX_VALUE,1); foreground=Color(0x45484E) }); panel.add(Box.createVerticalStrut(8)); panel.add(reasoningControls()); showPopup(model,panel,240)
    }
    private fun reasoningControls(): JPanel {
        val selected=chat()?.string("model").orEmpty()
        val entry=state["models"]?.jsonArray?.firstOrNull { it.jsonObject.string("model")==selected }?.jsonObject ?: return vertical()
        val values=entry["supportedReasoningEfforts"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.string("reasoningEffort") }
        if(values.isEmpty()) return vertical()
        val panel=vertical(); val label=JBLabel(chat()?.string("effort").orEmpty().replaceFirstChar { it.uppercase() }).apply { foreground=CHAT_FG; alignmentX=Component.CENTER_ALIGNMENT }; panel.add(label)
        panel.add(JSlider(0,values.lastIndex,values.indexOf(chat()?.string("effort")).coerceAtLeast(0)).apply {
            isOpaque=false; majorTickSpacing=1; snapToTicks=true; roundedReasoningTrack(); accessibleContext.accessibleName="Reasoning effort"
            addChangeListener { label.text=values[value].replaceFirstChar { it.uppercase() }; if(!valueIsAdjusting) dispatch("model","model" to JsonPrimitive(selected),"effort" to JsonPrimitive(values[value])) }
        }); return panel
    }
    private fun quotaWindows()=state["quotaWindows"]?.jsonArray.orEmpty().map { it.jsonObject }
    private fun quotaPopup() {
        dispatch("refresh"); showPopup(quota,quotaContent(),340); popup?.content?.putClientProperty("quota",true); quotaSignature=state["quotaWindows"].toString()
    }
    private fun quotaContent(): JPanel {
        val panel=vertical()
        for((minutes,label) in listOf(300L to "5-hour limit",10080L to "Weekly limit")) {
            panel.add(JBLabel(label).apply { foreground=CHAT_FG; font=font.deriveFont(Font.BOLD) })
            val windows=quotaWindows().filter { it["minutes"]?.jsonPrimitive?.longOrNull==minutes }
            if(windows.isEmpty()) panel.add(JBLabel("Unavailable").apply { foreground=CHAT_MUTED })
            windows.forEach { window ->
                val remaining=window["remaining"]?.jsonPrimitive?.doubleOrNull
                panel.add(JBLabel((remaining?.let { "${it.toInt()}% remaining" } ?: "Unavailable")+" · "+window.string("name").orEmpty()).apply { foreground=CHAT_FG })
                val date=window["resetsAt"]?.jsonPrimitive?.longOrNull?.let { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm z",java.util.Locale.ENGLISH)) }
                panel.add(JBLabel("Resets "+(date ?: "unavailable")).apply { foreground=CHAT_MUTED }); remaining?.let { panel.add(UsageMeter(it)) }
            }; panel.add(Box.createVerticalStrut(14))
        }; return panel
    }
    private fun promptHistory() {
        historyPrompts=ChatPresentation.prompts(chat()?.get("items")?.jsonArray ?: JsonArray(emptyList()))
        val panel=transparentPanel(BorderLayout(0,7)); val search=JTextField().apply { toolTipText="Search your prompts"; accessibleContext.accessibleName="Search your prompts" }
        historyQuery=search; val list=JList<Prompt>().apply { background=COMPOSER_BG; foreground=CHAT_FG; fixedCellHeight=34; selectionMode=ListSelectionModel.SINGLE_SELECTION }; historyList=list
        panel.add(search,BorderLayout.NORTH); panel.add(JBScrollPane(list).apply { preferredSize=Dimension(330,240); border=EmptyBorder(0,0,0,0); subtleScrollbars() },BorderLayout.CENTER)
        fun choose() { list.selectedValue?.let { popup?.cancel(); pendingReveal=it.id; dispatch("revealPrompt","itemId" to JsonPrimitive(it.id)) } }
        search.document.addDocumentListener(object: DocumentListener { override fun insertUpdate(e: DocumentEvent)=filterHistory(); override fun removeUpdate(e: DocumentEvent)=filterHistory(); override fun changedUpdate(e: DocumentEvent)=filterHistory() })
        list.addMouseListener(object: MouseAdapter() { override fun mouseClicked(e: MouseEvent) { if(list.locationToIndex(e.point)>=0) choose() } })
        list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"choose"); list.actionMap.put("choose",object: AbstractAction() { override fun actionPerformed(e: ActionEvent?)=choose() })
        search.addActionListener { choose() }; filterHistory(); showPopup(history,panel,360)
        pendingPromptRequest=java.util.UUID.randomUUID().toString(); dispatch("promptHistory","requestId" to JsonPrimitive(pendingPromptRequest),"query" to JsonPrimitive("")); search.requestFocusInWindow()
    }
    private fun filterHistory() { val query=historyQuery?.text.orEmpty(); historyList?.setListData(historyPrompts.filter { it.second.contains(query,true) }.asReversed().map { Prompt(it.first,it.second) }.toTypedArray()); if((historyList?.model?.size ?: 0)>0) historyList?.selectedIndex=0 }
    private fun reveal() { val id=pendingReveal ?: return; pendingReveal=null; SwingUtilities.invokeLater { if(!disposed) transcript.revealMessage(id) } }
    private fun complete() {
        if(disposed || customReply() || !input.hasFocus() || input.getClientProperty("composing")==true) return
        val token=ChatPresentation.token(input.text,input.caretPosition)
        if(token==null) { hideCompletions(); return }
        if(token!=completionToken) { completionFiles=emptyList(); pendingFileRequest=null }
        completionToken=token; showCompletions()
        if(token.marker=='@') { pendingFileRequest=java.util.UUID.randomUUID().toString(); dispatch("searchFiles","query" to JsonPrimitive(token.query),"requestId" to JsonPrimitive(pendingFileRequest)) }
    }
    private fun showCompletions() {
        val token=completionToken ?: return
        val options=mutableListOf<Suggestion>()
        if(token.marker=='/') {
            for((command,detail) in listOf("new" to "Start a new chat","compact" to "Compact current context","model" to "Choose model","resume" to "Resume a conversation","status" to "Show status","settings" to "Plugin settings")) if(command.contains(token.query,true)) options.add(Suggestion("/$command",detail) { replaceToken("/$command ") })
            state["skills"]?.jsonArray?.map { it.jsonObject }?.filter { it["enabled"]?.jsonPrimitive?.booleanOrNull!=false && it.string("name").orEmpty().contains(token.query,true) }?.forEach { skill -> options.add(Suggestion(skill.string("name").orEmpty(),skill.string("description") ?: "Skill") { selectedSkill=skill.string("name"); replaceToken(""); renderSelections() }) }
        } else {
            state["plugins"]?.jsonArray?.map { it.jsonObject }?.filter { it["enabled"]?.jsonPrimitive?.booleanOrNull!=false && it.string("name").orEmpty().contains(token.query,true) }?.forEach { plugin -> options.add(Suggestion(plugin.string("name").orEmpty(),plugin.string("description") ?: "Plugin") { selectedPlugin=plugin; replaceToken(""); renderSelections() }) }
            completionFiles.forEach { path -> options.add(Suggestion(path,"Project file") { dispatch("attachProjectFile","path" to JsonPrimitive(path)); replaceToken("") }) }
        }
        completionPopup?.cancel()
        val list=JList(options.toTypedArray()).apply {
            background=COMPOSER_BG; foreground=CHAT_FG; fixedCellHeight=46; fixedCellWidth=(this@NativeChatPanel.width-48).coerceIn(236,426); visibleRowCount=minOf(options.size.coerceAtLeast(1),7); selectedIndex=if(options.isEmpty()) -1 else 0
            cellRenderer=ListCellRenderer { _, value, _, selected, _ ->
                RoundedPanel(if(selected) Color(0x3A3D43) else COMPOSER_BG).apply {
                    border=EmptyBorder(5,9,5,9)
                    add(JBLabel(value.title).apply { foreground=CHAT_FG; font=chatFont(12f) },BorderLayout.NORTH)
                    add(JBLabel(value.detail).apply { foreground=CHAT_MUTED; font=font.deriveFont(Font.PLAIN,11f) },BorderLayout.SOUTH)
                }
            }
            addMouseListener(object: MouseAdapter() { override fun mouseClicked(e: MouseEvent) { selectedValue?.action?.invoke() } })
        }; completionList=list
        val menu=RoundedPanel(COMPOSER_BG,Color(0x44464B)).apply { isFocusable=false; border=EmptyBorder(6,6,6,6)
            add(if(options.isEmpty()) JBLabel(if(token.marker=='@') "No matching files or plugins" else "No matching commands or skills").apply { foreground=CHAT_MUTED; border=EmptyBorder(8,8,8,8) } else JBScrollPane(list).apply { border=EmptyBorder(0,0,0,0); subtleScrollbars() })
            preferredSize=Dimension((this@NativeChatPanel.width-24).coerceIn(260,450),if(options.isEmpty()) 38 else minOf(options.size,7)*46+12)
        }
        if(input.isShowing) {
            list.isFocusable=false
            completionPopup=JBPopupFactory.getInstance().createComponentPopupBuilder(menu,null)
                .setFocusable(false).setRequestFocus(false).setCancelKeyEnabled(false)
                .setCancelOnClickOutside(true).setCancelOnOtherWindowOpen(true).createPopup()
            completionPopup?.show(RelativePoint(input,Point(0,-menu.preferredSize.height-10)))
        }
    }
    private fun replaceToken(replacement: String) {
        val token=completionToken ?: return
        if(ChatPresentation.token(input.text,input.caretPosition)!=token) { hideCompletions(); return }
        hideCompletions(); input.replaceRange(replacement,token.start,token.end); input.caretPosition=token.start+replacement.length; input.requestFocusInWindow()
    }
    private fun hideCompletions() { autocomplete.stop(); completionPopup?.cancel(); completionPopup=null; completionList=null; completionToken=null; pendingFileRequest=null }
    private fun renderSelections() { selections.removeAll(); selectedSkill?.let { selections.add(flatButton("$it ×",tooltip="Remove skill") { selectedSkill=null; renderSelections() }) }; selectedPlugin?.let { selections.add(flatButton("${it.string("name")} ×",tooltip="Remove plugin") { selectedPlugin=null; renderSelections() }) }; selections.isVisible=selections.componentCount>0; selections.revalidate(); selections.repaint() }
    private fun resumeHistory() { dispatch("history","query" to JsonPrimitive(JOptionPane.showInputDialog(this,"Search conversations","") ?: return)) }
    private fun resumeResults(event: JsonObject) {
        val entries=event["entries"]?.jsonArray.orEmpty().map { val item=it.jsonObject; Prompt(item.string("id").orEmpty(),item.string("name") ?: item.string("preview") ?: "Conversation") }.toTypedArray()
        if(entries.isEmpty()) return
        (JOptionPane.showInputDialog(this,"Resume a conversation","Conversations",JOptionPane.PLAIN_MESSAGE,null,entries,entries[0]) as? Prompt)?.let { flushDraft(); dispatch("resume","threadId" to JsonPrimitive(it.id)) }
    }
    private fun settings() {
        val actions=arrayOf("Codex CLI path","Sign in","Auto-start 5h: "+if(state["autoWindowEnabled"]?.jsonPrimitive?.booleanOrNull==true) "On" else "Off","Cancel")
        when(JOptionPane.showOptionDialog(this,"Local Codex · ${state.string("version").orEmpty()}","Settings",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,actions,actions[0])) {
            0 -> JOptionPane.showInputDialog(this,"Codex CLI path (empty = automatic)",state.string("binary").orEmpty())?.let { dispatch("connect","binary" to JsonPrimitive(it)) }
            1 -> dispatch("login"); 2 -> dispatch("toggleWarmup")
        }
    }
    private fun setupTransfers() {
        val original=input.transferHandler
        input.transferHandler=object: TransferHandler() {
            override fun canImport(support: TransferSupport)=support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)||support.isDataFlavorSupported(DataFlavor.imageFlavor)||original.canImport(support)
            override fun importData(support: TransferSupport): Boolean=try {
                when {
                    support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> { @Suppress("UNCHECKED_CAST") val dropped=support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>; dispatch("nativeAttachPaths","paths" to JsonArray(dropped.map { JsonPrimitive(it.absolutePath) })); true }
                    support.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                        val image=support.transferable.getTransferData(DataFlavor.imageFlavor) as Image; val width=image.getWidth(null); val height=image.getHeight(null)
                        require(width>0 && height>0 && width.toLong()*height<=16_000_000) { "Clipboard image is too large" }
                        val buffered=BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB); val graphics=buffered.createGraphics(); try { graphics.drawImage(image,0,0,null) } finally { graphics.dispose() }
                        val bytes=ByteArrayOutputStream(); ImageIO.write(buffered,"png",bytes); dispatch("pasteImage","name" to JsonPrimitive("Image.png"),"mime" to JsonPrimitive("image/png"),"data" to JsonPrimitive(Base64.getEncoder().encodeToString(bytes.toByteArray()))); true
                    }
                    else -> original.importData(support)
                }
            } catch(e: Exception) { JOptionPane.showMessageDialog(this@NativeChatPanel,e.message ?: "Could not attach file","Attachment",JOptionPane.ERROR_MESSAGE); false }
        }
    }
    override fun dispose() { disposed=true; flushDraft(); popup?.cancel(); hideCompletions(); drafts.stop(); updates.stop(); inputResize.stop(); if(service.listener===sink) service.listener=null }
}
