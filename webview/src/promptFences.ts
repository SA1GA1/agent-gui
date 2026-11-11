export function fenceRanges(text:string):{start:number;end:number;closed:boolean}[]{
 const ranges:{start:number;end:number;closed:boolean}[]=[];let start:number|undefined,marker='';
 for(const match of text.matchAll(/^ {0,3}(`{3,}|~{3,})([^\n]*)$/gm)){
  if(start===undefined){start=match.index;marker=match[1];}
  else if(match[1][0]===marker[0]&&match[1].length>=marker.length&&!match[2].trim()){ranges.push({start,end:match.index+match[0].length,closed:true});start=undefined;}
 }
 if(start!==undefined)ranges.push({start,end:text.length,closed:false});return ranges;
}
export function fenceEnter(text:string,caret:number):string|undefined{
 const line=text.slice(0,caret).split('\n').at(-1) ?? '',start=caret-line.length;
 const ranges=fenceRanges(text);
 if(/^ {0,3}`{3,}[A-Za-z0-9_+-]*$/.test(line)&&ranges.some(r=>r.start===start))return ranges.find(r=>r.start===start)?.closed?'\n':'\n\n'+line.trim().match(/^`+/)![0];
 if(ranges.some(r=>caret>r.start&&(caret<r.end||!r.closed&&caret<=r.end)&&text.slice(r.start,caret).includes('\n')))return '\n';
}
