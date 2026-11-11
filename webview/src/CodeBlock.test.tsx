// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {act,cleanup,fireEvent,render,screen} from '@testing-library/react';
import {CodeBlock,UserMessage} from './MessageList';
afterEach(()=>{cleanup();vi.useRealTimers();vi.unstubAllGlobals();});
it('confirms a successful copy then restores the button',async()=>{
  vi.useFakeTimers();
  const writeText=vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal('navigator',{clipboard:{writeText}});
  render(<CodeBlock><code>val answer = 42</code></CodeBlock>);
  await act(async()=>fireEvent.click(screen.getByRole('button',{name:'Copy code'})));
  expect(writeText).toHaveBeenCalledWith('val answer = 42');
  expect(screen.getByRole('button',{name:'Copied'})).toBeTruthy();
  act(()=>vi.advanceTimersByTime(1600));
  expect(screen.getByRole('button',{name:'Copy code'})).toBeTruthy();
});

it('user message exposes timestamp and an icon-only copy control',async()=>{
  const writeText=vi.fn().mockResolvedValue(undefined);vi.stubGlobal('navigator',{clipboard:{writeText}});
  const {container}=render(<UserMessage item={{id:'u',type:'userMessage',sentAt:1700000000000,content:[{type:'text',text:'Keep my prompt'}]}}/>);
  expect(container.querySelector('time')?.dateTime).toBe('2023-11-14T22:13:20.000Z');
  const button=screen.getByRole('button',{name:'Copy message'});expect(button.textContent).toBe('');
  await act(async()=>fireEvent.click(button));expect(writeText).toHaveBeenCalledWith('Keep my prompt');
  expect(screen.getByRole('button',{name:'Copied'})).toBeTruthy();
});

it('keeps normal prompts beginning with Attached file and legacy image placeholders',()=>{
 const {rerender}=render(<UserMessage item={{id:'u',type:'userMessage',content:[{type:'text',text:'Attached file: please review report.pdf'}]}}/>);
 expect(screen.getByText('Attached file: please review report.pdf')).toBeTruthy();
 rerender(<UserMessage item={{id:'u',type:'userMessage',content:[{type:'localImage',path:'/tmp/legacy.png'}]}}/>);
 expect(screen.getByText('▧ Image')).toBeTruthy();
});
