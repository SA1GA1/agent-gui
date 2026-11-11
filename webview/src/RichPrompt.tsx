import {forwardRef,useLayoutEffect,useRef,useState,type KeyboardEvent,type ClipboardEvent} from 'react';
import {fenceRanges,fenceEnter} from './promptFences';
import {editPrompt,mentionLabel,validMentions,type Mention,type Prompt} from './mentions';

type Props={text:string;caret:number;mentions:Mention[];disabled:boolean;onEdit:(p:Prompt,caret:number)=>void;onKeyDown:(e:KeyboardEvent<HTMLDivElement>)=>void;onPasteFiles:(e:ClipboardEvent<HTMLDivElement>)=>boolean;onCaret:(caret:number)=>void;onScroll:(field:HTMLDivElement)=>void};
function promptText(node:Node):string {
 let text='';function scan(n:Node){if(n.nodeType===Node.TEXT_NODE){text+=n.textContent||'';return;}if(n instanceof HTMLElement){if(n.tagName==='BR'){text+='\n';return;}if((n.tagName==='DIV'||n.tagName==='P')&&text&&!text.endsWith('\n'))text+='\n';}n.childNodes.forEach(scan);}node.childNodes.forEach(scan);return text;
}
export function promptSelection(root:HTMLElement):[number,number]{
 const selection=window.getSelection();if(!selection?.rangeCount)return [root.textContent?.length||0,root.textContent?.length||0];
 const range=selection.getRangeAt(0);if(!root.contains(range.startContainer)||!root.contains(range.endContainer))return [root.textContent?.length||0,root.textContent?.length||0];
 const before=range.cloneRange();before.selectNodeContents(root);before.setEnd(range.startContainer,range.startOffset);const start=promptText(before.cloneContents()).length;
 const through=range.cloneRange();through.selectNodeContents(root);through.setEnd(range.endContainer,range.endOffset);return [start,promptText(through.cloneContents()).length];
}
export function setPromptCaret(root:HTMLElement,offset:number){
 const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node=walker.nextNode();let left=offset;const range=document.createRange();
 while(node){const length=node.textContent?.length||0;if(left<=length){const token=node.parentElement?.closest('[data-mention]');if(token&&left>0)range.setStartAfter(token);else range.setStart(node,left);range.collapse(true);const s=window.getSelection();s?.removeAllRanges();s?.addRange(range);return;}left-=length;node=walker.nextNode();}
 range.selectNodeContents(root);range.collapse(false);const s=window.getSelection();s?.removeAllRanges();s?.addRange(range);
}
export const RichPrompt=forwardRef<HTMLDivElement,Props>(function RichPrompt({text,caret,mentions,disabled,onEdit,onKeyDown,onPasteFiles,onCaret,onScroll},ref){
 const local=useRef<HTMLDivElement|null>(null),pendingCaret=useRef<number|undefined>(undefined);
 const history=useRef<Prompt[]>([]),redo=useRef<Prompt[]>([]),previous=useRef<Prompt>({text,mentions});
 const composing=useRef(false);const [compositionEpoch,setCompositionEpoch]=useState(0);
 const signature=JSON.stringify(mentions);
 useLayoutEffect(()=>{
  const root=local.current;if(!root||composing.current)return;
  if(previous.current.text!==text||JSON.stringify(previous.current.mentions)!==signature){if(document.activeElement===root){history.current.push(previous.current);if(history.current.length>100)history.current.shift();}else{history.current=[];redo.current=[];}previous.current={text,mentions};}
  const existing=Array.from(root.querySelectorAll('[data-mention]')).map(n=>n.getAttribute('data-mention')).join('|');
  const desired=mentions.map(m=>JSON.stringify(m)).join('|');
  const fences=JSON.stringify(fenceRanges(text));
  if(root.textContent!==text||existing!==desired||root.dataset.fences!==fences){
   root.dataset.fences=fences;
   function append(value:string){let offset=0;for(const range of fenceRanges(value)){root!.append(document.createTextNode(value.slice(offset,range.start)));const code=document.createElement('span');code.className='prompt-code';code.textContent=value.slice(range.start,range.end);root!.append(code);offset=range.end;}root!.append(document.createTextNode(value.slice(offset)));}
   const focused=document.activeElement===root;const targetCaret=pendingCaret.current??caret;
   root.replaceChildren();let position=0;for(const m of validMentions(text,mentions)){
    append(text.slice(position,m.start));const span=document.createElement('span');span.setAttribute('contenteditable','false');span.className='inline-mention';span.dataset.mention=JSON.stringify(m);span.textContent=mentionLabel(m);span.title=m.path;root.append(span);position=m.end;
   }append(text.slice(position));if(focused)setPromptCaret(root,targetCaret);
  }
  if(pendingCaret.current!==undefined){setPromptCaret(root,pendingCaret.current);pendingCaret.current=undefined;}
 },[text,signature,caret,compositionEpoch]);
 function commit(p:Prompt,caret:number){pendingCaret.current=caret;redo.current=[];onEdit(p,caret);}
 function read(){redo.current=[];const root=local.current!;let value='';const found:Mention[]=[];
  function scan(n:Node){if(n.nodeType===Node.TEXT_NODE){value+=n.textContent||'';return;}if(!(n instanceof HTMLElement))return;if(n.tagName==='BR'){value+='\n';return;}
   const raw=n.dataset.mention;if(raw){try{const identity=JSON.parse(raw) as Mention;const label=mentionLabel(identity);if(n.textContent===label){const start=value.length;value+=label;found.push({...identity,start,end:value.length});return;}}catch{}}
   if((n.tagName==='DIV'||n.tagName==='P')&&value&&!value.endsWith('\n'))value+='\n';n.childNodes.forEach(scan);
  }root.childNodes.forEach(scan);if(!value)root.replaceChildren();onEdit({text:value,mentions:validMentions(value,found)},promptSelection(root)[1]);
 }
 return <div ref={node=>{local.current=node;if(typeof ref==='function')ref(node);else if(ref)ref.current=node;}} role="textbox" aria-label="Message Codex" aria-multiline="true" aria-disabled={disabled} contentEditable={!disabled} suppressContentEditableWarning className="rich-prompt" data-placeholder="/ commands, skills and plugins · @ files" onCompositionStart={()=>{composing.current=true;}} onCompositionEnd={()=>{composing.current=false;read();setCompositionEpoch(v=>v+1);}} onInput={read} onKeyUp={()=>onCaret(promptSelection(local.current!)[1])} onMouseUp={()=>onCaret(promptSelection(local.current!)[1])} onScroll={e=>onScroll(e.currentTarget)} onPaste={e=>{
  if(onPasteFiles(e))return;e.preventDefault();const [start,end]=promptSelection(local.current!);const result=editPrompt({text,mentions},start,end,e.clipboardData.getData('text/plain'));commit(result,result.caret);
 }} onKeyDown={e=>{
  if((e.metaKey||e.ctrlKey)&&!e.nativeEvent.isComposing&&e.key.toLowerCase()==='z'){
   e.preventDefault();
   const stack=e.shiftKey?redo.current:history.current;const p=stack.pop();if(p){e.preventDefault();(e.shiftKey?history.current:redo.current).push({text,mentions});previous.current=p;pendingCaret.current=p.text.length;onEdit(p,p.text.length);}return;
  }
  if(!e.nativeEvent.isComposing&&(e.key==='Backspace'||e.key==='Delete')){
   const [start,end]=promptSelection(local.current!);const previous=mentions.find(m=>e.key==='Backspace'&&start===end&&m.end===start-1&&text[start-1]===' ');if(previous){e.preventDefault();const r=editPrompt({text,mentions},previous.start,end,'');commit(r,r.caret);return;}const a=start===end?(e.key==='Backspace'?Math.max(0,start-1):start):start;const b=start===end?(e.key==='Delete'?Math.min(text.length,end+1):end):end;
   if(mentions.some(m=>a<m.end&&b>m.start)){e.preventDefault();const r=editPrompt({text,mentions},a,b,'');commit(r,r.caret);return;}
  }
  if(e.key==='Enter'&&e.shiftKey&&!e.nativeEvent.isComposing){e.preventDefault();const [start,end]=promptSelection(local.current!);const r=editPrompt({text,mentions},start,end,'\n');commit(r,r.caret);return;}
  if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing){const [start,end]=promptSelection(local.current!);const addition=fenceEnter(text,start);if(addition!==undefined){e.preventDefault();const r=editPrompt({text,mentions},start,end,addition);commit(r,start+1);return;}}
  onKeyDown(e);
 }}/>
});
