import {useEffect,useRef,useState} from 'react';
import {enterAction} from './keyboard';
import {attachmentRoute} from './droppedFiles';
import {host,type Chat,type State} from './types';
const SendArrow=()=> <svg width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M10 16V4m-5 5 5-5 5 5"/></svg>;
const commands = [['/new','New chat'],['/model','Choose model'],['/status','Codex status'],['/compact','Compact context'],['/resume','Resume conversation']];
type Choice={value:string;label:string;group:string;path?:string};
export function Composer({chat,state,onHistory}:{chat:Chat;state:State;onHistory:()=>void}) {
  const [text,setText]=useState(chat.draft),[files,setFiles]=useState<string[]>([]),[selection,setSelection]=useState(0),[skill,setSkill]=useState('');
  const [plugin,setPlugin]=useState<{name:string;path:string}>(),[modelOpen,setModelOpen]=useState(false),[dismissed,setDismissed]=useState(false),[error,setError]=useState(''),[effortPreview,setEffortPreview]=useState<number>();
  const textarea=useRef<HTMLTextAreaElement>(null),locked=useRef(false),searchId=useRef(''),effortDrag=useRef(false);
  const approval=chat.approvals[0],requestKey=approval?`${chat.id}:${approval.id}`:'';
  const [customKey,setCustomKey]=useState(''),[approvalSelection,setApprovalSelection]=useState(0),[approvalLocked,setApprovalLocked]=useState(false);
  const replyDrafts=useRef(new Map<string,{text:string;editing:boolean}>()),optionRefs=useRef<(HTMLButtonElement|null)[]>([]);
  const custom=!!requestKey&&customKey===requestKey;
  const busy=chat.state!=='idle';
  const approvalChoices=approval?['Allow once',approval.method==='item/fileChange/requestApproval'?'Allow changes to these files for this session':'Allow for this session','Decline or write a reply']:[];
  useEffect(()=>{
    const saved=replyDrafts.current.get(requestKey);
    setCustomKey(saved?.editing?requestKey:'');setText(saved?.editing?saved.text:chat.draft);setApprovalLocked(false);setApprovalSelection(saved?.editing?2:0);
    for(const key of replyDrafts.current.keys())if(key.startsWith(chat.id+':')&&key!==requestKey)replyDrafts.current.delete(key);
  },[requestKey,chat.id]);
  function chooseApproval(index:number){
    if(approvalLocked||!approval)return;
    setApprovalSelection(index);
    if(index===2){const saved=replyDrafts.current.get(requestKey)||{text:'',editing:true};saved.editing=true;replyDrafts.current.set(requestKey,saved);setCustomKey(requestKey);setText(saved.text);requestAnimationFrame(()=>textarea.current?.focus());}
    else {setApprovalLocked(true);host('approve',{threadId:chat.id,requestId:String(approval.id),decision:index===0?'accept':'acceptForSession'});}
  }
  function sendApproval(){if(approvalLocked||!approval)return;setApprovalLocked(true);host('approve',{threadId:chat.id,requestId:String(approval.id),decision:'cancel',text:text.trim()});}
  function leaveApproval(){const saved=replyDrafts.current.get(requestKey);if(saved)saved.editing=false;setCustomKey('');setText(chat.draft);requestAnimationFrame(()=>optionRefs.current[2]?.focus());}

  const slash=text.startsWith('/')&&!text.includes(' '),at=text.match(/(?:^|\s)@([^\s]*)$/);
  const term=slash?text.slice(1):at?.[1]||'';
  const menu:Choice[]=custom||dismissed?[]:slash?[
    ...commands.filter(([c])=>c.startsWith(text)).map(([value,label])=>({value,label,group:'Commands'})),
    ...state.skills.filter(s=>s.enabled!==false&&s.name.toLowerCase().includes(term.toLowerCase())).map(s=>({value:s.name,label:s.description,group:'Skills',path:s.path}))
  ]:at?[
    ...files.map(value=>({value,label:'',group:'Files'})),
    ...(state.plugins||[]).filter(p=>p.enabled!==false&&p.name.toLowerCase().includes(term.toLowerCase())).map(p=>({value:p.name,label:p.description,group:'Plugins',path:p.path}))
  ]:[];
  useEffect(()=>{if(!replyDrafts.current.get(requestKey)?.editing)setText(chat.draft);locked.current=false;},[chat.id,chat.draft,chat.state,custom]);
  useEffect(()=>{setSkill('');setPlugin(undefined);setFiles([]);setModelOpen(false);},[chat.id]);
  useEffect(()=>{setSelection(0);setDismissed(false);const field=textarea.current;if(field){field.style.height='0px';field.style.height=`${Math.min(124,Math.max(24,field.scrollHeight))}px`;field.style.overflowY=field.scrollHeight>124?'auto':'hidden';}},[text]);
  useEffect(()=>{
    searchId.current='';setFiles([]);
    if(!at)return;
    const requestId=crypto.randomUUID();searchId.current=requestId;
    const timer=setTimeout(()=>host('searchFiles',{query:at[1],requestId}),180);
    return ()=>{clearTimeout(timer);searchId.current='';};
  },[at?.[1],chat.id]);
  useEffect(()=>{
    const listener=(e:Event)=>{const detail=(e as CustomEvent).detail;
      if(detail.type==='files'&&detail.requestId===searchId.current)setFiles(detail.files);
      if(detail.type==='show'){if(detail.panel==='models')setModelOpen(true);else window.dispatchEvent(new CustomEvent('agent-resume'));}
    };
    window.addEventListener('agent-state',listener);return()=>window.removeEventListener('agent-state',listener);
  },[onHistory]);
  function draft(value:string){setText(value);if(custom){replyDrafts.current.set(requestKey,{text:value,editing:true});}else host('draft',{threadId:chat.id,text:value});}
  function pick(item:Choice){
    if(item.group==='Skills'){setSkill(item.value);draft('');}
    else if(item.group==='Plugins'){setPlugin({name:item.value,path:item.path!});draft(text.replace(/@[^\s]*$/,''));}
    else if(slash){
      if(item.value==='/model'){setModelOpen(true);draft('');}
      else if(item.value==='/resume'){window.dispatchEvent(new CustomEvent('agent-resume'));draft('');}
      else draft(item.value+' ');
    }else{host('attachProjectFile',{threadId:chat.id,path:item.value});draft(text.replace(/@[^\s]*$/,''));setFiles([]);}
    textarea.current?.focus();
  }
  function send(){
    if(approval||busy||locked.current||(!text.trim()&&!chat.files.length))return;
    if(text.trim()==='/model'){setModelOpen(true);draft('');return;}
    if(text.trim()==='/resume'){window.dispatchEvent(new CustomEvent('agent-resume'));draft('');return;}
    locked.current=true;host('send',{threadId:chat.id,text,skill:skill||undefined,plugin});
    setTimeout(()=>{locked.current=false;},1000);
  }
  function fail(message:string){setError(message);window.dispatchEvent(new CustomEvent('agent-ui-notice',{detail:{text:message,threadId:chat.id}}));}
  function readFiles(list:File[]){for(const file of list){try{
    const route=attachmentRoute(file.type,file.size),reader=new FileReader();reader.onerror=()=>fail('Could not read the attachment.');
    reader.onload=()=>route==='image'?host('pasteImage',{threadId:chat.id,name:file.name,mime:file.type,data:String(reader.result).split(',')[1]}):host('uploadText',{threadId:chat.id,name:file.name,text:String(reader.result)});
    if(route==='image')reader.readAsDataURL(file);else reader.readAsText(file);
  }catch(e){fail(e instanceof Error?e.message:'Unsupported attachment.');}}}
  const model=state.models.find(m=>m.model===chat.model),efforts=model?.supportedReasoningEfforts||[];
  useEffect(()=>setEffortPreview(undefined),[chat.effort,modelOpen]);
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
      {!custom&&!!chat.files.length&&<div className="attachments">{chat.files.map(file=><div className="attachment" key={file.id}>{file.preview&&<img src={file.preview} alt={file.name}/>}<span>{file.name}</span><button aria-label={`Remove ${file.name}`} onClick={()=>host('removeAttachment',{threadId:chat.id,id:file.id})}>×</button></div>)}</div>}
      {!custom&&(skill||plugin)&&<div className="selected-context">{skill&&<button onClick={()=>setSkill('')} title="Remove skill">/{skill} ×</button>}{plugin&&<button onClick={()=>setPlugin(undefined)} title="Remove plugin">@{plugin.name} ×</button>}</div>}
      {!!menu.length&&<div className="suggestions" role="listbox" aria-label={slash?'Commands and skills':'Files and plugins'}>{menu.map((item,i)=><div key={`${item.group}:${item.path||item.value}`}>
        {(i===0||menu[i-1].group!==item.group)&&<div className="menu-label">{item.group}</div>}
        <button role="option" aria-selected={selection===i} className={selection===i?'selected':''} onMouseDown={e=>{e.preventDefault();pick(item);}}><strong>{item.group==='Skills'?'/':''}{item.value}</strong><span>{item.label}</span></button>
      </div>)}</div>}
      <textarea ref={textarea} aria-label="Message Codex" placeholder="/ commands and skills · @ files and plugins" value={text} disabled={!state.connected||!state.signedIn} onChange={e=>draft(e.target.value)} onPaste={e=>{const images=Array.from(e.clipboardData.files).filter(f=>f.type.startsWith('image/'));if(images.length){e.preventDefault();readFiles(images);}}} onKeyDown={e=>{
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
      {modelOpen&&<><button className="popover-dismiss" aria-label="Close models" onClick={()=>setModelOpen(false)}/><section className="model-popover popover" role="dialog" aria-label="Models"><div className="menu-label">Model</div>{state.models.length?state.models.map(m=><button key={m.model} className="model-option" onClick={()=>{host('model',{threadId:chat.id,model:m.model});setModelOpen(false);}}><span>{m.displayName}</span><span>{chat.model===m.model?'✓':''}</span></button>):<p className="muted">Models are unavailable.</p>}<div className="picker-reasoning">{!!efforts.length&&<label className="effort-control"><span>{efforts[effortPreview??effortIndex]?.reasoningEffort}</span><input aria-label="Reasoning effort" title={efforts[effortIndex]?.description} type="range" min="0" max={Math.max(0,efforts.length-1)} step="1" value={effortPreview??effortIndex} disabled={efforts.length<2} onPointerDown={()=>{effortDrag.current=true;}} onChange={e=>{const value=Number(e.target.value);setEffortPreview(value);if(!effortDrag.current)host('model',{threadId:chat.id,model:chat.model,effort:efforts[value].reasoningEffort});}} onPointerUp={e=>{effortDrag.current=false;host('model',{threadId:chat.id,model:chat.model,effort:efforts[Number(e.currentTarget.value)].reasoningEffort});}} onPointerCancel={()=>{effortDrag.current=false;setEffortPreview(undefined);}} onBlur={e=>{if(effortDrag.current){effortDrag.current=false;host('model',{threadId:chat.id,model:chat.model,effort:efforts[Number(e.currentTarget.value)].reasoningEffort});}}}/></label>}</div></section></>}
    </div></div>
        {custom?<button className="send" aria-label="Send" disabled={approvalLocked||!state.connected} onClick={sendApproval}><SendArrow/></button>:busy?<button className="send stop" aria-label="Stop" onClick={()=>host('stop',{threadId:chat.id})}><span/></button>:<button className="send" aria-label="Send" disabled={!!approval||!state.connected||!state.signedIn||(!text.trim()&&!chat.files.length)} onClick={send}><SendArrow/></button>}
      </div>
    </div>
    {error&&<span className="sr-only" role="status">{error}</span>}
  </section>;
}
