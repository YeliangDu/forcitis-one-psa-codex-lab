"""Actual official App Server process and loopback-only Responses request capture."""
import argparse,hashlib,http.server,json,os,select,subprocess,sys,threading,time,unittest
from pathlib import Path
import zlib

P=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()

def toml(d):
    return ''.join(f'{k} = {json.dumps(v)}\n' for k,v in d.items() if not isinstance(v,dict))

class Provider(http.server.ThreadingHTTPServer):
    daemon_threads=True
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*a):pass
    def do_GET(self):
        self.send_error(404)
    def do_POST(self):
        if self.path.split('?')[0]!='/v1/responses':
            self.send_error(404);return
        n=int(self.headers.get('Content-Length','0'))
        if not 0<n<512*1024:
            self.send_error(400);return
        data=self.rfile.read(n)
        if self.headers.get('Content-Encoding')=='gzip':data=zlib.decompress(data,31)
        elif self.headers.get('Content-Encoding')=='zstd':
            import ctypes
            z=ctypes.CDLL('libzstd.so.1');buf=ctypes.create_string_buffer(1024*1024)
            z.ZSTD_decompress.restype=ctypes.c_size_t
            size=z.ZSTD_decompress(buf,len(buf),data,len(data))
            if size>len(buf):raise ValueError('zstd capture size')
            data=buf.raw[:size]
        self.server.requests.append(data)
        self.server.ready.set()
        body=b'{"error":{"message":"offline capture completed","type":"invalid_request_error"}}'
        self.send_response(400);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)))
        self.end_headers();self.wfile.write(body)

def capture(a, native, facts):
    engine=Path(os.environ['LAB_OFFLINE_ENGINE']).resolve(strict=True)
    facts['engine_sha256']=sha(engine.read_bytes())
    assert facts['engine_sha256']=='cdcb45aaa01d0f14e298985e09465aebccd47f64ecd298aea44814af7b62bbda'
    v=subprocess.check_output([engine,'--version'],timeout=10,text=True).strip()
    facts['engine_version']=v
    assert v.endswith(' 0.167.0'),v
    home=Path(a.runner_temp)/'engine-home'; home.mkdir(mode=0o700)
    work=Path(a.runner_temp)/'engine-project'; work.mkdir(mode=0o700)
    cfg=home/'config.toml'
    catalog=P/'model-catalog.json'
    assert sha(catalog.read_bytes())=='d3abd842194800885cfdf099354dd2b7ef0c8d489d35fd90b8272cb190194703'
    facts['catalog_sha256']=sha(catalog.read_bytes())
    server=Provider(('127.0.0.1',0),Handler);server.requests=[];server.ready=threading.Event()
    th=threading.Thread(target=server.serve_forever,daemon=True);th.start()
    port=server.server_address[1]
    profile=native.expected_config(work);profile.update(model_catalog_json=str(catalog),model_provider='offline_capture')
    provider=dict(name='Offline capture',base_url=f'http://127.0.0.1:{port}/v1',wire_api='responses',
                  supports_websockets=False,requires_openai_auth=False,request_max_retries=0,stream_max_retries=0)
    cfg.write_text(toml(profile)+''.join('\n['+k+']\n'+toml(profile[k]) for k in ('features','agents','analytics'))+
        ''.join('\n[tools.'+k+']\n'+toml(v) for k,v in profile['tools'].items())+'\n[model_providers.offline_capture]\n'+toml(provider))
    facts['effective_input_toml']=cfg.read_text()
    err=Path(a.runner_temp)/'engine.stderr'
    argv=[engine,'app-server','--listen','stdio://','--strict-config']
    env=dict(PATH=str(engine.parent)+':/usr/bin:/bin',HOME=str(home),CODEX_HOME=str(home),TMPDIR=a.runner_temp,LANG='C.UTF-8')
    with err.open('wb') as ef:
        proc=subprocess.Popen(argv,cwd=work,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=ef)
    pending=b''; wire=[]; started=False
    def send(method,params=None,id=None):
        data=dict(method=method)
        if params is not None:data['params']=params
        if id is not None:data['id']=id
        proc.stdin.write(json.dumps(data).encode()+b'\n');proc.stdin.flush()
    def reply(id,timeout=18):
        nonlocal pending
        until=time.monotonic()+timeout
        while time.monotonic()<until:
            if b'\n' not in pending:
                if not select.select([proc.stdout],[],[],max(0,until-time.monotonic()))[0]:break
                b=os.read(proc.stdout.fileno(),65536)
                if not b:break
                pending+=b;continue
            row,pending=pending.split(b'\n',1); x=json.loads(row)
            if x.get('method')=='configWarning':raise ValueError('strict config warning: '+str(x.get('params')))
            if x.get('id')==id and 'method' not in x:
                wire.append(dict(id=id,success='result' in x,error=x.get('error')))
                if 'result' not in x:raise ValueError(str(x.get('error')))
                return x['result']
        raise ValueError(f'RPC reply missing: {id}')
    try:
        send('initialize',dict(clientInfo=dict(name='offline-inventory',version='1'),capabilities=dict(experimentalApi=True)),1)
        reply(1);send('initialized')
        send('thread/start',dict(cwd=str(work),approvalPolicy='never',permissions=':read-only',
            ephemeral=True,environments=[],dynamicTools=[native.__dict__.get('DYNAMIC_TOOL') or __import__('native_role').TOOL]),2)
        tid=reply(2)['thread']['id']
        send('turn/start',dict(threadId=tid,cwd=str(work),environments=[],input=[dict(type='text',text='Offline tool inventory; capture only.')]),3)
        turn=reply(3)['turn']['id'];started=True
        assert server.ready.wait(24),'official engine did not reach loopback Responses route'
        facts.update(thread_id=tid,turn_id=turn,synthetic_ephemeral=True,provider='127.0.0.1 only')
    finally:
        live=proc.poll() is None
        proc.stdin.close()
        try:rc=proc.wait(timeout=12)
        except subprocess.TimeoutExpired:
            proc.kill();rc=proc.wait()
        tail=proc.stdout.read()
        proc.stdout.close()
        server.shutdown();server.server_close()
        facts.update(terminal=dict(stdin_eof_sent_live=live,wait=rc,unread_stdout_bytes=len(tail)),
                     rpc=wire,stderr=err.read_text(errors='replace')[-7000:],
                     requests=[dict(sha256=sha(b),payload=json.loads(b)) for b in server.requests])
    assert started and len(server.requests)==1 and rc==0,'one actual request and same process clean stop required'
    return json.loads(server.requests[0])

def main():
    p=argparse.ArgumentParser()
    for flag in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):
        p.add_argument('--'+flag,required=True)
    a=p.parse_args();out=Path(a.evidence_dir)
    facts={};results={};rc=86;status='setup-HOLD'
    try:
        assert sys.platform=='linux' and os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted'
        assert os.readlink('/proc/self/ns/net')!=a.parent_netns
        assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
        root=Path(a.source_root); raw=(root/'SOURCE-CLOSURE.json').read_bytes()
        assert sha(raw)==a.source_closure_sha256
        for name,h in json.loads(raw)['files_sha256'].items():assert sha((root/name).read_bytes())==h
        sys.path[:0]=[str(root),str(P)]
        import role_native
        import frozen_tools
        payload=capture(a,role_native,facts)
        tests=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(frozen_tools))
        results['allowlist_parser']=dict(tests=tests.testsRun,failures=len(tests.failures),errors=len(tests.errors),skips=len(tests.skipped))
        try:
            frozen_tools.assert_engine_allowlist(payload)
            allowed=True
        except AssertionError as exc:
            facts['allowlist_assertion']=str(exc);allowed=False
        results['actual_official_request']=dict(tests=1,failures=int(not allowed),errors=0,skips=0)
        declarations=payload.get('tools',[])+[t for item in payload.get('input',[]) if item.get('type')=='additional_tools' for t in item['tools']]
        names={t.get('name') or t.get('type') for t in declarations}
        names.update(m.get('name') for t in declarations for m in t.get('tools',[]))
        facts['captured_tool_names']=sorted(names)
        historical={'get_goal','create_goal','update_goal','imagegen','lab_read_assignment'}
        if a.stage=='red' and historical<=names and not allowed and tests.wasSuccessful() and tests.testsRun>0:
            status='expected-actual-engine-inventory-red';rc=1
        if a.stage=='green' and allowed and tests.wasSuccessful() and tests.testsRun>0:
            assert role_native.request_tool_inventory(payload)
            e=out/'affected';e.mkdir()
            q=subprocess.run([sys.executable,'-I','-B',str(P/'affected/run_suites.py'),
                '--runner-temp',a.runner_temp,'--source-root',a.source_root,'--evidence-dir',str(e),
                '--parent-netns',a.parent_netns,'--source-closure-sha256',a.source_closure_sha256,'--stage','green'],timeout=300)
            regression=json.loads((e/'verdict.json').read_bytes())
            results['affected']=regression
            if q.returncode==0 and regression['tests']==17:
                status='official-engine-and-affected-regressions-passed';rc=0
    except Exception as exc:
        facts['error']=str(exc)
        print(type(exc).__name__,str(exc),file=sys.stderr)
    (out/'case-observations.json').write_text(json.dumps(facts,indent=2)+'\n')
    verdict=dict(classification=status,suites=results,**{k:sum(v.get(k,0) for v in results.values()) for k in ('tests','failures','errors','skips')})
    (out/'verdict.json').write_text(json.dumps(verdict,indent=2)+'\n')
    print(json.dumps(verdict))
    return rc
if __name__=='__main__':raise SystemExit(main())
