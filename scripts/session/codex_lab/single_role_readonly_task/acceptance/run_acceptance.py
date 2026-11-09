"""Independent five-case single-role readonly owned stdio selection. NOT RUN."""
import argparse,copy,hashlib,importlib.util,json,os,sqlite3,sys,time,unittest,uuid
from pathlib import Path
PACKAGE=Path(__file__).resolve().parent
BASE_SHA=__BASE__
CFG=None;B=None
class Hold(RuntimeError):pass
def sha(b):return hashlib.sha256(b).hexdigest()
def private(p,v):p.write_text(json.dumps(v));p.chmod(0o600)
def rejected(test,v):test.assertEqual(v.get('action'),'hold');test.assertNotIn('read',v)

class RoleFixture:
    def __init__(self):
        self.f=B.Fixture();self.calls=[];self.rolefile=self.f.root/'single-role-installation.json'
        self.assignment_file=self.f.root/'work/assignment.json'
        # Actual existing RoleStore creates role tables; no ResumeStore/migration,
        # pre-registered fake thread or parallel task state is inserted.
        sys.path.insert(0,str(PACKAGE/'owner-store-source'))
        from native_role import RoleStore
        from portable_config import PrivateRuntimeBinding
        binding=PrivateRuntimeBinding(json.dumps({'native':{'expected_registered_session':str(uuid.uuid4())}}),sha(b'role-fixture-data'),'b9ea684132b22e3cd0c0097a6edb7c37831d875658885a0453c7c4a22028a3c0')
        store=RoleStore(str(self.f.database.parent),dict(binding=binding,pin=self.f.owner_pin));store.db.close()
        self.f.install['kind']='fixture-single-role-stdio-only';private(self.f.install_path,self.f.install)
        self.role_install=dict(schema='lab-single-role-readonly-installation/1',kind='fixture-single-role-readonly',
            owned_installation_sha256=sha(self.f.install_path.read_bytes()),role='one-lead',assignment_file=str(self.assignment_file),
            max_assignment_bytes=4096,dynamic_tool=dict(name='lab_read_assignment',arguments={}))
        private(self.rolefile,self.role_install)
        self.argv_base=self.f.argv
        # Only test-fixture CLI argument composition is extended, not product code.
        self.f.argv=lambda mode:self.argv_base(mode)+['--single-role-installation',str(self.rolefile),
                    '--single-role-installation-sha256',sha(self.rolefile.read_bytes())]
        self.f.serve()
    def call(self,c,a=None,auth=None):
        v=self.f.call(c,a,auth);self.calls.append(dict(command=c,arguments=a or {},response=v));return v
    def start(self,test):
        v=self.call('runtime.start');test.assertEqual(v.get('action'),'started');test.assertTrue(v.get('epoch'));return v['epoch']
    def register(self,test,epoch):
        v=self.call('role.register',dict(epoch=epoch));test.assertEqual(v.get('action'),'registered');test.assertEqual(v.get('epoch'),epoch)
        test.assertTrue(v.get('thread_id'));return v['thread_id']
    def assignment(self,thread,revision):
        return dict(card=1,revision=revision,role='one-lead',session_id=thread,physical_cwd=str(self.f.root/'work'),
                    task='independent readonly revision '+str(revision),A=sha(b'A')[:40],B=sha(b'B')[:40],H=sha(b'H')[:40])
    def publish(self,test,body):
        v=self.call('assignment.publish',dict(assignment=body));test.assertEqual(v.get('action'),'published')
        # Input file belongs to independent test owner, never a parallel task ledger.
        self.assignment_file.write_text(json.dumps(body,indent=2)+'\n');self.assignment_file.chmod(0o600)
    def snapshot(self):
        with sqlite3.connect(self.f.database.as_uri()+'?mode=ro',uri=True) as db:
            db.row_factory=sqlite3.Row;db.execute('BEGIN')
            return {n:[dict(r) for r in db.execute('select * from '+n)] for n in ('tasks','turns','role_registration','native_dispatch','native_tool_operations')}
    def inputs(self,method):return [r for r in self.f.rows() if r.get('event')=='in' and r['wire'].get('method')==method]
    def finish(self,test,epoch,revision):
        d=self.call('role.dispatch-current',dict(epoch=epoch,revision=revision));test.assertEqual(d.get('action'),'dispatched');test.assertEqual(d.get('epoch'),epoch);test.assertEqual(d.get('revision'),revision);test.assertTrue(d.get('turn_id'))
        end=time.monotonic()+6;observed=None
        while time.monotonic()<end:
            observed=self.call('role.readback-current',dict(epoch=epoch,revision=revision))
            if observed.get('action')=='observed':break
            test.assertEqual(observed.get('action'),'hold');time.sleep(.02)
        if not observed or observed.get('action')!='observed':raise Hold('actual native tool/readback capability did not complete')
        test.assertEqual(observed['thread_id'],d['thread_id']);test.assertEqual(observed['turn_id'],d['turn_id'])
        test.assertEqual(observed['epoch'],epoch);test.assertEqual(observed['revision'],revision)
        raw=self.assignment_file.read_bytes();test.assertEqual(observed['read'],dict(revision=revision,sha256=sha(raw),assignment=json.loads(raw)))
        snap=self.snapshot();ops=[op for op in snap['native_tool_operations'] if op['revision']==revision]
        test.assertEqual(len(ops),1);op=ops[0];test.assertEqual(op['session_id'],d['thread_id']);test.assertEqual(op['turn_id'],d['turn_id']);test.assertEqual(op['generation'],self.f.auth['generation']);test.assertLess(op['authorization_seq'],op['execution_seq'])
        call=[r['wire'] for r in self.f.rows() if r['event']=='out' and r['wire'].get('method')=='item/tool/call' and r['wire']['params']['turnId']==d['turn_id']]
        test.assertEqual(len(call),1);test.assertEqual(call[0]['params']['arguments'],{});test.assertEqual(call[0]['params']['tool'],'lab_read_assignment');test.assertEqual(op['call_id'],call[0]['params']['callId'])
        replies=[r['wire'] for r in self.f.rows() if r['event']=='in' and 'method'not in r['wire'] and r['wire'].get('id')==call[0]['id']]
        test.assertEqual(len(replies),1);test.assertIs(replies[0]['result'].get('success'),True)
        test.assertEqual(json.loads(op['result']),replies[0]['result']);payload=json.loads(replies[0]['result']['contentItems'][0]['text']);test.assertEqual(payload,observed['read'])
        native=self.inputs('turn/start');test.assertEqual(native[-1]['wire']['params']['threadId'],d['thread_id'])
        return observed
    def stop(self,test,epoch):
        v=self.call('runtime.stop',dict(epoch=epoch,request_id=uuid.uuid4().hex));test.assertEqual(v.get('action'),'stopped')
    def close(self):self.f.close()

class SingleRoleAcceptance(unittest.TestCase):
    def setUp(self):
        self.assertTrue(CFG.gate_present,'SINGLE_ROLE_READONLY_PUBLIC_GATE_MISSING:'+self._testMethodName)
        self.f=RoleFixture();CFG.started.add(self._testMethodName);self.addCleanup(self.f.close)
    def tearDown(self):
        if not hasattr(self,'f'):return
        rows=self.f.f.rows();fact=dict(case=self._testMethodName,fixture_started=True,calls=self.f.calls,
            process_starts=sum(x['event']=='process-start' for x in rows),native_requests=[dict(method=x['wire'].get('method'),id=x['wire'].get('id'),instance=x['instance']) for x in rows if x['event']=='in'],
            durable_counts={k:len(v) for k,v in self.f.snapshot().items()})
        raw=json.dumps(fact)
        if any(s in raw for s in (self.f.f.initial_auth['token'],self.f.f.auth['token'])):fact=dict(case=self._testMethodName,credential_disclosure=True)
        CFG.facts.append(fact)
    def test_01_current_owner_revision_and_physical_cwd_gate(self):
        f=self.f;e=f.start(self);thread=f.register(self,e);body=f.assignment(thread,1);before=f.snapshot()
        bad=dict(body,physical_cwd=str(f.f.root/'home'));rejected(self,f.call('assignment.publish',dict(assignment=bad)));self.assertEqual(before,f.snapshot())
        stale=dict(f.f.auth,generation=0);rejected(self,f.call('assignment.publish',dict(assignment=body),stale));self.assertEqual(before,f.snapshot())
        f.publish(self,body);before_turns=len(f.inputs('turn/start'));rejected(self,f.call('role.dispatch-current',dict(epoch=e,revision=2)));self.assertEqual(before_turns,len(f.inputs('turn/start')))
    def test_02_real_native_ids_and_durable_read_boundary(self):
        f=self.f;e=f.start(self);thread=f.register(self,e);f.publish(self,f.assignment(thread,1));observed=f.finish(self,e,1)
        s=f.snapshot();self.assertEqual(s['role_registration'][0]['session_id'],thread);self.assertTrue(any(r['turn_id']==observed['turn_id'] for r in s['turns']))
    def test_03_same_private_state_runtime_restart_resumes_same_thread(self):
        f=self.f;e=f.start(self);thread=f.register(self,e);f.publish(self,f.assignment(thread,1));prior=f.finish(self,e,1);f.stop(self,e)
        e2=f.start(self);self.assertNotEqual(e2,e);v=f.call('role.resume',dict(epoch=e2));self.assertEqual(v.get('action'),'resumed');self.assertEqual(v.get('thread_id'),thread);self.assertEqual(len(f.inputs('thread/start')),1);self.assertEqual(len(f.inputs('thread/resume')),1)
        before=len(f.inputs('turn/start'));rejected(self,f.call('role.dispatch-current',dict(epoch=e,revision=1)));self.assertEqual(before,len(f.inputs('turn/start')));read=f.call('role.readback-current',dict(epoch=e2,revision=1));self.assertEqual(read.get('action'),'observed');self.assertEqual(read.get('thread_id'),thread);self.assertEqual(read.get('turn_id'),prior['turn_id']);self.assertEqual(read.get('read'),prior['read']);rejected(self,f.call('role.dispatch-current',dict(epoch=e2,revision=1)));self.assertEqual(len(f.inputs('turn/start')),1)
    def test_04_pause_fence_new_revision_first_read_is_new_file(self):
        f=self.f;e=f.start(self);thread=f.register(self,e);f.publish(self,f.assignment(thread,1));f.finish(self,e,1)
        rejected(self,f.call('assignment.revoke',{}));self.assertEqual(f.call('runtime.pause',dict(paused=True)).get('action'),'paused')
        v=f.call('assignment.revoke',{});self.assertLess(v['revoking_seq'],v['fenced_seq']);f.publish(self,f.assignment(thread,2))
        before=len(f.inputs('turn/start'));rejected(self,f.call('role.dispatch-current',dict(epoch=e,revision=1)));rejected(self,f.call('role.dispatch-current',dict(epoch=e,revision=2)));self.assertEqual(before,len(f.inputs('turn/start')))
        self.assertEqual(f.call('runtime.pause',dict(paused=False)).get('action'),'paused');f.finish(self,e,2)
    def test_05_pause_across_control_broker_restart_no_turn_before_resume(self):
        f=self.f;e=f.start(self);thread=f.register(self,e);f.publish(self,f.assignment(thread,1));self.assertEqual(len(f.inputs('turn/start')),0)
        f.call('runtime.pause',dict(paused=True));self.assertEqual(f.f.flags()[1],1);f.stop(self,e);f.f.stop_server();self.assertFalse(f.f.sock.exists());f.f.serve()
        before=len(f.f.requests());rejected(self,f.call('runtime.start'))
        for command in ('role.resume','role.dispatch-current','role.readback-current'):
            a=dict(epoch=e)
            if command!='role.resume':a['revision']=1
            rejected(self,f.call(command,a))
        self.assertEqual(before,len(f.f.requests()));self.assertEqual(len(f.inputs('turn/start')),0);self.assertEqual(f.f.flags()[1],1)
        f.call('runtime.pause',dict(paused=False));e2=f.start(self);self.assertNotEqual(e2,e);self.assertEqual(f.call('role.resume',dict(epoch=e2)).get('thread_id'),thread);f.finish(self,e2,1)

def main():
    global CFG,B
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--runner-temp',required=True,type=Path);p.add_argument('--source-root',type=Path,default=PACKAGE/'baseline-source');p.add_argument('--evidence-dir',required=True,type=Path);p.add_argument('--parent-netns',required=True);p.add_argument('--source-closure-sha256',default='c93b672988d75b3cfcbac458d6e4ae4fb8e5d26f2cd010c3596db83215d00df1');CFG=p.parse_args()
    CFG.runner_temp=CFG.runner_temp.resolve(strict=True);CFG.source_root=CFG.source_root.resolve(strict=True);CFG.evidence_dir=CFG.evidence_dir.resolve(strict=True);CFG.started=set();CFG.facts=[]
    try:
        if any(CFG.evidence_dir.iterdir()):raise Hold('fresh evidence directory required')
        if sys.platform!='linux' or os.geteuid()==0 or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted' or os.readlink('/proc/self/ns/net')==CFG.parent_netns:raise Hold('private nonroot hosted Linux required')
        if any(os.environ.get(k) for k in ('GH_TOKEN','GITHUB_TOKEN','GH_CONFIG_DIR','SSH_AUTH_SOCK','OPENAI_API_KEY')):raise Hold('credential environment refused')
        raw=(CFG.source_root/'SOURCE-CLOSURE.json').read_bytes()
        if sha(raw)!=CFG.source_closure_sha256:raise Hold('external source pin differs')
        for name,pin in json.loads(raw)['files_sha256'].items():
            if sha((CFG.source_root/name).read_bytes())!=pin:raise Hold('source file differs')
        helper=PACKAGE/'owned_fixture_base.py'
        if sha(helper.read_bytes())!='e315a4c5d3fb3973993d2276ef1205e1ce57331bf633a87388664def1072f3b3':raise Hold('owned fixture helper differs')
        spec=importlib.util.spec_from_file_location('_owned_fixture_reuse',helper);B=importlib.util.module_from_spec(spec);sys.modules[spec.name]=B;spec.loader.exec_module(B);B.CFG=CFG
        probe=__import__('subprocess').run(B.source_argv('--help'),cwd=CFG.source_root,env=dict(PATH='/usr/bin:/bin',HOME=str(CFG.runner_temp),PYTHONDONTWRITEBYTECODE='1'),capture_output=True,timeout=8)
        if probe.returncode:raise Hold('actual public CLI help unavailable')
        text=probe.stdout.decode();CFG.gate_present='--single-role-installation 'in text and '--single-role-installation-sha256 'in text
        CFG.preflight=dict(public_cli_help_exit=0,help_sha256=sha(probe.stdout),single_role_gate_declared=CFG.gate_present,fixture_started=False)
    except Exception:
        (CFG.evidence_dir/'verdict.json').write_text(json.dumps(dict(classification='capability-HOLD',tests=0)));return 86
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SingleRoleAcceptance));bad={t._testMethodName for t,_ in result.failures+result.errors};by={x['case']:x for x in CFG.facts};cases=[]
    for name in unittest.defaultTestLoader.getTestCaseNames(SingleRoleAcceptance):
        fact=by.get(name,dict(case=name));fact.update(fixture_started=name in CFG.started,actually_executed=name in by,passed=name not in bad);cases.append(fact)
    (CFG.evidence_dir/'case-observations.json').write_text(json.dumps(dict(preflight=CFG.preflight,fixture_started=bool(CFG.started),cases=cases),indent=2)+chr(10))
    expected=len(result.failures)==5 and not result.errors and all('SINGLE_ROLE_READONLY_PUBLIC_GATE_MISSING:'in text for _,text in result.failures)
    verdict=dict(classification='expected-public-behavior-red' if expected else ('single-role-readonly-fixture-selection-passed' if result.wasSuccessful() else 'selection-not-passed'),tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),actual_native_or_production_acceptance=False,local_execution_claimed=False)
    (CFG.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));return 86 if result.errors or result.skipped or result.testsRun!=5 else (0 if result.wasSuccessful() else 1)
if __name__=='__main__':raise SystemExit(main())
