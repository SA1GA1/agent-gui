#!/usr/bin/env python3
"""Read-only handshake against an installed Codex; never print account secrets."""
import argparse, json, os, selectors, shutil, subprocess, time
parser=argparse.ArgumentParser()
parser.add_argument("--project-history", help="Read-only checks for prompt pagination and installed plugins in this project")
args=parser.parse_args()
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
    if args.project_history:
        directory=os.path.abspath(args.project_history)
        plugins=rpc('plugin/list', {'cwds':[directory],'forceRefetch':False})
        installed=[plugin for marketplace in plugins.get('marketplaces',[]) for plugin in marketplace.get('plugins',[]) if plugin.get('installed') and plugin.get('enabled')]
        threads=rpc('thread/list', {'cwd':directory,'limit':1,'modelProviders':['openai']})
        if threads.get('data'):
            page=rpc('thread/items/list', {'threadId':threads['data'][0]['id'],'limit':100,'sortDirection':'asc'})
            assert all('item' in entry and 'turnId' in entry for entry in page['data'])
            print('PASS: prompt pagination (%d entries)' % len(page['data']))
        print('PASS: plugin discovery (%d installed and enabled)' % len(installed))
    print('PASS: initialize, initialized, model/list (%d models), account/read (signed in: %s)' % (len(models['data']), account.get('account') is not None))
finally:
    p.terminate()
    try: p.wait(3)
    except subprocess.TimeoutExpired: p.kill(); p.wait()
