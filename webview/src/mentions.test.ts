import {expect,it} from 'vitest';
import {editPrompt,insertMention,validMentions} from './mentions';
it('inserts an inline identity without losing text around the active token',()=>{
 const result=insertMention({text:'Please /Git now',mentions:[]},7,11,{kind:'plugin',name:'GitHub',path:'/github'});
 expect(result.text).toBe('Please /GitHub now');
 expect(result.mentions).toEqual([{kind:'plugin',name:'GitHub',path:'/github',start:7,end:14}]);
});
it('deleting any part of a mention removes its whole identity and shifts later ranges',()=>{
 const p={text:'/GitHub /review rest',mentions:[{kind:'plugin' as const,name:'GitHub',path:'/g',start:0,end:7},{kind:'skill' as const,name:'review',path:'/r',start:8,end:15}]};
 const r=editPrompt(p,6,7,'');expect(r.text).toBe(' /review rest');expect(r.mentions[0].start).toBe(1);expect(r.mentions[0].kind).toBe('skill');
});
it('rejects stale identity ranges and overlapping or forged labels',()=>{
 expect(validMentions('unrelated',[{kind:'plugin',name:'GitHub',path:'/g',start:0,end:7}])).toEqual([]);
});
