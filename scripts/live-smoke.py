#!/usr/bin/env python3
"""Opt-in live inference in an empty temporary directory. No project files sent."""
import json, os, queue, shutil, subprocess, tempfile, threading, time, struct, zlib
binary = os.environ.get('CODEX_BINARY') or shutil.which('codex')
assert binary
with tempfile.TemporaryDirectory(prefix='agent-gui-smoke-') as cwd:
    p=subprocess.Popen([binary,'app-server','--listen','stdio://'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    inbox=queue.Queue(); events=[]; seq=0
    def read():
        for line in p.stdout:
            inbox.put(json.loads(line))
        inbox.put(None)
    threading.Thread(target=read,daemon=True).start()
    def rpc(method,params):
        global seq
        seq+=1;p.stdin.write(json.dumps(dict(id=seq,method=method,params=params))+'\n');p.stdin.flush()
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            m=inbox.get(timeout=max(0.1,deadline-time.monotonic()))
            assert m is not None,'process exited'
            if m.get('id')==seq:
                assert 'error' not in m, m.get('error')
                return m['result']
            events.append(m)
        raise TimeoutError(method)
    def completion(thread):
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            for i,m in enumerate(events):
                if m.get('method')=='turn/completed' and m.get('params',{}).get('threadId')==thread:
                    return events.pop(i)['params']['turn']
            m=inbox.get(timeout=max(.1,deadline-time.monotonic()))
            assert m is not None
            events.append(m)
        raise TimeoutError('turn/completed')
    try:
        rpc('initialize',{'clientInfo':{'name':'agent_gui_live_smoke','version':'0.1.0'}})
        p.stdin.write('{"method":"initialized"}\n');p.stdin.flush()
        catalog=rpc('model/list',{})['data']
        model=next((m for m in catalog if m.get('isDefault')),catalog[0])['model']
        thread=rpc('thread/start',{'cwd':cwd,'ephemeral':True,'model':model,'modelProvider':'openai','sandbox':'read-only','approvalPolicy':'never'})['thread']['id']
        result=rpc('turn/start',{'threadId':thread,'input':[{'type':'text','text':'Reply exactly AGENT_GUI_OK. Do not use tools.'}]})
        turn=completion(thread)
        assert turn['status']=='completed', str(turn.get('error'))
        assert any(m.get('method')=='item/agentMessage/delta' for m in events),'no streaming delta'
        print('PASS: real turn/start, streaming delta, turn/completed')
        print('INFO: token usage event:',any(m.get('method')=='thread/tokenUsage/updated' for m in events))
        if '--image' in __import__('sys').argv:
            def chunk(kind, payload):
                return struct.pack('!I',len(payload))+kind+payload+struct.pack('!I',zlib.crc32(kind+payload)&0xffffffff)
            path=os.path.join(cwd,'red fixture.png')
            raw=b''.join(b'\x00'+bytes([255,0,0])*64 for _ in range(64))
            with open(path,'wb') as f:
                f.write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',64,64,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
            events.clear()
            rpc('turn/start',{'threadId':thread,'input':[{'type':'text','text':'Name the single color of this image in English. Reply with one word. Do not use tools.'},{'type':'localImage','path':path}]})
            turn=completion(thread)
            assert turn['status']=='completed',str(turn.get('error'))
            text=''.join(m.get('params',{}).get('item',{}).get('text','') for m in events if m.get('method')=='item/completed')
            assert 'red' in text.lower(), 'image color not recognized'
            print('PASS: localImage path with spaces delivered to the model; red recognized')
            events.clear()
        result=rpc('turn/start',{'threadId':thread,'input':[{'type':'text','text':'Count from 1 to 2000, one number per line. Do not use tools.'}]})
        rpc('turn/interrupt',{'threadId':thread,'turnId':result['turn']['id']})
        turn=completion(thread)
        assert turn['status']=='interrupted',turn['status']
        print('PASS: turn/interrupt yields interrupted completion')
    finally:
        p.terminate()
        try:p.wait(3)
        except subprocess.TimeoutExpired:p.kill();p.wait()
