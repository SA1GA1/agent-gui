// @vitest-environment jsdom
import {afterEach,expect,it} from 'vitest';
import {act,cleanup,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {App} from './App';
afterEach(()=>{cleanup();delete window.agentHost;});
const chat={id:'a',title:'A chat',draft:'',open:true,model:'m',effort:'low',state:'idle',items:[{id:'u1',type:'userMessage',content:[{type:'text',text:'First prompt'}]}],files:[],contextPercent:50,contextStale:false,contextLevel:'warning',approvals:[]};
const snapshot={type:'state',connected:true,signedIn:true,status:'Ready',version:'',binary:'',cwd:'',branch:'main',activeId:'a',models:[],sessions:[chat],history:[{id:'other',name:'Other conversation'}],historyCursor:null,skills:[],quotaWindows:[{name:'Codex',minutes:300,remaining:50,resetsAt:2000000000},{name:'Codex',minutes:10080,remaining:30,resetsAt:2000100000}]};
function update(value:unknown){act(()=>{window.dispatchEvent(new CustomEvent('agent-state',{detail:value}));});}
it('keeps the disconnected bottom to one connect button',()=>{
 window.agentHost=()=>{};render(<App/>);
 expect(screen.getByRole('button',{name:'Connect Codex'})).toBeTruthy();
 expect(screen.queryByRole('textbox')).toBeNull();expect(screen.queryByRole('button',{name:/5h:/})).toBeNull();
});
it('anchors actual quota and prompt history to the connected composer',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);render(<App/>);update(snapshot);
 await userEvent.click(screen.getByRole('button',{name:'5h: 50%'}));
 expect(screen.getByText('Weekly')).toBeTruthy();expect(screen.getByText('Codex: 30% remaining')).toBeTruthy();expect(screen.getAllByText(/Resets:/)).toHaveLength(2);
 await userEvent.click(screen.getByRole('button',{name:'History'}));
 expect(screen.getByRole('button',{name:'First prompt'})).toBeTruthy();expect(screen.queryByText('Other conversation')).toBeNull();
 await userEvent.click(screen.getByRole('button',{name:'First prompt'}));
 expect(calls.some(c=>c.type==='revealPrompt' && c.itemId==='u1' && c.threadId==='a')).toBe(true);
});
it('uses strict context thresholds and exposes compact only in a top notice',()=>{
 window.agentHost=()=>{};const {container}=render(<App/>);update(snapshot);
 expect(container.querySelector('.context.warning')).toBeNull();expect(screen.queryByRole('button',{name:'Compact'})).toBeNull();
 update({...snapshot,sessions:[{...chat,contextPercent:50.1}]});expect(container.querySelector('.context.warning')).toBeTruthy();
 update({...snapshot,sessions:[{...chat,contextPercent:75}]});expect(container.querySelector('.context.critical')).toBeNull();
 update({...snapshot,sessions:[{...chat,contextPercent:75.1}]});expect(container.querySelector('.context.critical')).toBeTruthy();expect(screen.queryByRole('button',{name:'Compact'})).toBeNull();
});
it('unknown quota remains unknown',()=>{window.agentHost=()=>{};render(<App/>);update({...snapshot,quotaWindows:[]});expect(screen.getByRole('button',{name:'5h: Unknown'})).toBeTruthy();});
it('rejects history results from another chat or superseded popup',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);render(<App/>);update(snapshot);
 const user=userEvent.setup();await user.click(screen.getByRole('button',{name:'History'}));
 const first=calls.filter(c=>c.type==='promptHistory').at(-1)!;
 await user.click(screen.getByRole('button',{name:'History'}));await user.click(screen.getByRole('button',{name:'History'}));
 await user.type(screen.getByRole('textbox',{name:'Search prompts'}),'target');
 const latest=calls.filter(c=>c.type==='promptHistory').at(-1)!;
 update({type:'promptHistoryResult',threadId:'a',requestId:first.requestId,prompts:[{id:'old',text:'target stale'}]});
 expect(screen.queryByRole('button',{name:'target stale'})).toBeNull();
 update({type:'promptHistoryResult',threadId:'other',requestId:latest.requestId,prompts:[{id:'wrong',text:'target wrong chat'}]});
 expect(screen.queryByRole('button',{name:'target wrong chat'})).toBeNull();
 update({type:'promptHistoryResult',threadId:'a',requestId:latest.requestId,prompts:[{id:'right',text:'target result'}]});
 expect(screen.getByRole('button',{name:'target result'})).toBeTruthy();
 update({...snapshot,activeId:'b',sessions:[chat,{...chat,id:'b',title:'B chat',items:[]}]});
 expect(screen.queryByRole('textbox',{name:'Search prompts'})).toBeNull();
});
it('keeps /resume conversation selection separate from prompt history',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);render(<App/>);update(snapshot);
 const user=userEvent.setup();await user.type(screen.getByRole('textbox',{name:'Message Codex'}),'/resume{Enter}');
 expect(calls.some(c=>c.type==='history'&&c.query==='')).toBe(true);expect(calls.some(c=>c.type==='promptHistory')).toBe(false);
 update({type:'historyResult',entries:[{id:'old-chat',name:'Prior work',preview:'Previous conversation'}],cursor:'page2'});
 expect(screen.getByRole('dialog',{name:'Resume a conversation'})).toBeTruthy();
 await user.click(screen.getByRole('button',{name:'Load more conversations'}));expect(calls.at(-1)).toEqual({type:'history',query:'',cursor:'page2'});
 update({type:'historyResult',entries:[{id:'second-chat',name:'Second work',preview:''}],cursor:null});
 expect(screen.getByRole('button',{name:'Prior work'})).toBeTruthy();
 await user.click(screen.getByRole('button',{name:'Second work'}));expect(calls.at(-1)).toEqual({type:'resume',threadId:'second-chat'});
 expect(screen.queryByRole('dialog',{name:'Resume a conversation'})).toBeNull();
 await user.click(screen.getByRole('button',{name:'History'}));expect(screen.getByRole('button',{name:'First prompt'})).toBeTruthy();
});
it('uses only dismissible server context notices',()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);render(<App/>);
 update({...snapshot,sessions:[{...chat,contextPercent:90}]});expect(screen.queryByRole('button',{name:'Compact'})).toBeNull();
 update({...snapshot,sessions:[{...chat,contextPercent:90}],notices:[{id:'ctx',threadId:'a',text:'Context is almost full',level:'critical',action:'compact'}]});
 expect(screen.getAllByRole('button',{name:'Compact'})).toHaveLength(1);
});
it('loads prompt history once and filters subsequent search locally',async()=>{
 const calls:Record<string,unknown>[]=[];window.agentHost=m=>calls.push(m);render(<App/>);update(snapshot);
 const user=userEvent.setup();await user.click(screen.getByRole('button',{name:'History'}));await user.type(screen.getByRole('textbox',{name:'Search prompts'}),'first');
 expect(calls.filter(c=>c.type==='promptHistory')).toHaveLength(1);expect(screen.getByRole('button',{name:'First prompt'})).toBeTruthy();
});
