"""Direct-tool conflict and terminal-only failure reconciliation regression."""
import argparse,copy,hashlib,importlib.util,json,os,sqlite3,subprocess,sys,tempfile,unittest,uuid
from pathlib import Path
PACKAGE=Path(__file__).resolve().parent
CFG=None;N=None;R=None
BOOT='import sys,runpy;root=sys.argv.pop(1);sys.path.insert(0,root);runpy.run_path(root+"/portable_broker.py",run_name="__main__")'
def sha(b):return hashlib.sha256(b).hexdigest()
def canonical(v):return json.dumps(v,sort_keys=True,separators=(',',':'),allow_nan=False)
class Hold(RuntimeError):pass
TOOL=dict(type='function',name='lab_read_assignment',description='Read current fixed assignment',parameters=dict(type='object',properties={},additionalProperties=False))
class Regression(unittest.TestCase):
    def function(self,module,name,sentinel):
        f=getattr(module,name,None);self.assertTrue(callable(f),sentinel);return f
    def test_01_direct_catalog_and_conflicting_model_mode(self):
        f=self.function(N,'check_model_catalog','DIRECT_MODEL_CATALOG_GATE_MISSING')
        direct=dict(models=[dict(slug='gpt-6.1-sol',tool_mode='direct')]);f(copy.deepcopy(direct))
        cases=[dict(models=[dict(slug='gpt-6.1-sol',tool_mode='code_mode_only')]),
               dict(models=[dict(slug='gpt-6.1-sol',tool_mode='direct'),dict(slug='gpt-6.1-sol',tool_mode='code_mode_only')]),
               dict(models=[dict(slug='other',tool_mode='direct')]),dict(models=[dict(slug='gpt-6.1-sol')]),{}]
        for v in cases:
            with self.subTest(catalog=v):
                v['features']={'code_mode':False}
                with self.assertRaises(ValueError):f(v)
    def test_02_provider_top_level_and_input_additional_namespaces(self):
        f=self.function(N,'request_tool_inventory','PROVIDER_REQUEST_INVENTORY_PARSER_MISSING')
        self.assertEqual(f(dict(tools=[TOOL],input=[])),[dict(namespace=None,name='lab_read_assignment')])
        payload=dict(input=[dict(type='additional_tools',role='developer',tools=[dict(type='namespace',name='functions',tools=[TOOL])])])
        self.assertEqual(f(payload),[dict(namespace='functions',name='lab_read_assignment')])
        self.assertEqual(f(dict(input=[dict(type='additional_tools',role='developer',tools=[TOOL])])),[dict(namespace=None,name='lab_read_assignment')])
        with self.assertRaises(ValueError):f(dict(input=[dict(type='message',role='developer',content=[],additional_tools=dict(tools=[TOOL]))]))
        with self.assertRaises(ValueError):f(dict(input=[]))
    def test_03_actual_failure_and_unsafe_declarations_refused(self):
        f=self.function(N,'request_tool_inventory','PROVIDER_REQUEST_INVENTORY_PARSER_MISSING')
        sample=json.loads((PACKAGE/'failure-sample.json').read_text())
        definitions=sample['actual_incremental_declarations'][0]['definitions']
        namespace=[]
        for definition in definitions:
            namespace.append(dict(type='namespace',name=definition['name'],tools=[dict(type='function',name=name,parameters={}) for name in definition['members']]))
        payloads=[dict(tools=namespace,input=[]),dict(input=[dict(type='additional_tools',role='developer',tools=namespace)]),
                  dict(tools=[dict(type='function',name='exec')],input=[]),dict(tools=[dict(type='function',name='wait')],input=[]),
                  dict(tools=[dict(type='namespace',name='collaboration',tools=[TOOL])],input=[]),dict(tools=[dict(type='namespace',name='other',tools=[TOOL])],input=[]),dict(tools=[dict(type='function',name='other.lab_read_assignment')],input=[]),dict(input=[])]
        for payload in payloads:
            with self.subTest(payload=payload):
                with self.assertRaises(ValueError):f(payload)
    def fixture(self):
        # Actual unchanged public RoleStore, not a parallel reconciler.
        path=Path(tempfile.mkdtemp(prefix='failed-tool-',dir=CFG.runner_temp));path.chmod(0o700);self.addCleanup(__import__('shutil').rmtree,path)
        sys.path.insert(0,str(PACKAGE/'store-fixture-source'))
        spec=importlib.util.spec_from_file_location('_actual_fixture_runtime_store',PACKAGE/'store-fixture-source/runtime_store.py');runtime=importlib.util.module_from_spec(spec);spec.loader.exec_module(runtime)
        # native_role must inherit that exact public base for fixture construction.
        saved=sys.modules.get('runtime_store');sys.modules['runtime_store']=runtime
        try:
            sp=importlib.util.spec_from_file_location('_actual_fixture_role_store',PACKAGE/'store-fixture-source/native_role.py');role=importlib.util.module_from_spec(sp);sp.loader.exec_module(role)
        finally:
            if saved is not None:sys.modules['runtime_store']=saved
            else:sys.modules.pop('runtime_store',None)
        from portable_config import PrivateRuntimeBinding
        tid=str(uuid.uuid4());turn=str(uuid.uuid4());epoch='fixture-owned-epoch';auth=dict(client='fixture-owner',generation=1,token=os.urandom(32).hex())
        # Plain constructor data only; no fake authorization methods are supplied.
        binding=PrivateRuntimeBinding(canonical(dict(native=dict(expected_registered_session=tid),role=dict(name='one-lead',physical_cwd=str(path)),ledger=dict(directory=str(path),installation_sha256=sha(b'fixture-installation')))),sha(b'fixture-data-binding'),'b9ea684132b22e3cd0c0097a6edb7c37831d875658885a0453c7c4a22028a3c0')
        store=role.RoleStore(str(path),dict(binding=binding,pin=sha(b'fixture-installation'),role='one-lead',physical_cwd=str(path),runtime_directory=str(path),native_session=tid));self.addCleanup(store.db.close)
        assignment=dict(card=1,revision=1,role='one-lead',session_id=tid,physical_cwd=str(path),task='failed readonly fixture',A='a'*40,B='b'*40,H='c'*40)
        effect='d'*48
        with store.tx() as db:
            db.execute('update owner set client=?,generation=1,token_sha=?,paused=1 where id=1',(auth['client'],sha(auth['token'].encode())))
            db.execute("insert into tasks values(?,?,'active',1,null)",(1,canonical(assignment)))
            db.execute("insert into turns values(?,1,1,1,'active')",(turn,))
            db.execute("insert into role_registration values(1,?,'registered',1,?,null,null)",('e'*48,tid))
            db.execute("insert into native_dispatch values(?,?,1,1,1,?,'observed',?,?)",(effect,tid,sha(b'prompt'),epoch,turn))
            db.execute("insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,1,1,'fixture-native-turn','native-turn','{}','unknown',1)",(effect,))
        failed=copy.deepcopy(json.loads((PACKAGE/'failure-sample.json').read_text()));failed['thread_id']=tid;failed['turn_id']=turn
        args=dict(epoch=epoch,revision=1,thread_id=tid,turn_id=turn,failure_sha256=sha(canonical(failed).encode()))
        readback=dict(thread=dict(id=tid,cwd=str(path),turns=[dict(id=turn,status='completed',error=None,items=[dict(type='customToolCall',name='exec',status='failed')])]))
        return store,auth,epoch,args,readback,failed,effect
    def reconcile(self):
        self.assertIn('role.reconcile-failed-current',CFG.help_text,'FAILED_TOOL_RECONCILIATION_PUBLIC_COMMAND_MISSING')
        return self.function(R,'reconcile_failed_tool','FAILED_TOOL_RECONCILIATION_TRANSACTION_MISSING')
    def test_04_exact_completed_failed_tool_marks_business_failure_only(self):
        f=self.reconcile();store,auth,epoch,args,readback,failed,effect=self.fixture()
        before_effects=[tuple(r) for r in store.db.execute('select * from effects')];before_dispatch=[tuple(r) for r in store.db.execute('select * from native_dispatch')]
        value=f(store,auth,epoch,args,readback,failed)
        self.assertEqual(value.get('action'),'business-failed');self.assertEqual(value.get('turn_id'),args['turn_id'])
        self.assertEqual(store.db.execute('select state from turns where turn_id=?',(args['turn_id'],)).fetchone()[0],'terminal')
        self.assertEqual(before_effects,[tuple(r) for r in store.db.execute('select * from effects')]);self.assertEqual(before_dispatch,[tuple(r) for r in store.db.execute('select * from native_dispatch')])
        self.assertEqual(store.db.execute('select count(*) from native_tool_operations').fetchone()[0],0)
        self.assertEqual(store.db.execute("select count(*) from events where kind='single-role-readonly-task-business-failed'").fetchone()[0],1)
    def test_05_nonterminal_wrong_identity_or_owner_cannot_reconcile(self):
        f=self.reconcile()
        for mutation in ('in-progress','wrong-thread','wrong-turn','stale-epoch','stale-owner','unpaused','wrong-failure-hash'):
            with self.subTest(mutation=mutation):
                store,auth,epoch,args,readback,failed,effect=self.fixture()
                if mutation=='in-progress':readback['thread']['turns'][0]['status']='inProgress'
                elif mutation=='wrong-thread':readback['thread']['id']=str(uuid.uuid4())
                elif mutation=='wrong-turn':readback['thread']['turns'][0]['id']=str(uuid.uuid4())
                elif mutation=='stale-epoch':args['epoch']='old-epoch'
                elif mutation=='stale-owner':auth['generation']=0
                elif mutation=='unpaused':
                    with store.tx() as db:db.execute('update owner set paused=0 where id=1')
                else:args['failure_sha256']='0'*64
                before={t:[tuple(r) for r in store.db.execute('select * from '+t)] for t in ('owner','tasks','turns','effects','native_dispatch','native_tool_operations','events')}
                with self.assertRaises(ValueError):f(store,auth,epoch,args,readback,failed)
                self.assertEqual(before,{t:[tuple(r) for r in store.db.execute('select * from '+t)] for t in before})

def main():
    global CFG,N,R
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--runner-temp',required=True,type=Path);p.add_argument('--source-root',type=Path,default=PACKAGE/'baseline-source');p.add_argument('--evidence-dir',required=True,type=Path);p.add_argument('--parent-netns',required=True);p.add_argument('--source-closure-sha256',default='1e68b1fcbbdb3b2eaf4328e9f07a442af93fb5567413ae10553e39d6870f3b94');CFG=p.parse_args();CFG.source_root=CFG.source_root.resolve(strict=True);CFG.runner_temp=CFG.runner_temp.resolve(strict=True);CFG.evidence_dir=CFG.evidence_dir.resolve(strict=True)
    try:
        if any(CFG.evidence_dir.iterdir()) or sys.platform!='linux' or os.geteuid()==0 or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted' or os.readlink('/proc/self/ns/net')==CFG.parent_netns:raise Hold('fresh isolated hosted context required')
        raw=(CFG.source_root/'SOURCE-CLOSURE.json').read_bytes()
        if sha(raw)!=CFG.source_closure_sha256:raise Hold('external executable pin mismatch')
        for name,pin in json.loads(raw)['files_sha256'].items():
            if sha((CFG.source_root/name).read_bytes())!=pin:raise Hold('source dependency differs')
        sys.path.insert(0,str(CFG.source_root));import role_native;import single_role;N=role_native;R=single_role
        cli=[sys.executable,'-I','-S','-B','-c',BOOT,str(CFG.source_root),'--help'];help_result=subprocess.run(cli,env=dict(PATH='/usr/bin:/bin',HOME=str(CFG.runner_temp),PYTHONDONTWRITEBYTECODE='1'),capture_output=True,timeout=8)
        if help_result.returncode:raise Hold('public help unavailable')
        CFG.help_text=help_result.stdout.decode()
    except Exception:
        (CFG.evidence_dir/'verdict.json').write_text(json.dumps(dict(classification='capability-HOLD',tests=0)));return 86
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Regression));fail={t._testMethodName:text for t,text in result.failures};errors={t._testMethodName for t,_ in result.errors}
    expected=len(result.failures)==5 and not result.errors and all(any(s in text for s in ['DIRECT_MODEL_CATALOG_GATE_MISSING','PROVIDER_REQUEST_INVENTORY_PARSER_MISSING','FAILED_TOOL_RECONCILIATION_PUBLIC_COMMAND_MISSING']) for text in fail.values())
    facts=[dict(case=name,passed=name not in fail and name not in errors,status='error-HOLD' if name in errors else ('assertion-red' if name in fail else 'passed')) for name in unittest.defaultTestLoader.getTestCaseNames(Regression)]
    (CFG.evidence_dir/'case-observations.json').write_text(json.dumps(dict(public_help_exit=0,source_closure_sha256=CFG.source_closure_sha256,cases=facts,models_or_credentials_involved=False,actual_native_reconciliation_proven=False),indent=2)+chr(10))
    verdict=dict(classification='expected-missing-required-behavior-red' if expected else ('bounded-direct-and-store-reconciliation-regression-passed' if result.wasSuccessful() else 'selection-not-passed'),tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),production_or_native_authentication_proven=False)
    (CFG.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));return 86 if result.errors or result.skipped or result.testsRun!=5 else (0 if result.wasSuccessful() else 1)
if __name__=='__main__':raise SystemExit(main())
