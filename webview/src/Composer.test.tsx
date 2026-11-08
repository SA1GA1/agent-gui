// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {act,cleanup,fireEvent,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Composer} from './Composer';
import type {Chat,State} from './types';
const chat:Chat={id:'a',title:'Test',draft:'',open:true,model:'m',effort:'low',state:'idle',items:[],files:[],contextPercent:null,contextLevel:'unknown',contextStale:true,approvals:[]};
const state:State={type:'state',connected:true,signedIn:true,status:'Готов',version:'',binary:'',cwd:'',branch:'main',activeId:'a',sessions:[chat],models:[{model:'m',displayName:'Model',defaultReasoningEffort:'low',supportedReasoningEfforts:[{reasoningEffort:'low',description:''}]}],history:[],historyCursor:null,skills:[]};
afterEach(()=>{cleanup();delete window.agentHost;});
describe('Composer',()=>{
  it('sends multiline text exactly once on two rapid Enter presses',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={chat} state={state} onHistory={()=>{}}/>);
    const user=userEvent.setup();await user.click(screen.getByRole('textbox'));
    await user.type(screen.getByRole('textbox'),'Первая{Shift>}{Enter}{/Shift}Вторая{Enter}{Enter}');
    const sent=calls.filter(c=>c.type==='send');expect(sent).toHaveLength(1);expect(sent[0].text).toBe('Первая\nВторая');
  });
  it('selects slash menu before sending and routes model locally',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={chat} state={state} onHistory={()=>{}}/>);
    const user=userEvent.setup();await user.type(screen.getByRole('textbox'),'/model{Enter}');
    expect(calls.filter(c=>c.type==='send')).toHaveLength(0);
    expect(screen.getByRole('dialog',{name:'Models'})).toBeTruthy();
    expect(calls.filter(c=>c.type==='send')).toHaveLength(0);
  });
  it('offers enabled skills through slash and installed plugins through at',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={chat} state={{...state,skills:[{name:'review',description:'Review code',path:'/review',enabled:true}],plugins:[{name:'GitHub',id:'github',path:'/github',description:'Repositories',enabled:true},{name:'Hidden',id:'hidden',path:'/hidden',description:'',enabled:false}]}} onHistory={()=>{}}/>);
    const user=userEvent.setup();const input=screen.getByRole('textbox');await user.type(input,'/rev');
    expect(screen.getByText('Skills')).toBeTruthy();await user.keyboard('{Enter}');await user.type(input,'Check this{Enter}');
    expect(calls.find(c=>c.type==='send')?.skill).toBe('review');
    await user.clear(input);await user.type(input,'@');expect(screen.getByText('Plugins')).toBeTruthy();expect(screen.getByText('GitHub')).toBeTruthy();expect(screen.queryByText('Hidden')).toBeNull();
  });
  it('sends a selected installed plugin identity and keeps files in the composer',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={{...chat,files:[{id:'f',name:'notes.txt',image:false}]}} state={{...state,plugins:[{name:'GitHub',id:'github',path:'/github',description:'Repositories',enabled:true}]}} onHistory={()=>{}}/>);
    const user=userEvent.setup();await user.type(screen.getByRole('textbox'),'@Git{Enter}Look at this{Enter}');
    expect(calls.find(c=>c.type==='send')?.plugin).toEqual({name:'GitHub',path:'/github'});
    expect(screen.getByText('notes.txt')).toBeTruthy();
    await user.click(screen.getByRole('button',{name:'Remove notes.txt'}));expect(calls.at(-1)).toEqual({type:'removeAttachment',threadId:'a',id:'f'});
  });
  it('ignores stale file results and dismisses autocomplete with Escape',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={chat} state={state} onHistory={()=>{}}/>);
    const user=userEvent.setup();const input=screen.getByRole('textbox');await user.type(input,'@src');
    await new Promise(resolve=>setTimeout(resolve,210));
    const id=calls.find(c=>c.type==='searchFiles')?.requestId;expect(id).toBeTruthy();
    act(()=>window.dispatchEvent(new CustomEvent('agent-state',{detail:{type:'files',requestId:'old',files:['wrong.ts']}})));
    expect(screen.queryByText('wrong.ts')).toBeNull();
    act(()=>window.dispatchEvent(new CustomEvent('agent-state',{detail:{type:'files',requestId:id,files:['src/main.ts']}})));
    expect(screen.getByRole('option')).toBeTruthy();await user.keyboard('{Escape}');expect(screen.queryByRole('listbox')).toBeNull();
  });
  it('blocks Enter while running and exposes Stop instead of Send',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={{...chat,state:'running'}} state={state} onHistory={()=>{}}/>);
    const user=userEvent.setup();await user.type(screen.getByRole('textbox'),'Continue{Enter}');
    expect(calls.some(c=>c.type==='send')).toBe(false);expect(screen.queryByRole('button',{name:'Send'})).toBeNull();
    await user.click(screen.getByRole('button',{name:'Stop'}));expect(calls.at(-1)).toEqual({type:'stop',threadId:'a'});
  });
  it('keeps IME Enter in the input',()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={{...chat,draft:'hello'}} state={state} onHistory={()=>{}}/>);
    fireEvent.keyDown(screen.getByRole('textbox'),{key:'Enter',keyCode:229,isComposing:true});
    expect(calls.some(c=>c.type==='send')).toBe(false);
  });
});

it('keeps model and reasoning behind one composer button',async()=>{
 window.agentHost=()=>{};render(<Composer chat={chat} state={state} onHistory={()=>{}}/>);
 expect(screen.queryByRole('slider',{name:'Reasoning effort'})).toBeNull();
 await userEvent.click(screen.getByRole('button',{name:'Model'}));
 expect(screen.getByRole('slider',{name:'Reasoning effort'})).toBeTruthy();
});
