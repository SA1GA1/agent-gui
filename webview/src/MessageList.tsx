import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import 'katex/dist/katex.min.css';
import {normalizeMath} from './mathNormalizer';
import {useEffect,useLayoutEffect,useRef,useState,type ReactNode} from 'react';
import {Virtuoso,type VirtuosoHandle} from 'react-virtuoso';
import {host, type Item} from './types';
function CopyButton({text,label,className}:{text:()=>string|undefined;label:string;className?:string}) {
  const [copied,setCopied]=useState(false),timer=useRef<ReturnType<typeof setTimeout>|undefined>(undefined),alive=useRef(true);
  useEffect(()=>{alive.current=true;return()=>{alive.current=false;clearTimeout(timer.current);};},[]);
  async function copy(){
    const value=text();if(value==null)return;
    try {
      await navigator.clipboard.writeText(value);
      if(!alive.current)return;
      setCopied(true);clearTimeout(timer.current);timer.current=setTimeout(()=>setCopied(false),1600);
    } catch { /* Keep copying available when clipboard access is denied. */ }
  }
  return <button className={className} onMouseDown={e=>e.preventDefault()} onClick={copy} aria-label={copied?'Copied':label} title={copied?'Copied':label} aria-live="polite"><svg aria-hidden="true" width="16" height="16" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">{copied?<path d="m4 10 4 4 8-9"/>:<><path d="M7 5V4a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-1"/><rect x="2" y="6" width="12" height="12" rx="2"/></>}</svg></button>;
}
export function CodeBlock({children}:{children?:ReactNode}) {
  const block=useRef<HTMLDivElement>(null);
  return <div className="code-block" ref={block}><CopyButton className="copy" label="Copy code" text={()=>block.current?.querySelector('code')?.textContent ?? undefined}/><pre>{children}</pre></div>;
}
export function UserMessage({item}:{item:Item}) {
  const text=item.content?.filter(c=>!(item.sentFiles?.length&&c.type==='text'&&c.text?.startsWith('Attached file: ')&&c.text.includes('\nImmutable local snapshot: '))).map(c=>c.type==='text'?c.text:item.sentFiles?.length?'':c.type==='localImage'||c.type==='image'?'▧ Image':'[Attachment]').filter(Boolean).join('\n') ?? '';
  const [expanded,setExpanded]=useState(false),[long,setLong]=useState(false),body=useRef<HTMLDivElement>(null);
  useLayoutEffect(()=>{const element=body.current;if(!element)return;function measure(){const line=Number.parseFloat(getComputedStyle(element!).lineHeight)||21;setLong(element!.scrollHeight>line*14+2);}measure();const observer=typeof ResizeObserver!=='undefined'?new ResizeObserver(measure):undefined;observer?.observe(element);return()=>observer?.disconnect();},[text]);
  return <article className="message user">{!!item.sentFiles?.length&&<div className="sent-attachments">{item.sentFiles.map(f=><div className="sent-attachment" key={f.id}>{f.preview?<img src={f.preview} alt={f.name}/>:<><span aria-hidden="true">▤</span><span>{f.name.includes('.')?f.name.slice(0,f.name.lastIndexOf('.')):f.name}<small>{f.name.split('.').at(-1)?.toUpperCase()}</small></span></>}</div>)}</div>}<div className="user-bubble"><div ref={body} className={`user-text${expanded?' expanded':''}`}><ReactMarkdown remarkPlugins={[remarkGfm]}>{text}</ReactMarkdown></div>{long&&<button className="show-more" onClick={()=>setExpanded(!expanded)}>{expanded?'Show less ⌃':'Show more ⌄'}</button>}</div><div className="message-actions">{item.sentAt&&<time dateTime={new Date(item.sentAt).toISOString()}>{new Date(item.sentAt).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit',hour12:false})}</time>}<CopyButton label="Copy message" text={()=>text}/></div></article>;
}
function Message({item}:{item:Item}) {
  if (item.type === 'userMessage') return <UserMessage item={item}/>;
  if (item.type === 'agentMessage' || item.type === 'plan') return <article className="message assistant"><div className="role">{item.type === 'plan' ? 'Plan' : 'Codex'}</div><ReactMarkdown remarkPlugins={[remarkGfm,remarkMath]} rehypePlugins={[[rehypeKatex,{trust:false,strict:'ignore',throwOnError:false,maxExpand:200,maxSize:20}]]} components={{
    a:({href,children})=><a href={href} onClick={e=>{e.preventDefault(); if (href) host('openUrl',{url:href});}}>{children}</a>,
    pre:CodeBlock,
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
