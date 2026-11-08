export function enterAction(e: {key:string;shiftKey:boolean;isComposing:boolean}, menuOpen:boolean, busy:boolean) {
  if (e.key !== 'Enter') return 'ignore';
  if (e.shiftKey || e.isComposing) return 'newline';
  if (menuOpen) return 'select';
  return busy ? 'ignore' : 'send';
}
