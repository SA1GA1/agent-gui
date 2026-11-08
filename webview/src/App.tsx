import {useCallback,useEffect,useState} from 'react';
import {host,type State} from './types';
import {Composer} from './Composer';
import {MessageList} from './MessageList';
const initial:State={type:'state',connected:false,signedIn:false,status:'Подключите установленный Codex CLI',version:'',binary:'',cwd:'',branch:'Нет данных',activeId:'',sessions:[],models:[],history:[],historyCursor:null,skills:[]};
export function App() {
  const [state,setState]=useState(initial);
  const [quotaOpen,setQuotaOpen]=useState(false);
  const [settings,setSettings]=useState(false);
  const [history,setHistory]=useState(false);
  const [query,setQuery]=useState('');
  const [binary,setBinary]=useState('');
  const [preview,setPreview]=useState(!window.agentHost);
  const openHistory=useCallback(()=>{setHistory(true);host('history',{query:''});},[]);
  useEffect(()=>{
    const receive=(e:Event)=>{const next=(e as CustomEvent).detail;if(next.type==='state'){setState(next);setBinary(next.binary);}};
    window.addEventListener('agent-state',receive);host('ready');
    const focus=()=>host('refresh');window.addEventListener('focus',focus);
    return ()=>{window.removeEventListener('agent-state',receive);window.removeEventListener('focus',focus);};
  },[]);
  const chat=state.sessions.find(s=>s.id===state.activeId);
  const five=state.quotaWindows?.filter(w=>w.minutes===300 && w.remaining!==null) || [];
  const fiveRemaining=five.length?Math.min(...five.map(w=>w.remaining!)):null;
  const active=state.sessions.filter(s=>s.state!=='idle');
  return <main>
    <header><div className="brand"><div className="brand-mark">⌘</div><span>Agent GUI <small>CODEX</small></span></div><div className="header-actions"><button className="quota-button" onClick={()=>{setQuotaOpen(!quotaOpen);host('refresh');}}>5 ч: {fiveRemaining===null?'нет данных':`${Math.floor(fiveRemaining)}%`}</button><button title="История разговоров" onClick={openHistory}>◷</button><button title="Настройки" onClick={()=>setSettings(!settings)}>⚙</button></div></header>
    {quotaOpen && <section className="quota-popup"><div className="section-heading"><strong>Лимиты Codex</strong><button onClick={()=>setQuotaOpen(false)}>×</button></div>{[[300,'5 часов'],[10080,'Неделя']].map(([minutes,label])=>{
      const windows=state.quotaWindows?.filter(w=>w.minutes===minutes) || [];
      return <div key={minutes}><strong>{label}</strong>{windows.length?windows.map((w,i)=><p key={i}>{w.name}: {w.remaining===null?'нет данных':`${Math.floor(w.remaining)}% осталось`}<br/><small>Обновление: {w.resetsAt?new Date(w.resetsAt*1000).toLocaleString():'нет данных'}</small></p>):<p>Нет данных</p>}</div>;
    })}</section>}
    <nav className="tabs">{state.sessions.filter(s=>s.open).map(s=><div className={s.id===state.activeId?'tab active':'tab'} key={s.id}><button onClick={()=>host('select',{threadId:s.id})} onDoubleClick={()=>{const title=prompt('Название разговора',s.title);if(title)host('rename',{threadId:s.id,title});}}><span className={s.state==='idle'?'tab-dot':'tab-dot busy'}/>{s.title}</button><button className="close-tab" title="Закрыть вкладку; разговор останется в истории" onClick={()=>host('close',{threadId:s.id})}>×</button></div>)}<button title="Новый чат" disabled={!state.connected} onClick={()=>host('new')}>＋</button></nav>
    {preview && <div className="notice">Предпросмотр интерфейса. Соединение с IDE недоступно.<button onClick={()=>setPreview(false)}>×</button></div>}
    {settings && <section className="settings"><label>Путь к Codex CLI<input value={binary} placeholder="Автоопределение" onChange={e=>setBinary(e.target.value)}/></label><button className="primary" onClick={()=>host('connect',{binary})}>Подключить</button><p>Используется ваш локальный CLI и его авторизация. Плагин не хранит ключи.</p><label className="warmup-setting"><input type="checkbox" checked={state.autoWindowEnabled??true} onChange={()=>host('toggleWarmup')}/>Автостарт 5-часового окна, когда задачи не выполняются</label><small>{state.version || 'Проверенная версия: 0.163.0'}</small></section>}
    {history && <section className="history"><div className="section-heading"><strong>История проекта</strong><button onClick={()=>setHistory(false)}>×</button></div><form onSubmit={e=>{e.preventDefault();host('history',{query});}}><input placeholder="Найти разговор…" value={query} onChange={e=>setQuery(e.target.value)}/><button>Найти</button></form>{state.history.map(s=><div className="history-row" key={s.id}><button onClick={()=>{host('resume',{threadId:s.id});setHistory(false);}}>{s.name || s.preview || 'Разговор'}</button><button title="Архивировать" onClick={()=>{host('archive',{threadId:s.id});host('history',{query});}}>⌑</button></div>)}{state.historyCursor && <button onClick={()=>host('history',{query,cursor:state.historyCursor})}>Следующая страница</button>}</section>}
    {!state.connected ? <section className="welcome"><div className="welcome-icon">⌘</div><h1>Codex в вашем проекте</h1><p>Задавайте вопросы, меняйте код<br/>и продолжайте работу в привычном чате.</p><button className="primary" onClick={()=>host('connect',{binary})}>Подключить Codex</button><button className="text-button" onClick={()=>setSettings(true)}>Указать путь к CLI</button><div className="welcome-features"><span>↵ Многострочный ввод</span><span>▧ Файлы и изображения</span><span>⑂ Ветка и контекст</span></div></section> : !state.signedIn ? <section className="welcome"><h1>Войдите в Codex</h1><p>Вход откроется в вашем браузере.</p><button className="primary" onClick={()=>host('login')}>Войти через ChatGPT</button></section> : !chat ? <section className="welcome"><h1>Начните новый разговор</h1><button className="primary" onClick={()=>host('new')}>Новый чат</button></section> : <>
      {chat.items.length ? <MessageList key={chat.id} items={chat.items}/> : <section className="empty"><div className="eyebrow">ВАШ ПРОЕКТ. ВАШ CODEX.</div><h1>Что сделаем?</h1><p>Опишите задачу. Добавьте нужные файлы через @<br/>или прикрепите изображение.</p><div className="suggestion-cards">{['Объясни структуру проекта','Помоги найти ошибку','Предложи рефакторинг'].map(t=><button key={t} onClick={()=>host('draft',{threadId:chat.id,text:t})}>{t}<span>↗</span></button>)}</div></section>}
      {!!chat.approvals.length && <section className="approvals">{chat.approvals.map(a=><div key={a.id}><strong>Codex запрашивает разрешение</strong><p>{a.params.reason || 'Выполнить действие?'}</p>{a.params.command && <pre>{a.params.command}</pre>}{a.params.cwd && <small>{a.params.cwd}</small>}<div className="approval-buttons"><button className="primary" onClick={()=>host('approve',{threadId:chat.id,requestId:String(a.id),decision:'accept'})}>Разрешить один раз</button><button onClick={()=>host('approve',{threadId:chat.id,requestId:String(a.id),decision:'decline'})}>Отклонить</button></div></div>)}</section>}
      {!!state.notices?.length && <section className="chat-notices">{state.notices.filter(n=>!n.threadId || n.threadId===chat.id).slice(-4).map(n=><div className={`chat-notice ${n.level}`} key={n.id}><span>{n.text}</span><button aria-label="Закрыть уведомление" onClick={()=>host('dismissNotice',{id:n.id})}>×</button></div>)}</section>}
      <Composer key={chat.id} chat={chat} state={state} onHistory={openHistory}/>
    </>}
    {active.length>0 && <div className="tasks">{active.map(s=><button key={s.id} onClick={()=>host('resume',{threadId:s.id})}><span className="activity running"/>{s.title} · {s.state==='queued'?'в очереди':s.state==='compacting'?'сжатие':'в работе'}</button>)}<small>Параллельные чаты меняют файлы в одной папке.</small></div>}
    <footer><span title={state.cwd}>⑂ {state.branch}</span><span className={`context ${chat?.contextStale?'stale':chat?.contextLevel || ''}`} title={chat?.contextStale?'Данные контекста устарели':'Использование последнего запроса'}>{chat?.contextPercent==null?'Контекст: нет данных':`Контекст: ${chat.contextPercent.toFixed(1)}%${chat.contextStale?' · устарел':''}`}</span></footer>
    <div className="connection-status"><span className={state.connected?'status-dot connected':'status-dot'}/><span>{state.status}</span></div>
  </main>;
}
