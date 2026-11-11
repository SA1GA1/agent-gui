export type Mention={kind:'skill'|'plugin'|'file';name:string;path:string;start:number;end:number};
export type Prompt={text:string;mentions:Mention[]};
export const mentionLabel=(m:Pick<Mention,'kind'|'name'>)=>(m.kind==='file'?'@':'/')+m.name.replace(/^[/@]/,'');
export function validMentions(text:string,mentions:readonly Mention[]):Mention[]{
 let last=0;return [...mentions].sort((a,b)=>a.start-b.start).filter(m=>{
  if(!['skill','plugin','file'].includes(m.kind)||!Number.isInteger(m.start)||!Number.isInteger(m.end)||m.start<last||m.start<0||m.end>text.length||m.end<=m.start||text.slice(m.start,m.end)!==mentionLabel(m))return false;
  last=m.end;return true;
 });
}
export function editPrompt(p:Prompt,start:number,end:number,replacement:string):Prompt&{caret:number}{
 start=Math.max(0,Math.min(start,p.text.length));end=Math.max(start,Math.min(end,p.text.length));
 const touched=p.mentions.filter(m=>start<m.end&&end>m.start || start===end&&start>m.start&&start<m.end);
 for(const m of touched){start=Math.min(start,m.start);end=Math.max(end,m.end);}
 const delta=replacement.length-(end-start);
 return {text:p.text.slice(0,start)+replacement+p.text.slice(end),mentions:p.mentions.filter(m=>!touched.includes(m)).map(m=>m.start>=end?{...m,start:m.start+delta,end:m.end+delta}:m),caret:start+replacement.length};
}
export function insertMention(p:Prompt,start:number,end:number,identity:Omit<Mention,'start'|'end'>):Prompt&{caret:number}{
 const label=mentionLabel(identity);const r=editPrompt(p,start,end,label);
 return {...r,mentions:[...r.mentions,{...identity,start:r.caret-label.length,end:r.caret}].sort((a,b)=>a.start-b.start)};
}
