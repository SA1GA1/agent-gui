// @vitest-environment jsdom
import {afterEach,expect,it} from 'vitest';
import {act,cleanup,render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {App} from './App';
afterEach(()=>{cleanup();delete window.agentHost;});
const snapshot={type:'state',connected:false,signedIn:false,status:'',version:'',binary:'',cwd:'',branch:'main',activeId:'',models:[],sessions:[],history:[],historyCursor:null,skills:[],quotaWindows:[{name:'Codex',minutes:300,remaining:50,resetsAt:2000000000},{name:'Codex',minutes:10080,remaining:30,resetsAt:2000100000}]};
it('opens the two real quota windows and reset dates from the quota button',async()=>{
 window.agentHost=()=>{};render(<App/>);
 act(()=>{window.dispatchEvent(new CustomEvent('agent-state',{detail:snapshot}));});
 await userEvent.click(screen.getByRole('button',{name:'5 ч: 50%'}));
 expect(screen.getByText('5 часов')).toBeTruthy();expect(screen.getByText('Неделя')).toBeTruthy();
 expect(screen.getByText('Codex: 30% осталось')).toBeTruthy();
 expect(screen.getAllByText(/Обновление:/)).toHaveLength(2);
});
it('unknown quota is displayed as unknown instead of an invented zero',()=>{
 window.agentHost=()=>{};render(<App/>);
 expect(screen.getByRole('button',{name:'5 ч: нет данных'})).toBeTruthy();
});
