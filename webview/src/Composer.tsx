import {useEffect,useRef,useState} from 'react';
import {enterAction} from './keyboard';
import {attachmentRoute} from './droppedFiles';
import {host,type Chat,type State} from './types';
const commands = [ ['/new','Новый разговор'],['/model','Выбор модели'],['/status','Состояние Codex'],['/compact','Сжать контекст'],['/resume','История разговоров'] ];
export function Composer({chat,state,onHistory}:{chat:Chat;state:State;onHistory:()=>void}) {
  const [text,setText] = useState(chat.draft);
  const [files,setFiles] = useState<string[]>([]);
  const [selection,setSelection] = useState(0);
  const [skill,setSkill] = useState('');
  const textarea = useRef<HTMLTextAreaElement>(null);
  const modelSelect = useRef<HTMLSelectElement>(null);
  const locked = useRef(false);
  const searchId = useRef('');
  const busy = chat.state !== 'idle';
  const slash = text.startsWith('/') && !text.includes(' ');
  const at = text.match(/(?:^|\s)@([^\s]*)$/);
  const menu = slash ? commands.filter(([c])=>c.startsWith(text)).map(([value,label])=>({value,label})) : at ? files.map(value=>({value,label:value})) : [];
  useEffect(()=>{setText(chat.draft);locked.current=false;},[chat.id,chat.draft,chat.state]);
  useEffect(()=>{setSelection(0);},[text]);
  useEffect(()=>{
    if (!at) {setFiles([]);return;}
    const requestId = crypto.randomUUID(); searchId.current=requestId;
    const timer=setTimeout(()=>host('searchFiles',{query:at[1],requestId}),180);
    return ()=>clearTimeout(timer);
  },[at?.[1]]);
  useEffect(()=>{
    const listener=(e:Event)=>{
      const detail=(e as CustomEvent).detail;
      if(detail.type==='files' && detail.requestId===searchId.current) setFiles(detail.files);
      if(detail.type==='show') {if(detail.panel==='models') modelSelect.current?.focus();else onHistory();}
    };
    window.addEventListener('agent-state',listener);return ()=>window.removeEventListener('agent-state',listener);
  },[onHistory]);
  function draft(value:string) {setText(value);host('draft',{threadId:chat.id,text:value});}
  function pick(value:string) {
    if(slash) draft(value+' ');
    else {host('attachProjectFile',{threadId:chat.id,path:value});draft(text.replace(/@[^\s]*$/,''));setFiles([]);}
    textarea.current?.focus();
  }
  function send() {
    if(busy || locked.current || (!text.trim() && !chat.files.length)) return;
    const trimmed=text.trim();
    if(trimmed==='/model') {modelSelect.current?.focus();draft('');return;}
    if(trimmed==='/resume') {onHistory();draft('');return;}
    locked.current=true;
    host('send',{threadId:chat.id,text,skill:skill || undefined});
    // Host state acknowledges or rejects the send; no automatic retry.
    setTimeout(()=>{locked.current=false;},1000);
  }
  function readFiles(list:File[]) {
    for(const file of list) {
      try {
        const route=attachmentRoute(file.type,file.size);
        const reader=new FileReader();
        reader.onerror=()=>alert('Не удалось прочитать файл');
        reader.onload=()=> route==='image'
          ? host('pasteImage',{threadId:chat.id,name:file.name,mime:file.type,data:String(reader.result).split(',')[1]})
          : host('uploadText',{threadId:chat.id,name:file.name,text:String(reader.result)});
        if(route==='image') reader.readAsDataURL(file); else reader.readAsText(file);
      } catch(e) {alert(e instanceof Error?e.message:'Неподдерживаемое вложение');}
    }
  }
  const model=state.models.find(m=>m.model===chat.model);
  return <section className="composer-section">
    {chat.contextPercent!==null && !chat.contextStale && chat.contextPercent>70 && <div className={`context-banner ${chat.contextLevel}`}><span>Контекст занят на {chat.contextPercent.toFixed(1)}%. Можно сжать историю.</span><button disabled={busy} onClick={()=>host('compact',{threadId:chat.id})}>/compact</button></div>}
    <div className="composer" onDragOver={e=>{e.preventDefault();}} onDrop={e=>{e.preventDefault();readFiles(Array.from(e.dataTransfer.files));}}>
      {!!chat.files.length && <div className="attachments">{chat.files.map(file=><div className="attachment" key={file.id}>{file.preview && <img src={file.preview} alt=""/>}<span>{file.name}</span><button aria-label={`Удалить ${file.name}`} onClick={()=>host('removeAttachment',{threadId:chat.id,id:file.id})}>×</button></div>)}</div>}
      {!!menu.length && <div className="suggestions" role="listbox">{menu.map((item,i)=><button role="option" aria-selected={selection===i} className={selection===i?'selected':''} key={item.value} onMouseDown={e=>{e.preventDefault();pick(item.value);}}><strong>{item.value}</strong><span>{slash?item.label:''}</span></button>)}</div>}
      <textarea ref={textarea} aria-label="Сообщение Codex" placeholder="Задайте вопрос или поручите задачу…" value={text} disabled={!state.connected || !state.signedIn} onChange={e=>draft(e.target.value)} onPaste={e=>{const images=Array.from(e.clipboardData.files).filter(f=>f.type.startsWith('image/'));if(images.length){e.preventDefault();readFiles(images);}}} onKeyDown={e=>{
        if(menu.length && ['ArrowDown','ArrowUp'].includes(e.key)){e.preventDefault();setSelection((selection+(e.key==='ArrowDown'?1:menu.length-1))%menu.length);return;}
        if(e.key==='Escape'){setFiles([]);return;}
        const action=enterAction({key:e.key,shiftKey:e.shiftKey,isComposing:e.nativeEvent.isComposing || e.keyCode===229},menu.length>0,busy || locked.current);
        if(action==='select'){e.preventDefault();pick(menu[selection]?.value || menu[0].value);}
        if(action==='send'){e.preventDefault();send();}
        if(action==='ignore' && e.key==='Enter' && !e.shiftKey) e.preventDefault();
      }}/>
      <div className="composer-toolbar"><div className="tools"><button title="Добавить файлы или изображения" aria-label="Добавить вложение" onClick={()=>host('attach',{threadId:chat.id})}>＋</button><button title="Найти файл проекта" onClick={()=>{draft(text+' @');textarea.current?.focus();}}>@</button><button title="Команды" onClick={()=>{draft('/');textarea.current?.focus();}}>/</button></div>
        <div className="selectors"><select ref={modelSelect} aria-label="Модель" value={chat.model} onChange={e=>host('model',{threadId:chat.id,model:e.target.value})}>{state.models.map(m=><option key={m.model} value={m.model}>{m.displayName}</option>)}</select><select aria-label="Уровень рассуждения" value={chat.effort} onChange={e=>host('model',{threadId:chat.id,model:chat.model,effort:e.target.value})}>{model?.supportedReasoningEfforts.map(r=><option value={r.reasoningEffort} key={r.reasoningEffort}>{r.reasoningEffort}</option>)}</select></div>
        {busy ? <button className="send stop" aria-label="Остановить" onClick={()=>host('stop',{threadId:chat.id})}>■</button> : <button className="send" aria-label="Отправить" disabled={!state.connected || !state.signedIn || (!text.trim() && !chat.files.length)} onClick={send}>↑</button>}
      </div>
      {!!state.skills.length && <select className="skill-select" aria-label="Skill" value={skill} onChange={e=>setSkill(e.target.value)}><option value="">Без skill</option>{state.skills.filter(s=>s.enabled!==false).map(s=><option key={s.path} value={s.name}>{s.name}</option>)}</select>}
    </div>
    <div className="input-hint"><span>Enter — отправить · Shift + Enter — новая строка</span><span>Локальный Codex</span></div>
  </section>;
}
