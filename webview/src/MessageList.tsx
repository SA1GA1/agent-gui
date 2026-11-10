import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import 'katex/dist/katex.min.css';
import {normalizeMath} from './mathNormalizer';
import {useEffect,useRef} from 'react';
import {Virtuoso,type VirtuosoHandle} from 'react-virtuoso';
import {host, type Item} from './types';
function Message({item}:{item:Item}) {
  if (item.type === 'userMessage') return <article className="message user"><div className="role">You</div><div className="user-text">{item.content?.map((c,i)=> <span key={i}>{c.type === 'text' ? c.text : c.type === 'localImage' || c.type === 'image' ? '▧ Image' : c.type}</span>)}</div></article>;
  if (item.type === 'agentMessage' || item.type === 'plan') return <article className="message assistant"><div className="role">{item.type === 'plan' ? 'Plan' : 'Codex'}</div><ReactMarkdown remarkPlugins={[remarkGfm,remarkMath]} rehypePlugins={[[rehypeKatex,{trust:false,strict:'ignore',throwOnError:false,maxExpand:200,maxSize:20}]]} components={{
    a:({href,children})=><a href={href} onClick={e=>{e.preventDefault(); if (href) host('openUrl',{url:href});}}>{children}</a>,
    pre:({children})=><div className="code-block"><button className="copy" onClick={e=>{const text=e.currentTarget.parentElement?.querySelector('code')?.textContent; if (text) navigator.clipboard.writeText(text).catch(()=>{});}}>Copy</button><pre>{children}</pre></div>,
    img:()=> <span>[Response image]</span>
  }}>{normalizeMath(item.text || '')}</ReactMarkdown></article>;
  if (item.type === 'reasoning' || item.type === 'hookPrompt') return null;
  return <details className="action"><summary><span className={item.status === 'inProgress' ? 'activity running' : 'activity'}/> {item.type === 'commandExecution' ? item.command : item.type === 'fileChange' ? 'File changes' : item.type === 'contextCompaction' ? 'Compacting context' : item.tool ? `${item.server || ''} / ${item.tool}` : item.type}<small>{item.status}</small></summary>{item.aggregatedOutput && <pre>{item.aggregatedOutput}</pre>}{item.changes?.map(c=><div key={c.path}>{c.path}</div>)}</details>;
}
export function MessageList({items,threadId}:{items:Item[];threadId?:string}) {
  const visible=items.filter(i=>!['reasoning','hookPrompt','error','turnError'].includes(i.type));
  const list=useRef<VirtuosoHandle>(null),pending=useRef<string|null>(null);
  function reveal(){if(!pending.current)return;const index=visible.findIndex(i=>i.id===pending.current);if(index>=0){list.current?.scrollToIndex({index,align:'center',behavior:'smooth'});pending.current=null;}}
  useEffect(()=>{
    const listener=(e:Event)=>{const detail=(e as CustomEvent).detail;if(detail.type==='revealPrompt'&&detail.threadId===threadId){pending.current=detail.itemId;reveal();}};
    window.addEventListener('agent-state',listener);reveal();return()=>window.removeEventListener('agent-state',listener);
  },[items,threadId]);
  return <Virtuoso ref={list} className="message-list" data={visible} followOutput="smooth" initialTopMostItemIndex={Math.max(0,visible.length-1)} computeItemKey={(_,item)=>item.id} itemContent={(_,item)=><Message item={item}/>} />;
}
