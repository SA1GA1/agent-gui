import {useEffect,useRef,useState} from 'react';
import {enterAction} from './keyboard';
import {attachmentRoute,MAX_ATTACHMENTS} from './droppedFiles';
import {RichPrompt} from './RichPrompt';
import {editPrompt,insertMention,validMentions,type Mention} from './mentions';
import {host,type Chat,type State} from './types';
const SendArrow=()=> <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M10 16V4m-5 5 5-5 5 5"/></svg>;
const commands = [['/new','New chat'],['/model','Choose model'],['/status','Codex status'],['/compact','Compact context'],['/resume','Resume conversation']];
type Choice={value:string;label:string;group:string;path?:string};
export function Composer({chat,state,onHistory}:{chat:Chat;state:State;onHistory:()=>void}) {
  const [text,setText]=useState(chat.draft),[files,setFiles]=useState<string[]>([]),[selection,setSelection]=useState(0),[mentions,setMentions]=useState<Mention[]>(chat.mentions||[]),[caret,setCaret]=useState(chat.draft.length);
  const [modelOpen,setModelOpen]=useState(false),[dismissed,setDismissed]=useState(false),[error,setError]=useState(''),[effortPreview,setEffortPreview]=useState<number>();
  const textarea=useRef<HTMLDivElement>(null),locked=useRef(false),searchId=useRef(''),effortDrag=useRef(false),scrollIdle=useRef<ReturnType<typeof setTimeout>|undefined>(undefined);
  useEffect(()=>()=>{if(scrollIdle.current)clearTimeout(scrollIdle.current);},[]);
  const approval=chat.approvals[0],requestKey=approval?`${chat.id}:${approval.id}`:'';
  const [customKey,setCustomKey]=useState(''),[approvalSelection,setApprovalSelection]=useState(0),[approvalLocked,setApprovalLocked]=useState(false);
  const replyDrafts=useRef(new Map<string,{text:string;editing:boolean}>()),optionRefs=useRef<(HTMLButtonElement|null)[]>([]);
  const custom=!!requestKey&&customKey===requestKey;
  const pendingSubmission=useRef<{id:string;text:string;mentions:Mention[]}|undefined>(undefined);
  const currentPrompt=useRef({text,mentions});currentPrompt.current={text,mentions};
  const busy=chat.state!=='idle';
  const approvalChoices=approval?['Allow once',approval.method==='item/fileChange/requestApproval'?'Allow changes to these files for this session':'Allow for this session','Decline or write a reply']:[];
  useEffect(()=>{
    const saved=replyDrafts.current.get(requestKey);
    setCustomKey(saved?.editing?requestKey:'');setText(saved?.editing?saved.text:chat.draft);setMentions(saved?.editing?[]:chat.mentions||[]);setCaret((saved?.editing?saved.text:chat.draft).length);setApprovalLocked(false);setApprovalSelection(saved?.editing?2:0);
    for(const key of replyDrafts.current.keys())if(key.startsWith(chat.id+':')&&key!==requestKey)replyDrafts.current.delete(key);
  },[requestKey,chat.id]);
  function chooseApproval(index:number){
    if(approvalLocked||!approval)return;
    setApprovalSelection(index);
    if(index===2){const saved=replyDrafts.current.get(requestKey)||{text:'',editing:true};saved.editing=true;replyDrafts.current.set(requestKey,saved);setCustomKey(requestKey);setText(saved.text);setMentions([]);requestAnimationFrame(()=>textarea.current?.focus());}
    else {setApprovalLocked(true);host('approve',{threadId:chat.id,requestId:String(approval.id),decision:index===0?'accept':'acceptForSession'});}
  }
  function sendApproval(){if(approvalLocked||!approval)return;setApprovalLocked(true);host('approve',{threadId:chat.id,requestId:String(approval.id),decision:'cancel',text:text.trim()});}
  function leaveApproval(){const saved=replyDrafts.current.get(requestKey);if(saved)saved.editing=false;setCustomKey('');setText(chat.draft);setMentions(chat.mentions||[]);setCaret(chat.draft.length);requestAnimationFrame(()=>optionRefs.current[2]?.focus());}

  const match=!mentions.some(m=>caret>m.start&&caret<=m.end)?text.slice(0,caret).match(/(?:^|\s)([/@])([^\s/@]*)$/):null;
  const slash=match?.[1]==='/',at=match?.[1]==='@'?match:undefined;
  const term=match?.[2]||'',tokenStart=match?caret-term.length-1:caret;
  const menu:Choice[]=custom||dismissed?[]:slash?[
    ...commands.filter(([c])=>c.startsWith('/'+term)).map(([value,label])=>({value,label,group:'Commands'})),
    ...state.skills.filter(s=>s.enabled!==false&&s.name.toLowerCase().includes(term.toLowerCase())).map(s=>({value:s.name,label:s.description,group:'Skills',path:s.path})),
    ...(state.plugins||[]).filter(p=>p.enabled!==false&&p.name.toLowerCase().includes(term.toLowerCase())).map(p=>({value:p.name,label:p.description,group:'Plugins',path:p.path}))
  ]:at?[
    ...files.map(value=>({value,label:'',group:'Files'})),

  ]:[];
  useEffect(()=>{if(!replyDrafts.current.get(requestKey)?.editing){setText(chat.draft);setMentions(chat.mentions||[]);setCaret(chat.draft.length);}locked.current=false;},[chat.id,chat.draft,chat.state,custom]);
  useEffect(()=>{setMentions(chat.mentions||[]);setFiles([]);setModelOpen(false);},[chat.id]);
  useEffect(()=>{setSelection(0);setDismissed(false);const field=textarea.current;if(field){const line=parseFloat(getComputedStyle(field).lineHeight)||19.5;const max=line*7+4;field.style.height='0px';field.style.maxHeight=`${max}px`;const needed=field.scrollHeight;field.style.height=`${Math.min(max,Math.max(line+4,needed))}px`;field.style.overflowY=needed>max?'auto':'hidden';}},[text]);
  useEffect(()=>{
    searchId.current='';setFiles([]);
    if(!at)return;
    const requestId=crypto.randomUUID();searchId.current=requestId;
    const timer=setTimeout(()=>host('searchFiles',{query:term,requestId}),180);
    return ()=>{clearTimeout(timer);searchId.current='';};
  },[term,!!at,chat.id]);
  useEffect(()=>{
    const listener=(e:Event)=>{const detail=(e as CustomEvent).detail;
      if(detail.type==='submitted'&&detail.threadId===chat.id&&detail.requestId===pendingSubmission.current?.id){
        const pending=pendingSubmission.current;pendingSubmission.current=undefined;
        if(pending&&currentPrompt.current.text===pending.text&&JSON.stringify(currentPrompt.current.mentions)===JSON.stringify(pending.mentions))draft('',[]);
        locked.current=false;
      }
      if(detail.type==='files'&&detail.requestId===searchId.current)setFiles(detail.files);
      if(detail.type==='show'){if(detail.panel==='models')setModelOpen(true);else window.dispatchEvent(new CustomEvent('agent-resume'));}
    };
    window.addEventListener('agent-state',listener);return()=>window.removeEventListener('agent-state',listener);
  },[onHistory,chat.id]);
  function draft(value:string,next:Mention[]=validMentions(value,mentions),position=value.length){setText(value);setMentions(next);setCaret(position);if(custom){replyDrafts.current.set(requestKey,{text:value,editing:true});}else host('draft',{threadId:chat.id,text:value,mentions:next});}
  function pick(item:Choice){
    if(item.group==='Skills'||item.group==='Plugins'||item.group==='Files'){
      const kind=item.group==='Skills'?'skill':item.group==='Plugins'?'plugin':'file';
      const name=kind==='file'?item.value.split('/').pop()||item.value:item.value;
      const result=insertMention({text,mentions},tokenStart,caret,{kind,name,path:item.path||item.value});
      const spaced=editPrompt(result,result.caret,result.caret,' ');draft(spaced.text,spaced.mentions,spaced.caret);
      if(kind==='file'){host('attachProjectFile',{threadId:chat.id,path:item.value});setFiles([]);}
      textarea.current?.focus();
    }else if(item.value==='/model'){setModelOpen(true);draft('');}
    else if(item.value==='/resume'){window.dispatchEvent(new CustomEvent('agent-resume'));draft('');}
    else {const result=editPrompt({text,mentions},tokenStart,caret,item.value+' ');draft(result.text,result.mentions,result.caret);}
    textarea.current?.focus();
  }
  function send(){
    if(approval||busy||locked.current||(!text.trim()&&!chat.files.length))return;
    if(text.trim()==='/model'){setModelOpen(true);draft('');return;}
    if(text.trim()==='/resume'){window.dispatchEvent(new CustomEvent('agent-resume'));draft('');return;}
    locked.current=true;const requestId=crypto.randomUUID();pendingSubmission.current={id:requestId,text,mentions};host('send',{threadId:chat.id,requestId,text,mentions,skill:mentions.find(m=>m.kind==='skill')?.name,plugin:mentions.filter(m=>m.kind==='plugin').map(m=>({name:m.name,path:m.path}))[0]});
    setTimeout(()=>{locked.current=false;},1000);
  }
  function fail(message:string){setError(message);window.dispatchEvent(new CustomEvent('agent-ui-notice',{detail:{text:message,threadId:chat.id}}));}
  function readFiles(selected:File[]){
    if(custom)return;
    if(chat.files.length+selected.length>MAX_ATTACHMENTS){fail(`Up to ${MAX_ATTACHMENTS} attachments are supported`);return;}
    for(const file of selected)try{
      attachmentRoute(file.type,file.size);const reader=new FileReader();reader.onerror=()=>fail('Could not read the attachment.');
      reader.onload=()=>host('uploadBinary',{threadId:chat.id,name:file.name,mime:file.type,data:String(reader.result).split(',')[1]});reader.readAsDataURL(file);
    }catch(e){fail(e instanceof Error?e.message:'Could not attach file.');}
  }

  const model=state.models.find(m=>m.model===chat.model),efforts=model?.supportedReasoningEfforts||[];
  useEffect(()=>setEffortPreview(undefined),[chat.model,chat.effort,modelOpen]);
  const effortIndex=Math.max(0,efforts.findIndex(r=>r.reasoningEffort===chat.effort));
  return <section className="composer-section">
    <div className={`composer${approval?' has-approval':''}`} onDragOver={e=>e.preventDefault()} onDrop={e=>{e.preventDefault();readFiles(Array.from(e.dataTransfer.files));}}>
      {approval&&<section className="approval-menu" aria-label="Approval request">
        <div className="approval-title">{approval.params.reason||(approval.method==='item/fileChange/requestApproval'?'Allow file changes?':'Run this command?')}</div>
        {approval.params.command&&<details className="approval-details"><summary aria-label="Expand command"><pre>{approval.params.command||'File changes'}</pre><svg width="16" height="9" viewBox="0 0 16 9" fill="none" stroke="currentColor" strokeWidth="1.2"><path d="m3 3 5 2.5L13 3"/></svg></summary><pre>{approval.params.command?.trimEnd()}</pre></details>}
        <div className="approval-options" role="group" aria-label="Approval options">{approvalChoices.map((label,index)=><button key={label} ref={node=>{optionRefs.current[index]=node;}} className={approvalSelection===index?'selected':''} tabIndex={approvalSelection===index?0:-1} disabled={approvalLocked} onFocus={()=>setApprovalSelection(index)} onClick={()=>chooseApproval(index)} onKeyDown={e=>{
          if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();const next=(index+(e.key==='ArrowDown'?1:2))%3;setApprovalSelection(next);optionRefs.current[next]?.focus();}
          if(e.key==='Enter'){e.preventDefault();chooseApproval(index);}
        }}>{index+1}. {label}</button>)}</div>
      </section>}
      {!custom&&!!chat.files.length&&<div className="attachments" role="group" aria-label="Attachments">{chat.files.map(file=><div className={`attachment${file.preview?' image-attachment':''}`} key={file.id}>{file.preview&&<img src={file.preview} alt={file.name}/>}{!file.preview&&<svg className="document-icon" width="23" height="27" viewBox="0 0 24 28" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round"><path d="M5 2h9l6 6v18H5zM14 2v7h6M8 15h9M8 19h7"/></svg>}<span className="attachment-caption"><span>{file.name.lastIndexOf('.')>0?file.name.slice(0,file.name.lastIndexOf('.')):file.name}</span><small>{file.name.lastIndexOf('.')>0?file.name.split('.').pop()?.toUpperCase()||'FILE':file.image?'IMAGE':'FILE'}</small></span><button className="chip-close" aria-label={`Remove ${file.name}`} onClick={()=>host('removeAttachment',{threadId:chat.id,id:file.id})}>×</button></div>)}</div>}
      {!!menu.length&&<div className="suggestions" role="listbox" aria-label={slash?'Commands, skills and plugins':'Files'}>{menu.map((item,i)=><div key={`${item.group}:${item.path||item.value}`}>
        {(i===0||menu[i-1].group!==item.group)&&<div className="menu-label">{item.group}</div>}
        <button role="option" aria-selected={selection===i} className={selection===i?'selected':''} onMouseDown={e=>{e.preventDefault();pick(item);}}><strong>{item.group==='Skills'||item.group==='Plugins'?'/':''}{item.value}</strong><span>{item.label}</span></button>
      </div>)}</div>}
      <RichPrompt key={`${chat.id}:${custom?requestKey:"draft"}`} ref={textarea} text={text} caret={caret} mentions={custom?[]:mentions} disabled={!state.connected||!state.signedIn} onCaret={setCaret} onEdit={(p,c)=>draft(p.text,p.mentions,c)} onScroll={field=>{field.dataset.scrolling='true';if(scrollIdle.current)clearTimeout(scrollIdle.current);scrollIdle.current=setTimeout(()=>{delete field.dataset.scrolling;},900);}} onPasteFiles={e=>{const pasted=Array.from(e.clipboardData.files);if(pasted.length){e.preventDefault();readFiles(pasted);return true;}return false;}} onKeyDown={e=>{
        if(e.nativeEvent.isComposing||e.keyCode===229)return;
        if(menu.length&&['ArrowDown','ArrowUp'].includes(e.key)){e.preventDefault();setSelection((selection+(e.key==='ArrowDown'?1:menu.length-1))%menu.length);return;}
        if(e.key==='Escape'){if(custom){e.preventDefault();leaveApproval();return;}setDismissed(true);setModelOpen(false);return;}
        if(custom&&e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing&&e.keyCode!==229){e.preventDefault();sendApproval();return;}
        const action=enterAction({key:e.key,shiftKey:e.shiftKey,isComposing:e.nativeEvent.isComposing||e.keyCode===229},menu.length>0,busy||!!approval||locked.current);
        if(action==='select'){e.preventDefault();pick(menu[selection]||menu[0]);}
        if(action==='send'){e.preventDefault();send();}
        if(action==='ignore'&&e.key==='Enter'&&!e.shiftKey)e.preventDefault();
      }}/>
      <div className="composer-toolbar"><button disabled={custom} className="attach-button" aria-label="Add attachment" title="Add files or images" onClick={()=>host('attach',{threadId:chat.id})}><svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="m8 12 6-6a3.5 3.5 0 0 1 5 5l-8 8a5.5 5.5 0 0 1-8-8l8-8M7 13l7-7a1.5 1.5 0 0 1 2 2l-8 8"/></svg></button>
    <div className="model-row"><div className="model-anchor"><button aria-label="Model" aria-expanded={modelOpen} onClick={()=>setModelOpen(!modelOpen)}>{model?.displayName||chat.model||'Choose model'} · {chat.effort} <span>⌄</span></button>
      {modelOpen&&<><button className="popover-dismiss" aria-label="Close models" onClick={()=>setModelOpen(false)}/><section className="model-popover popover" role="dialog" aria-label="Models"><div className="menu-label">Model</div>{state.models.length?state.models.map(m=><button key={m.model} className="model-option" onClick={()=>{host('model',{threadId:chat.id,model:m.model});}}><span>{m.displayName}</span><span>{chat.model===m.model?'✓':''}</span></button>):<p className="muted">Models are unavailable.</p>}<div className="picker-reasoning">{!!efforts.length&&<label className="effort-control"><span>{efforts[effortPreview??effortIndex]?.reasoningEffort}</span><input aria-label="Reasoning effort" title={efforts[effortIndex]?.description} type="range" min="0" max={Math.max(0,efforts.length-1)} step="1" value={effortPreview??effortIndex} disabled={efforts.length<2} onPointerDown={()=>{effortDrag.current=true;}} onChange={e=>{const value=Number(e.target.value);setEffortPreview(value);if(!effortDrag.current)host('model',{threadId:chat.id,model:chat.model,effort:efforts[value].reasoningEffort});}} onPointerUp={e=>{effortDrag.current=false;host('model',{threadId:chat.id,model:chat.model,effort:efforts[Number(e.currentTarget.value)].reasoningEffort});}} onPointerCancel={()=>{effortDrag.current=false;setEffortPreview(undefined);}} onBlur={e=>{if(effortDrag.current){effortDrag.current=false;host('model',{threadId:chat.id,model:chat.model,effort:efforts[Number(e.currentTarget.value)].reasoningEffort});}}}/></label>}</div></section></>}
    </div></div>
        {custom?<button className="send" aria-label="Send" disabled={approvalLocked||!state.connected} onClick={sendApproval}><SendArrow/></button>:busy?<button className="send stop" aria-label="Stop" onClick={()=>host('stop',{threadId:chat.id})}><span/></button>:<button className="send" aria-label="Send" disabled={!!approval||!state.connected||!state.signedIn||(!text.trim()&&!chat.files.length)} onClick={send}><SendArrow/></button>}
      </div>
    </div>
    {error&&<span className="sr-only" role="status">{error}</span>}
  </section>;
}
