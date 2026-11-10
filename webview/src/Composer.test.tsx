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
  it('shows document formats and image thumbnails with independent removal',async()=>{
    const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
    render(<Composer chat={{...chat,files:[{id:'doc',name:'long-notes.md',image:false},{id:'photo',name:'photo.png',image:true,preview:'data:image/png;base64,iVBORw0KGgo='}]}} state={state} onHistory={()=>{}}/>);
    expect(screen.getByText('MD')).toBeTruthy();expect(screen.getByRole('img',{name:'photo.png'})).toBeTruthy();
    await userEvent.setup().click(screen.getByRole('button',{name:'Remove photo.png'}));
    expect(calls.at(-1)).toEqual({type:'removeAttachment',threadId:'a',id:'photo'});
    expect(screen.getByText('long-notes.md')).toBeTruthy();
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

it('keeps options visible in the shared composer and restores ordinary draft after custom send',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
 const pending:Chat={...chat,state:'running',draft:'Keep this ordinary draft',approvals:[{id:42,method:'item/commandExecution/requestApproval',params:{threadId:'a',command:'./gradlew test'}}]};
 const {rerender}=render(<Composer chat={pending} state={state} onHistory={()=>{}}/>);
 const user=userEvent.setup();await user.click(screen.getByRole('button',{name:/3\. Decline or write a reply/}));
 const input=screen.getByRole('textbox');expect((input as HTMLTextAreaElement).value).toBe('');
 await user.type(input,'Review only');
 expect(screen.getByRole('button',{name:/1\. Allow once/})).toBeTruthy();
 expect(calls.filter(c=>c.type==='draft')).toHaveLength(0);
 await user.keyboard('{Escape}');expect((input as HTMLTextAreaElement).value).toBe('Keep this ordinary draft');
 await user.click(screen.getByRole('button',{name:/3\. Decline or write a reply/}));expect((input as HTMLTextAreaElement).value).toBe('Review only');
 await user.click(screen.getByRole('button',{name:'Send'}));expect(calls.at(-1)).toEqual({type:'approve',threadId:'a',requestId:'42',decision:'cancel',text:'Review only'});
 rerender(<Composer chat={{...pending,approvals:[],state:'running'}} state={state} onHistory={()=>{}}/>);
 expect((input as HTMLTextAreaElement).value).toBe('Keep this ordinary draft');
});

it('moves approval selection with arrows without consenting until Enter',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
 render(<Composer chat={{...chat,state:'running',approvals:[{id:9,method:'item/fileChange/requestApproval',params:{threadId:'a',reason:'Apply changes?'}}]}} state={state} onHistory={()=>{}}/>);
 const first=screen.getByRole('button',{name:/1\. Allow once/});first.focus();
 fireEvent.keyDown(first,{key:'ArrowDown'});expect(calls.some(c=>c.type==='approve')).toBe(false);
 fireEvent.keyDown(document.activeElement!,{key:'Enter'});
 expect(calls.at(-1)).toMatchObject({type:'approve',requestId:'9',decision:'acceptForSession'});
});

it('restores the custom reply rather than the ordinary draft when returning to a chat',async()=>{
 window.agentHost=()=>{};
 const a:Chat={...chat,state:'running',draft:'ordinary A',approvals:[{id:42,method:'item/commandExecution/requestApproval',params:{threadId:'a'}}]};
 const b:Chat={...chat,id:'b',draft:'ordinary B'};
 const {rerender}=render(<Composer chat={a} state={state} onHistory={()=>{}}/>);
 const user=userEvent.setup();await user.click(screen.getByRole('button',{name:/3\. Decline or write a reply/}));await user.type(screen.getByRole('textbox'),'custom A');
 rerender(<Composer chat={b} state={state} onHistory={()=>{}}/>);expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('ordinary B');
 rerender(<Composer chat={a} state={state} onHistory={()=>{}}/>);expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('custom A');
});

it('keeps model picker inside the composer toolbar and uses a slash placeholder',()=>{
 window.agentHost=()=>{};render(<Composer chat={chat} state={state} onHistory={()=>{}}/>);
 const field=screen.getByRole('textbox');expect(field.getAttribute('placeholder')).toMatch(/^\//);expect(field.getAttribute('title')).toBeNull();
 expect(screen.getByRole('button',{name:'Model'}).closest('.composer-toolbar')).not.toBeNull();
});

it('saves reasoning changes made without a pointer or a specific key event',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
 const models=[{...state.models[0],supportedReasoningEfforts:[{reasoningEffort:'low',description:''},{reasoningEffort:'high',description:''}]}];
 render(<Composer chat={chat} state={{...state,models}} onHistory={()=>{}}/>);
 await userEvent.click(screen.getByRole('button',{name:'Model'}));
 fireEvent.change(screen.getByRole('slider',{name:'Reasoning effort'}),{target:{value:'1'}});
 expect(calls.at(-1)).toEqual({type:'model',threadId:'a',model:'m',effort:'high'});
});

it('keeps the model picker open when choosing a model',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);
 render(<Composer chat={chat} state={{...state,models:[...state.models,{...state.models[0],model:'other',displayName:'Other model'}]}} onHistory={()=>{}}/>);
 await userEvent.click(screen.getByRole('button',{name:'Model'}));await userEvent.click(screen.getByRole('button',{name:'Other model'}));
 expect(screen.getByRole('dialog',{name:'Models'})).toBeTruthy();expect(calls.at(-1)).toEqual({type:'model',threadId:'a',model:'other'});
});

it('shows compact prefixed skill and plugin chips with independent close actions',async()=>{
 window.agentHost=()=>{};
 render(<Composer chat={chat} state={{...state,skills:[{name:'review',description:'Review',path:'/review',enabled:true}],plugins:[{name:'GitHub',id:'github',path:'/github',description:'Repositories',enabled:true}]}} onHistory={()=>{}}/>);
 const user=userEvent.setup();await user.type(screen.getByRole('textbox'),'/rev{Enter}@Git{Enter}');
 expect(screen.getByText('/review').closest('.context-chip')).not.toBeNull();expect(screen.getByText('@GitHub').closest('.context-chip')).not.toBeNull();
 await user.click(screen.getByRole('button',{name:'Remove skill'}));expect(screen.queryByText('/review')).toBeNull();expect(screen.getByText('@GitHub')).toBeTruthy();
});
