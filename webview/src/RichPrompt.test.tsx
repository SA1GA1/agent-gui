// @vitest-environment jsdom
import {expect,it} from 'vitest';
import {promptSelection} from './RichPrompt';
it('counts a browser line break when measuring the caret',()=>{
 const root=document.createElement('div');root.innerHTML='a<br>b';document.body.append(root);
 const range=document.createRange();range.setStart(root.lastChild!,1);range.collapse(true);window.getSelection()?.removeAllRanges();window.getSelection()?.addRange(range);
 expect(promptSelection(root)).toEqual([3,3]);root.remove();
});
