#!/usr/bin/env python3
"""Read-only handshake against an installed Codex; never print account secrets."""
import json, os, selectors, shutil, subprocess, time
binary = os.environ.get('CODEX_BINARY') or shutil.which('codex')
assert binary, 'Codex not found'
p = subprocess.Popen([binary, 'app-server', '--listen', 'stdio://'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
selector = selectors.DefaultSelector()
selector.register(p.stdout, selectors.EVENT_READ)
seq = 0
def rpc(method, params):
    global seq
    seq += 1
    p.stdin.write(json.dumps(dict(id=seq, method=method, params=params))+'\n'); p.stdin.flush()
    deadline = time.monotonic()+30
    while time.monotonic()<deadline:
        if not selector.select(1): continue
        line = p.stdout.readline()
        assert line, 'process exited'
        msg=json.loads(line)
        if msg.get('id') != seq: continue
        assert 'error' not in msg, msg.get('error')
        return msg['result']
    raise TimeoutError(method)
try:
    rpc('initialize', {'clientInfo':{'name':'agent_gui_smoke','version':'0.1.0'}})
    p.stdin.write('{"method":"initialized"}\n'); p.stdin.flush()
    models=rpc('model/list', {})
    assert models['data'], 'empty model catalog'
    account=rpc('account/read', {'refreshToken':False})
    print('PASS: initialize, initialized, model/list (%d models), account/read (signed in: %s)' % (len(models['data']), account.get('account') is not None))
finally:
    p.terminate()
    try: p.wait(3)
    except subprocess.TimeoutExpired: p.kill(); p.wait()
