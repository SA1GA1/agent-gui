// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
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
    expect((screen.getByRole('textbox') as HTMLTextAreaElement).value).toBe('/model ');
    await user.keyboard('{Enter}');expect(document.activeElement).toBe(screen.getByRole('combobox',{name:'Модель'}));
    expect(calls.filter(c=>c.type==='send')).toHaveLength(0);
  });
  it('warns only above 70 percent and disables compact during a task',()=>{
    const {rerender}=render(<Composer chat={{...chat,contextPercent:70,contextStale:false}} state={state} onHistory={()=>{}}/>);
    expect(screen.queryByText('/compact')).toBeNull();
    rerender(<Composer chat={{...chat,contextPercent:70.01,contextStale:false,state:'running'}} state={state} onHistory={()=>{}}/>);
    expect((screen.getByText('/compact') as HTMLButtonElement).disabled).toBe(true);
  });
});
