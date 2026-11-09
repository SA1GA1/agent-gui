import {useCallback,useEffect,useRef,useState} from 'react';
import {host,type State} from './types';
import {Composer} from './Composer';
import {MessageList} from './MessageList';
const initial:State={type:'state',connected:false,signedIn:false,status:'',version:'',binary:'',cwd:'',branch:'',activeId:'',sessions:[],models:[],history:[],historyCursor:null,skills:[]};
type Prompt={id:string;text:string};
export function App(){
  const [state,setState]=useState(initial),[popover,setPopover]=useState<'history'|'quota'|null>(null),[query,setQuery]=useState(''),[prompts,setPrompts]=useState<Prompt[]>([]),[tabMenu,setTabMenu]=useState<string|null>(null),[uiNotices,setUiNotices]=useState<{id:string;text:string;threadId:string;expiresAt:number}[]>([]),[resumeOpen,setResumeOpen]=useState(false),[conversations,setConversations]=useState<{id:string;name?:string;preview?:string}[]>([]),[resumeCursor,setResumeCursor]=useState<string|null>(null);
  const activeId=useRef(''),requestId=useRef('');
  const chat=state.sessions.find(s=>s.id===state.activeId);
  const openHistory=useCallback(()=>{setPopover('history');setQuery('');},[]);
  useEffect(()=>{
    const receive=(e:Event)=>{const next=(e as CustomEvent).detail;
      if(next.type==='state'){activeId.current=next.activeId;setState(next);}
      if(next.type==='promptHistoryResult'&&next.threadId===activeId.current&&next.requestId===requestId.current)setPrompts(next.prompts);
      if(next.type==='show'&&next.panel==='history')openResume();
      if(next.type==='historyResult'){setConversations(previous=>[...previous,...next.entries.filter((entry:{id:string})=>!previous.some(p=>p.id===entry.id))]);setResumeCursor(next.cursor||null);}
    };
    const openResume=()=>{setResumeOpen(true);setPopover(null);setConversations([]);setResumeCursor(null);host('history',{query:''});};
    const notice=(e:Event)=>setUiNotices(previous=>[...previous,{...(e as CustomEvent).detail,id:crypto.randomUUID(),expiresAt:Date.now()+180000}].slice(-4));
    const focus=()=>host('refresh');
    window.addEventListener('agent-resume',openResume);window.addEventListener('agent-state',receive);window.addEventListener('agent-ui-notice',notice);window.addEventListener('focus',focus);host('ready');
    return()=>{window.removeEventListener('agent-resume',openResume);window.removeEventListener('agent-state',receive);window.removeEventListener('agent-ui-notice',notice);window.removeEventListener('focus',focus);};
  },[openHistory]);
  useEffect(()=>{setPopover(null);setTabMenu(null);setQuery('');setPrompts([]);requestId.current='';},[state.activeId,state.connected]);
  useEffect(()=>{
    if(popover!=='history'||!chat)return;
    const id=crypto.randomUUID();requestId.current=id;
    setPrompts(chat.items.filter(i=>i.type==='userMessage').map(i=>({id:i.id,text:i.content?.filter(c=>c.type==='text').map(c=>c.text).join('\n')||'Image or attachment'})));
    host('promptHistory',{threadId:chat.id,requestId:id,query:''});
    return()=>{requestId.current='';};
  },[popover,chat?.id]);
  useEffect(()=>{
    if(!uiNotices.length)return;
    const timer=setTimeout(()=>setUiNotices(current=>current.filter(n=>n.expiresAt>Date.now())),Math.max(1,Math.min(...uiNotices.map(n=>n.expiresAt))-Date.now()));
    return()=>clearTimeout(timer);
  },[uiNotices]);
  const [signInNotice,setSignInNotice]=useState(false);
  useEffect(()=>{if(!state.connected||state.signedIn){setSignInNotice(false);return;}setSignInNotice(true);const timer=setTimeout(()=>setSignInNotice(false),180000);return()=>clearTimeout(timer);},[state.connected,state.signedIn]);
  const five=state.quotaWindows?.filter(w=>w.minutes===300&&w.remaining!==null)||[];
  const remaining=five.length?Math.min(...five.map(w=>w.remaining!)):null;
  const contextClass=chat&&!chat.contextStale&&chat.contextPercent!==null?(chat.contextPercent>75?'critical':chat.contextPercent>50?'warning':''):'';
  const notices=state.notices?.filter(n=>!n.threadId||n.threadId===state.activeId)||[];
  function reveal(itemId:string){if(!chat)return;host('revealPrompt',{threadId:chat.id,itemId});window.dispatchEvent(new CustomEvent('agent-state',{detail:{type:'revealPrompt',threadId:chat.id,itemId}}));setPopover(null);}
  return <main onKeyDown={e=>{if(e.key==='Escape'){setPopover(null);setTabMenu(null);setResumeOpen(false);}}}>
    <header><nav className="tabs" aria-label="Chats">{state.sessions.filter(s=>s.open).map(s=><div className={s.id===state.activeId?'tab active':'tab'} key={s.id}><button onClick={()=>host('select',{threadId:s.id})} onDoubleClick={()=>{const title=prompt('Rename chat',s.title);if(title?.trim())host('rename',{threadId:s.id,title:title.trim()});}} onContextMenu={e=>{e.preventDefault();setTabMenu(s.id);}}>{s.state!=='idle'&&<span className="tab-dot busy"/>}{s.title}</button>{s.id===state.activeId&&<button className="tab-close" aria-label={`Close ${s.title}`} onClick={()=>host('close',{threadId:s.id})}>×</button>}</div>)}</nav><button className="new-chat" aria-label="New chat" disabled={!state.connected} onClick={()=>host('new')}>+</button></header>
    {resumeOpen&&<><button className="popover-dismiss" aria-label="Close conversations" onClick={()=>setResumeOpen(false)}/><section className="history-popup popover" role="dialog" aria-label="Resume a conversation"><div className="menu-label">Resume a conversation</div><div className="prompt-results">{conversations.map(c=><button key={c.id} onClick={()=>{host('resume',{threadId:c.id});setResumeOpen(false);}}>{c.name||c.preview||'Conversation'}</button>)}{!conversations.length&&<p className="muted">No conversations found.</p>}{resumeCursor&&<button onClick={()=>host('history',{query:'',cursor:resumeCursor})}>Load more conversations</button>}</div></section></>}
    {tabMenu&&<><button className="popover-dismiss" aria-label="Close chat menu" onClick={()=>setTabMenu(null)}/><div className="tab-menu popover" role="menu"><button role="menuitem" onClick={()=>{const s=state.sessions.find(s=>s.id===tabMenu);const title=prompt('Rename chat',s?.title);if(title?.trim())host('rename',{threadId:tabMenu,title:title.trim()});setTabMenu(null);}}>Rename chat</button><button role="menuitem" onClick={()=>{host('close',{threadId:tabMenu});setTabMenu(null);}}>Close chat</button></div></>}
    {(!!notices.length||uiNotices.some(n=>n.threadId===state.activeId)||signInNotice)&&<section className="chat-notices" aria-label="Notifications">
      {notices.map(n=><div className={`chat-notice ${n.level}`} key={n.id}><svg aria-hidden="true" className="notice-symbol" width="22" height="22" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M10 2 18 17H2Z" strokeLinejoin="round"/><path d="M10 7v5"/><circle cx="10" cy="14.5" r=".7" fill="currentColor"/></svg><span>{n.text}</span>{n.action==='compact'&&chat&&<button className="notice-action" disabled={chat.state!=='idle'} onClick={()=>host('compact',{threadId:chat.id})}>Compact</button>}<button className="notice-close" aria-label="Dismiss notification" onClick={()=>host('dismissNotice',{id:n.id})}>×</button></div>)}
      {uiNotices.filter(n=>n.threadId===state.activeId).map(n=><div className="chat-notice error" key={n.id}><span>{n.text}</span><button className="notice-close" aria-label="Dismiss attachment notification" onClick={()=>setUiNotices(all=>all.filter(x=>x.id!==n.id))}>×</button></div>)}
      {signInNotice&&<div className="chat-notice info"><span>Sign in to start using Codex.</span><button onClick={()=>host('login')}>Sign in</button></div>}
    </section>}
    {chat?.items.length?<MessageList key={chat.id} threadId={chat.id} items={chat.items}/>:<section className="empty"><h1>{state.connected?'What can I help with?':'Codex'}</h1>{state.connected&&<p>Ask a question or describe a task.</p>}</section>}
    {!state.connected?<div className="connect-bottom"><button className="connect-button" onClick={()=>host('connect',{binary:state.binary})}>Connect Codex</button></div>:chat?<>
      <Composer chat={chat} state={state} onHistory={openHistory}/>
      <footer><div className="footer-anchor"><button aria-label="History" aria-expanded={popover==='history'} onClick={()=>popover==='history'?setPopover(null):openHistory()}><svg aria-hidden="true" className="clock-icon" width="18" height="18" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="10" cy="10" r="7.5"/><path d="M10 5.5v5l3 2"/></svg> History</button>
        {popover==='history'&&<><button className="popover-dismiss" aria-label="Close history" onClick={()=>setPopover(null)}/><section className="history-popup popover" aria-label="Prompt history"><div className="menu-label">This chat’s prompts</div><input aria-label="Search prompts" placeholder="Search prompts" value={query} onChange={e=>setQuery(e.target.value)}/><div className="prompt-results">{prompts.filter(p=>p.text.toLowerCase().includes(query.toLowerCase())).map(p=><button key={p.id} onClick={()=>reveal(p.id)}>{p.text}</button>)}{!prompts.some(p=>p.text.toLowerCase().includes(query.toLowerCase()))&&<p className="muted">No prompts found.</p>}</div></section></>}
      </div><div className="footer-right"><div className="footer-anchor"><button className="quota-button" aria-expanded={popover==='quota'} onClick={()=>{setPopover(popover==='quota'?null:'quota');host('refresh');}}><svg aria-hidden="true" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.4"><circle cx="10" cy="10" r="7.5"/><path d="M10 2.5V10h7.5"/><path d="M10 2.5A7.5 7.5 0 0 1 17.5 10H10Z" fill="currentColor"/></svg>5h: {remaining===null?'Unknown':`${Math.floor(remaining)}%`}</button>
        {popover==='quota'&&<><button className="popover-dismiss" aria-label="Close quota" onClick={()=>setPopover(null)}/><section className="quota-popup popover" aria-label="Usage limits"><div className="menu-label">Usage limits</div>{[[300,'5 hours'],[10080,'Weekly']].map(([minutes,label])=>{const windows=state.quotaWindows?.filter(w=>w.minutes===minutes)||[];return <div key={minutes}><strong>{label}</strong>{windows.length?windows.map((w,i)=><p key={i}>{w.name}: {w.remaining===null?'Unknown':`${Math.floor(w.remaining)}% remaining`}<br/><small>Resets: {w.resetsAt===null?'Unknown':new Date(w.resetsAt*1000).toLocaleString()}</small>{w.remaining!==null&&<span className="quota-meter"><span style={{width:`${w.remaining}%`}}/></span>}</p>):<p>Unknown</p>}</div>})}</section></>}
      </div><span className={`context ${contextClass}`} title="Context used">Context: {chat.contextPercent===null?'Unknown':`${Math.floor(chat.contextPercent)}%`}</span></div></footer>
    </>:<div className="connect-bottom"><button className="connect-button" onClick={()=>host('new')}>New chat</button></div>}
  </main>;
}
