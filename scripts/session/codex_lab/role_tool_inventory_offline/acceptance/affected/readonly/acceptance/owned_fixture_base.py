"""Independent five-case owned-runtime CLI acceptance. NOT RUN locally."""
import argparse,copy,hashlib,importlib.util,json,os,re,signal,sqlite3,subprocess,sys,tempfile,time,unittest,uuid
from pathlib import Path
PACKAGE=Path(__file__).resolve().parent
CFG=None
BOOT='import sys,runpy;root=sys.argv.pop(1);sys.path.insert(0,root);runpy.run_path(root+"/portable_broker.py",run_name="__main__")'
def sha(b):return hashlib.sha256(b).hexdigest()
class Hold(RuntimeError):pass

def source_argv(*args):return [sys.executable,'-I','-S','-B','-c',BOOT,str(CFG.source_root),*args]
def alive(pid):
    try:return Path('/proc/'+str(pid)+'/stat').read_text().rsplit(')',1)[1].split()[0] not in ('Z','X')
    except FileNotFoundError:return False

class Fixture:
    def __init__(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='owned-test-',dir=CFG.runner_temp);self.root=Path(self.tmp.name).resolve();self.root.chmod(0o700)
        for n in ('runtime','owner','work','home'):(self.root/n).mkdir(mode=0o700)
        self.runtime=self.root/'runtime';self.database=self.root/'owner/runtime.sqlite3';self.sock=self.runtime/'owned.sock';self.server=None;self.seq=0;self.auth=dict(client='fixture-owner',generation=1,token=os.urandom(32).hex());self.initial_auth=dict(self.auth)
        # Actual existing base Store creates its own eight-table scratch schema;
        # no ResumeStore/bootstrap migration/NativeProxy is used or imported.
        sys.path.insert(0,str(PACKAGE/'owner-store-source'))
        from runtime_store import Store
        from portable_config import PrivateRuntimeBinding
        self.owner_pin=sha(os.urandom(32));binding=PrivateRuntimeBinding(json.dumps({'native':{'expected_registered_session':str(uuid.uuid4())}}),sha(b'fixture-binding-data'),'b9ea684132b22e3cd0c0097a6edb7c37831d875658885a0453c7c4a22028a3c0')
        store=Store(str(self.database.parent),dict(binding=binding,pin=self.owner_pin))
        with store.tx() as db:
            db.execute('update owner set client=?,generation=1,token_sha=?,paused=0 where id=1',(self.auth['client'],sha(self.auth['token'].encode())))
            row=store.owner(db,self.auth)
            if row['generation']!=1:raise Hold('actual Store.owner fixture unavailable')
        store.db.close();self.database.chmod(0o600)
        self.audit=self.root/'child-audit.jsonl';self.control=self.root/'control.json';self.replay=self.root/'old-frame.json';self.mode('normal')
        binary=str(Path(sys.executable).resolve());worker=str(PACKAGE/'stdio_fixture.py')
        self.install=dict(schema='lab-owned-runtime-installation/1',kind='fixture-stdio-only',runtime_directory=str(self.runtime),owner_database=str(self.database),owner_installation_sha256=self.owner_pin,
            physical_cwd=str(self.root/'work'),control_socket=str(self.sock),child=dict(argv=[binary,'-I','-S','-B',worker,'--audit',str(self.audit),'--control',str(self.control),'--replay',str(self.replay)],files_sha256={binary:sha(Path(binary).read_bytes()),worker:sha(Path(worker).read_bytes())},environment=dict(PATH='/usr/bin:/bin',HOME=str(self.root/'home'),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1')),timeouts=dict(startup_ms=3000,request_ms=3000,stop_ms=3000))
        self.calls=[]
        self.install_path=self.root/'owned-installation.json';self.install_path.write_text(json.dumps(self.install));self.install_path.chmod(0o600)
        self.env=dict(PATH='/usr/bin:/bin',HOME=str(self.root/'home'),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1')
    def mode(self,value):self.control.write_text(json.dumps(dict(mode=value)));self.control.chmod(0o600)
    def rows(self):return [json.loads(x) for x in self.audit.read_text().splitlines()] if self.audit.exists() else []
    def requests(self):return [r for r in self.rows() if r['event']=='in']
    def flags(self):
        with sqlite3.connect(self.database.as_uri()+'?mode=ro',uri=True) as db:return db.execute('select generation,paused from owner where id=1').fetchone()
    def argv(self,mode):return source_argv(mode,'--source-closure',str(CFG.source_root/'SOURCE-CLOSURE.json'),'--source-closure-sha256',CFG.source_closure_sha256,'--owned-installation',str(self.install_path),'--owned-installation-sha256',sha(self.install_path.read_bytes()))
    def serve(self):
        self.server=subprocess.Popen(self.argv('serve-owned'),cwd=self.root,env=self.env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
        end=time.monotonic()+8
        while self.server.poll() is None and not self.sock.exists() and time.monotonic()<end:time.sleep(.02)
        if self.server.poll() is not None or not self.sock.exists():raise Hold('owned public serve did not expose scratch control socket')
    def stop_server(self):
        if self.server and self.server.poll() is None:
            self.server.send_signal(signal.SIGINT)
            try:self.server.wait(timeout=6)
            except subprocess.TimeoutExpired:os.killpg(self.server.pid,signal.SIGKILL);self.server.wait(timeout=6);raise Hold('owned control server did not stop')
    def call(self,command,arguments=None,auth=None):
        self.seq+=1;path=self.root/('request-'+str(self.seq)+'.json');path.write_text(json.dumps(dict(command=command,auth=self.auth if auth is None else auth,arguments=arguments or {})));path.chmod(0o600)
        p=subprocess.run(self.argv('call-owned')+['--request',str(path)],cwd=self.root,env=self.env,capture_output=True,timeout=12)
        if any(s.encode() in p.stdout+p.stderr for s in (self.initial_auth['token'],self.auth['token'])):raise AssertionError('OWNED_CLI_CREDENTIAL_DISCLOSURE')
        try:value=json.loads(p.stdout)
        except ValueError:raise Hold('owned public call returned no JSON')
        self.calls.append(dict(command=command,arguments=arguments or {},response=value))
        return value
    def close(self):
        try:self.stop_server()
        finally:self.tmp.cleanup()

def rejected(test,value):
    test.assertEqual(value.get('action'),'hold','OWNED_OPERATION_NOT_FAIL_CLOSED')
    test.assertNotIn('result',value,'REJECTED_CALL_RETURNED_STALE_NATIVE_RESULT')

def rpc(test,f,epoch,command,method):
    request_id=uuid.uuid4().hex;value=f.call(command,dict(epoch=epoch,request_id=request_id))
    test.assertEqual(value.get('action'),'ok');test.assertEqual(value.get('request_id'),request_id);test.assertEqual(value.get('epoch'),epoch)
    native=value.get('native_id');test.assertIn(type(native),(str,int));test.assertNotEqual(native,'')
    inputs=[r for r in f.rows() if r['event']=='in' and r['wire'].get('method')==method and r['wire'].get('id')==native]
    outputs=[r for r in f.rows() if r['event']=='out' and r['wire'].get('id')==native]
    test.assertEqual(len(inputs),1);test.assertEqual(len(outputs),1);test.assertEqual(inputs[0]['instance'],outputs[0]['instance'])
    test.assertEqual(value.get('result'),outputs[0]['wire'].get('result'));test.assertNotIn('auth',inputs[0]['wire'].get('params',{}))
    return value,outputs[0]['wire']

def start(test,f):
    before=len([r for r in f.rows() if r['event']=='process-start']);v=f.call('runtime.start')
    test.assertEqual(v.get('action'),'started');test.assertIsInstance(v.get('epoch'),str);test.assertTrue(v['epoch']);test.assertIs(type(v.get('pid')),int)
    starts=[r for r in f.rows() if r['event']=='process-start'];test.assertEqual(len(starts),before+1);test.assertEqual(v['pid'],starts[-1]['pid']);test.assertTrue(alive(v['pid']))
    return v,starts[-1]

def stop(test,f,epoch):
    rid=uuid.uuid4().hex;v=f.call('runtime.stop',dict(epoch=epoch,request_id=rid));test.assertEqual(v.get('action'),'stopped');test.assertEqual(v.get('request_id'),rid);test.assertEqual(v.get('epoch'),epoch);test.assertIsNone(v.get('native_id'));return v

class OwnedAcceptance(unittest.TestCase):
    def setUp(self):
        # Baseline red is an explicit required public-CLI behavior assertion.
        # It runs before ANY fixture/child/SQLite setup; no usage-error is red.
        self.assertTrue({'serve-owned','call-owned'}<=CFG.public_modes,'OWNED_RUNTIME_PUBLIC_MODES_MISSING:'+self._testMethodName)
        self.f=Fixture();CFG.fixture_created.add(self._testMethodName);self.addCleanup(self.f.close);self.f.serve();CFG.control_started.add(self._testMethodName)
    def tearDown(self):
        if not hasattr(self,'f'):return
        facts=[]
        for row in self.f.rows():
            wire=row.get('wire',{})
            facts.append(dict(event=row['event'],pid=row['pid'],instance=row['instance'],
                              method=wire.get('method'),wire_id=wire.get('id'),
                              wire_sha256=sha(json.dumps(wire,sort_keys=True).encode())))
        value=dict(case=self._testMethodName,child_facts=facts,calls=self.f.calls)
        raw=json.dumps(value)
        if any(secret in raw for secret in (self.f.initial_auth['token'],self.f.auth['token'])):
            value=dict(case=self._testMethodName,credential_disclosure_observed=True)
        CFG.case_facts.append(value)
        (CFG.evidence_dir/'case-observations.json').write_text(json.dumps(dict(preflight=CFG.preflight,cases=CFG.case_facts),indent=2)+chr(10))
    def test_01_real_owned_start_read_stop(self):
        f=self.f;self.assertEqual(f.rows(),[],'SERVE_IMPLICITLY_STARTED_CHILD');v,instance=start(self,f)
        rpc(self,f,v['epoch'],'runtime.config-read','config/read');rpc(self,f,v['epoch'],'runtime.thread-list','thread/list');stop(self,f,v['epoch'])
        self.assertFalse(alive(v['pid']));state=f.call('runtime.status');self.assertEqual(state.get('state'),'stopped');stop(self,f,v['epoch'])
    def test_02_wrong_or_unexpected_wire_id_rejected(self):
        for mode in ('wrong-id','unsolicited'):
            with self.subTest(mode=mode):
                v,_=start(self,self.f);self.f.mode(mode);rejected(self,self.f.call('runtime.config-read',dict(epoch=v['epoch'],request_id=uuid.uuid4().hex)))
                state=self.f.call('runtime.status');self.assertIn(state.get('state'),('faulted','disconnected','stopped'));stop(self,self.f,v['epoch']);self.f.mode('normal')
    def test_03_exit_and_stdout_disconnect_not_cached_success(self):
        for mode in ('exit','disconnect'):
            with self.subTest(mode=mode):
                v,_=start(self,self.f);rpc(self,self.f,v['epoch'],'runtime.config-read','config/read');self.f.mode(mode)
                rejected(self,self.f.call('runtime.config-read',dict(epoch=v['epoch'],request_id=uuid.uuid4().hex)))
                self.assertIn(self.f.call('runtime.status').get('state'),('faulted','disconnected','stopped'));stop(self,self.f,v['epoch']);self.f.mode('normal')
    def test_04_current_owner_and_pause_survive_broker_restart(self):
        f=self.f;v,_=start(self,f);self.assertEqual(f.call('runtime.pause',dict(paused=True)).get('action'),'paused');self.assertEqual(f.flags(),(1,1))
        for command in ('runtime.start','runtime.config-read','runtime.thread-list'):
            rejected(self,f.call(command,{} if command=='runtime.start' else dict(epoch=v['epoch'],request_id=uuid.uuid4().hex)))
        stop(self,f,v['epoch']);f.stop_server();self.assertFalse(f.sock.exists(),'OWNED_CONTROL_SOCKET_NOT_REMOVED_ON_CONTROLLED_STOP');f.serve();self.assertEqual(f.flags(),(1,1));rejected(self,f.call('runtime.start'))
        old=dict(f.auth);new=dict(client='fixture-next',generation=2,token=os.urandom(32).hex())
        with sqlite3.connect(f.database) as db:db.execute('update owner set client=?,generation=?,token_sha=? where id=1',(new['client'],2,sha(new['token'].encode())))
        f.auth=new
        for command in ('runtime.start','runtime.status','runtime.config-read','runtime.thread-list','runtime.stop','runtime.pause'):
            arguments=dict(paused=False) if command=='runtime.pause' else (dict(epoch=v['epoch'],request_id=uuid.uuid4().hex) if command in ('runtime.config-read','runtime.thread-list','runtime.stop') else {})
            rejected(self,f.call(command,arguments,old))
        self.assertEqual(f.call('runtime.status').get('action'),'status');self.assertEqual(f.call('runtime.pause',dict(paused=False)).get('action'),'paused');self.assertEqual(f.flags(),(2,0));fresh,_=start(self,f);self.assertNotEqual(fresh['epoch'],v['epoch']);stop(self,f,fresh['epoch'])
    def test_05_same_broker_restart_new_epoch_old_result_fenced(self):
        f=self.f;first,instance1=start(self,f);value,old_wire=rpc(self,f,first['epoch'],'runtime.config-read','config/read')
        repeat=f.call('runtime.start');self.assertIn(repeat.get('action'),('started','hold'));self.assertEqual(len([r for r in f.rows() if r['event']=='process-start']),1)
        stop(self,f,first['epoch']);self.assertFalse(alive(first['pid']));second,instance2=start(self,f)
        self.assertNotEqual(second['epoch'],first['epoch']);self.assertNotEqual(instance1['instance'],instance2['instance'])
        before=len(f.requests())
        for command in ('runtime.config-read','runtime.thread-list','runtime.stop'):
            rejected(self,f.call(command,dict(epoch=first['epoch'],request_id=uuid.uuid4().hex)))
        self.assertEqual(len(f.requests()),before,'OLD_EPOCH_REACHED_NEW_CHILD')
        f.replay.write_text(json.dumps(old_wire));f.replay.chmod(0o600);f.mode('replay')
        rejected(self,f.call('runtime.config-read',dict(epoch=second['epoch'],request_id=uuid.uuid4().hex)))
        new_request=[r for r in f.requests() if r['wire'].get('method')=='config/read'][-1]
        self.assertNotEqual(new_request['wire']['id'],old_wire['id'],'NATIVE_WIRE_ID_REUSED_ACROSS_EPOCHS');stop(self,f,second['epoch'])

def main():
    global CFG
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--runner-temp',required=True,type=Path);p.add_argument('--source-root',type=Path,default=PACKAGE/'baseline-source');p.add_argument('--evidence-dir',required=True,type=Path);p.add_argument('--parent-netns',required=True);p.add_argument('--source-closure-sha256',default='5094eb8b0aa25e6dae12b3f658a74a5f865c29c14c9c02e427800378a0fa6287');CFG=p.parse_args()
    CFG.source_root=CFG.source_root.resolve(strict=True);CFG.runner_temp=CFG.runner_temp.resolve(strict=True);CFG.evidence_dir=CFG.evidence_dir.resolve(strict=True)
    if any(CFG.evidence_dir.iterdir()):raise Hold('fresh evidence directory required')
    try:
        if sys.platform!='linux' or os.geteuid()==0 or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':raise Hold('nonroot hosted Linux required')
        if os.readlink('/proc/self/ns/net')==CFG.parent_netns:raise Hold('private netns required')
        if any(os.environ.get(k) for k in ('GH_TOKEN','GITHUB_TOKEN','GH_CONFIG_DIR','SSH_AUTH_SOCK','OPENAI_API_KEY')):raise Hold('credential environment refused')
        raw=(CFG.source_root/'SOURCE-CLOSURE.json').read_bytes()
        if sha(raw)!=CFG.source_closure_sha256:raise Hold('independent executable closure differs')
        closure=json.loads(raw)
        for name,pin in closure['files_sha256'].items():
            if sha((CFG.source_root/name).read_bytes())!=pin:raise Hold('source dependency differs')
        probe=subprocess.run(source_argv('--help'),cwd=CFG.source_root,env=dict(PATH='/usr/bin:/bin',HOME=str(CFG.runner_temp),PYTHONDONTWRITEBYTECODE='1'),capture_output=True,timeout=8)
        if probe.returncode!=0:raise Hold('actual baseline public CLI help unavailable')
        groups=re.findall('[{]([^{}]+)[}]',probe.stdout.decode());CFG.public_modes=set()
        for group in groups:
            choices={v.strip() for v in group.split(',')}
            if {'serve','call'}<=choices:CFG.public_modes|=choices
        CFG.preflight=dict(public_CLI_help_exit=0,public_modes=sorted(CFG.public_modes),help_stdout_sha256=sha(probe.stdout),source_closure_sha256=CFG.source_closure_sha256);CFG.case_facts=[];CFG.fixture_created=set();CFG.control_started=set()
        (CFG.evidence_dir/'case-observations.json').write_text(json.dumps(dict(preflight=CFG.preflight,cases=[]))+chr(10))
    except Exception as e:
        (CFG.evidence_dir/'verdict.json').write_text(json.dumps(dict(classification='capability-HOLD',reason=type(e).__name__,tests=0)));return 86
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(OwnedAcceptance))
    failed={t._testMethodName for t,_ in result.failures};errors={t._testMethodName for t,_ in result.errors}
    actual={item['case']:item for item in CFG.case_facts};summaries=[]
    for name in unittest.defaultTestLoader.getTestCaseNames(OwnedAcceptance):
        item=actual.get(name,dict(case=name,child_facts=[],calls=[]))
        item.update(public_cli_reached=True,fixture_started=name in CFG.fixture_created,
                    owned_control_started=name in CFG.control_started,actually_executed=name in actual,
                    passed=name not in failed|errors,
                    status='error-HOLD' if name in errors else ('assertion-red' if name in failed else 'passed'))
        child=item.get('child_facts',[]);calls=item.get('calls',[])
        item['owned_lifecycle_summary']=dict(real_child_process_starts=sum(x.get('event')=='process-start' for x in child),
            actual_stdio_methods=[x['method'] for x in child if x.get('event')=='in'],
            successful_start_epochs=[x['response'].get('epoch') for x in calls if x['command']=='runtime.start' and x['response'].get('action')=='started'],
            stopped_responses=sum(x['command']=='runtime.stop' and x['response'].get('action')=='stopped' for x in calls))
        summaries.append(item)
    (CFG.evidence_dir/'case-observations.json').write_text(json.dumps(dict(preflight=CFG.preflight,
        fixture_started=bool(CFG.fixture_created),owned_control_started=bool(CFG.control_started),cases=summaries),indent=2)+chr(10))
    expected_missing=len(result.failures)==5 and not result.errors and all('OWNED_RUNTIME_PUBLIC_MODES_MISSING:'in text for _,text in result.failures)
    verdict=dict(classification='expected-public-behavior-red' if expected_missing else ('owned-runtime-fixture-selection-passed' if result.wasSuccessful() else 'owned-runtime-selection-not-passed'),tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),baseline_expected_red=expected_missing,local_execution_claimed=False,production_or_native_acceptance=False)
    (CFG.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));return 86 if result.errors or result.skipped or result.testsRun!=5 else (0 if result.wasSuccessful() else 1)
if __name__=='__main__':raise SystemExit(main())
