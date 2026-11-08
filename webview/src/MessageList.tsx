import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {Virtuoso} from 'react-virtuoso';
import {host, type Item} from './types';
function Message({item}:{item:Item}) {
  if (item.type === 'userMessage') return <article className="message user"><div className="role">Вы</div><div className="user-text">{item.content?.map((c,i)=> <span key={i}>{c.type === 'text' ? c.text : c.type === 'localImage' || c.type === 'image' ? '▧ Изображение' : c.type}</span>)}</div></article>;
  if (item.type === 'agentMessage' || item.type === 'plan') return <article className="message assistant"><div className="role">{item.type === 'plan' ? 'План' : 'Codex'}</div><ReactMarkdown remarkPlugins={[remarkGfm]} components={{
    a:({href,children})=><a href={href} onClick={e=>{e.preventDefault(); if (href) host('openUrl',{url:href});}}>{children}</a>,
    pre:({children})=><div className="code-block"><button className="copy" onClick={e=>{const text=e.currentTarget.parentElement?.querySelector('code')?.textContent; if (text) navigator.clipboard.writeText(text).catch(()=>{});}}>Копировать</button><pre>{children}</pre></div>,
    img:()=> <span>[Изображение из ответа]</span>
  }}>{item.text || ''}</ReactMarkdown></article>;
  if (item.type === 'reasoning' || item.type === 'hookPrompt') return null;
  return <details className="action"><summary><span className={item.status === 'inProgress' ? 'activity running' : 'activity'}/> {item.type === 'commandExecution' ? item.command : item.type === 'fileChange' ? 'Изменение файлов' : item.type === 'contextCompaction' ? 'Сжатие контекста' : item.tool ? `${item.server || ''} / ${item.tool}` : item.type}<small>{item.status}</small></summary>{item.aggregatedOutput && <pre>{item.aggregatedOutput}</pre>}{item.changes?.map(c=><div key={c.path}>{c.path}</div>)}</details>;
}
export function MessageList({items}:{items:Item[]}) {
  const visible = items.filter(i=>i.type !== 'reasoning' && i.type !== 'hookPrompt');
  return <Virtuoso className="message-list" data={visible} followOutput="smooth" initialTopMostItemIndex={Math.max(0,visible.length-1)} computeItemKey={(_,item)=>item.id} itemContent={(_,item)=><Message item={item}/>} />;
}
