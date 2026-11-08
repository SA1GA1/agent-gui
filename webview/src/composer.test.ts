import {describe, expect, it} from 'vitest';
import {enterAction} from './keyboard';
describe('message input', () => {
  const event = {key:'Enter',shiftKey:false,isComposing:false};
  it('sends on Enter, preserves Shift+Enter and IME', () => {
    expect(enterAction(event,false,false)).toBe('send');
    expect(enterAction({...event,shiftKey:true},false,false)).toBe('newline');
    expect(enterAction({...event,isComposing:true},false,false)).toBe('newline');
  });
  it('selects a menu result first and blocks duplicate send while busy', () => {
    expect(enterAction(event,true,false)).toBe('select');
    expect(enterAction(event,false,true)).toBe('ignore');
    expect(enterAction({...event,key:'a'},false,false)).toBe('ignore');
  });
});
