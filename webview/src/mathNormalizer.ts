/** Normalize model LaTeX delimiters before Markdown consumes their backslashes.
 * Code is copied verbatim; currency dollars are escaped for remark-math.
 */
export function normalizeMath(text: string): string {
  let out='',i=0;
  while(i<text.length) {
    if(i===0 || text[i-1]==='\n') {
      const marker=text.slice(i).match(/^ {0,3}(`{3,}|~{3,})/);
      if(marker) {
        const run=marker[1],openingEnd=text.indexOf('\n',i),start=openingEnd<0?text.length:openingEnd+1;
        const close=new RegExp('^ {0,3}'+run[0]+'{'+run.length+',}[ \\t]*(?:\\n|$)','gm');
        close.lastIndex=start;
        const match=close.exec(text),end=match?match.index+match[0].length:text.length;
        out+=text.slice(i,end);i=end;continue;
      }
    }
    if(text[i]==='`') {
      const ticks=text.slice(i).match(/^`+/)![0],end=text.indexOf(ticks,i+ticks.length);
      if(end>=0) {out+=text.slice(i,end+ticks.length);i=end+ticks.length;continue;}
    }
    if(text.startsWith('\\[',i) || text.startsWith('\\(',i)) {
      const display=text[i+1]==='[',close=display?'\\]':'\\)',end=text.indexOf(close,i+2);
      if(end>=0) {const source=text.slice(i+2,end);out+=display?'\n\n$$\n'+source+'\n$$\n\n':'$'+source+'$';i=end+2;continue;}
    }
    if(text[i]==='\\') {out+=text.slice(i,i+2);i+=2;continue;}
    if(text[i]==='$' && text[i+1]!=='$' && text[i-1]!=='$') {
      const end=text.indexOf('$',i+1);
      if(end>i+1 && !/\s/.test(text[i+1]) && !/\s/.test(text[end-1]) && !/\d/.test(text[end+1]||'')) {
        out+=text.slice(i,end+1);i=end+1;continue;
      }
      out+='\\$';i++;continue;
    }
    out+=text[i++];
  }
  return out;
}
