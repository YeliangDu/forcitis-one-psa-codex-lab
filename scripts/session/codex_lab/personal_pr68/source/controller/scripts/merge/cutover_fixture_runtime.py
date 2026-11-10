"""Concrete test-owned runtime for provisional actual child driver. Hosted only."""
import base64
import contextlib
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import threading
from unittest.mock import patch

from locked_phase_setup import CONTEXTS,phase_manifest
from scenario_ledger import ScenarioLedger,encoded
from synthetic_inputs import build,adoption,ROLES
from ordinary_native_fixture import build as build_ordinary,native_response
from cutover_owned_receiver import ParentReceiver,need
from cutover_host_probe import HostProbe
from recovery_probe import RecoveryProbe

HERE=Path(__file__).resolve().parent


def private_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(encoded(value));path.chmod(0o600)


def bind_scenario(fixture):
    context=CONTEXTS.get(str(fixture['root']))
    need(context is not None,'E2E_PRE_OH_LOCKED_MATERIAL_SETUP_UNBOUND')
    need(context['test'].old==fixture['old_base'] and context['test'].candidate==fixture['candidate_head'],
         'E2E_RUNTIME_SUBJECT_NOT_TEST_OWNED')
    return ScenarioRuntime(context,fixture)


class ScenarioRuntime:
    def __init__(self,context,fixture):
        self.context,self.test,self.material=context,context['test'],context['material']
        self.root=Path(fixture['root']);self.fixture=fixture;self.fault=fixture['fault']
        self.host_probe=fixture.get('host_probe') or HostProbe(self.test)
        self.recovery_probe=fixture.get('recovery_probe') or RecoveryProbe(self.test,self.host_probe)
        self.paths={};self.manifests={}
        for phase,commit in [('old',fixture['old_base']),('new',fixture['candidate_head'])]:
            self.paths[phase],self.manifests[phase]=phase_manifest(context,phase,commit)
        tree=self.test.git('merge-tree','--write-tree',self.test.old,self.test.candidate).splitlines()[0]
        self.merge=self.test.git('commit-tree',tree,'-p',self.test.old,'-p',self.test.candidate,'-m','Synthetic protected cutover')
        self.ordinary_merge=None
        self.release_receipts={}
        self.payload=build(self)
        successor_health=adoption(self,self.payload['approval'])
        native=successor_health['health']
        self.successor_health_pins=dict(run_id=native['run']['id'],attempt=native['run']['run_attempt'],
                                       job_id=native['job']['id'],artifact_id=native['artifact']['id'])
        self.ordinary_approval,self.ordinary_pr,self.ordinary_static=build_ordinary(self)
        self.ledger=ScenarioLedger();self.allowed={};self.reads={}
        self.socket=self.root/'.test-owned-ipc.sock'
        self.request_file=self.root/'test-owned-request.json'
        self.proof_trace=self.root/'test-owned-proof-trace.json'
        self.request=self.make_request()
        private_json(self.request_file,self.request)
        self.install_private_approvals()
        self.write_proof_trace()

    def actual_main(self):
        return self.test.git('rev-parse','--verify','refs/heads/main')

    def source_paths(self,phase):return tuple(self.paths[phase])

    def source_root(self,phase,commit):
        destination=self.root/'.source-profiles'/phase
        if not destination.exists():
            destination.parent.mkdir(parents=True,exist_ok=True)
            self.test.git('worktree','add','--detach',str(destination),commit)
        need(self.test.git_at(destination,'rev-parse','HEAD')==commit,'E2E_PROFILE_WORKTREE_NOT_EXACT')
        for relative in self.paths[phase]:
            raw=self.test.git_bytes('show',commit+':'+relative)
            need((destination/relative).read_bytes()==raw,'E2E_PROFILE_SOURCE_BYTES_MOVED')
        if phase=='new':
            self.request['ordinary']['source_root']=str(destination)
            private_json(self.request_file,self.request)
        return destination

    def resource_scope(self,name):
        # Test-owned fixed material only; never call candidate read_scope or
        # infer trust from child/result values.
        approval=self.payload['approval']
        scope={key:approval[key] for key in ('repository','repository_id','number','base','head')}
        scope['resource']=name
        if name.startswith('baseline.') or name=='baseline_witness':
            pins=self.payload['baseline_pins']
            scope.update(source_commit=pins['workflow']['source_commit'],run_id=pins['run_id'],
                         attempt=pins['run_attempt'],job_id=pins['job_id'],
                         upload_step=pins['step_number'],artifact_id=pins['artifact_id'])
        elif name.startswith('candidate.') or name=='candidate_zip':
            pins=self.payload['candidate_pins']
            scope.update(source_commit=pins['independent_source'],run_id=pins['run_id'],
                         attempt=pins['run_attempt'],job_id=pins['aggregation_job_id'],
                         upload_step=pins['upload_step'],artifact_id=pins['artifact_id'])
        elif name=='preparation':
            scope.update(common_dir=str(self.fixture['common_dir']),
                         backup_path=str(self.test.dbpath.parent/'test-owned-v1.backup.sqlite3'),
                         reviewed_v1_sha256=self.fixture['reviewed_v1_sha256'])
        elif name=='merged':
            scope['merge_sha']=self.merge
        elif name=='successor_health':
            pins=self.successor_health_pins
            scope.update(merge_sha=self.merge,source_commit=self.merge,run_id=pins['run_id'],
                         attempt=pins['attempt'],job_id=pins['job_id'],artifact_id=pins['artifact_id'])
        else:
            need(name in ('reviews','registry'),'E2E_TEST_OWNED_RAW_RESOURCE_UNKNOWN')
        return scope

    def put(self,name,value,binary=False,roles=('old',),before=True):
        scope=self.resource_scope(name)
        record=self.ledger.freeze(name,scope,value,binary=binary)
        self.reads[name]=record
        self.allowed[name]=dict(scope=scope,roles=list(roles),before_protected_cas=before)
        return record

    def make_request(self):
        p=self.payload
        for name,value in p['baseline'].items():self.put('baseline.'+name,value)
        self.put('baseline_witness',p['witness'],binary=True)
        for name,value in p['candidate'].items():self.put('candidate.'+name,value)
        self.put('candidate_zip',p['zip'],binary=True)
        self.put('reviews',p['rows']);self.put('registry',p['registry'])
        self.put('preparation',self.preparation())
        self.put('merged',dict(repository=p['approval']['repository'],repository_id=1449415666,number=42,
            head=self.test.candidate,base=self.test.old,target='main',merged=True,merge_sha=self.merge,
            merge_parents=[self.test.old,self.test.candidate],target_tip=self.merge,target_contains_merge=True),
            roles=('old','successor'),before=False)
        self.put('successor_health',adoption(self,p['approval']),roles=('successor',),before=False)
        return dict(number=42,root=str(self.root),common_dir=str(self.fixture['common_dir']),socket=str(self.socket),
            successor_merge=self.merge,successor_health_pins=self.successor_health_pins,
            fault=self.fault,proof_trace=str(self.proof_trace),
            backup_path=str(self.test.dbpath.parent/'test-owned-v1.backup.sqlite3'),
            reviewed_v1_sha256=self.fixture['reviewed_v1_sha256'],source_paths=self.paths,reads=self.reads,
            expected_approval=p['approval'],baseline_pins=p['baseline_pins'],candidate_pins=p['candidate_pins'],
            independent_workflow=p['independent_workflow'],review_body_sha256=p['review_hashes'],
            ordinary=dict(number=61,card_number=62,expected_approval=self.ordinary_approval,source_root=str(self.root/'.source-profiles/new'),
                github_read=dict(key='ordinary.native',scope=dict(number=61,base=self.merge,head=self.ordinary_head))))

    def preparation(self):
        def identity(path):
            s=path.stat();return dict(device=s.st_dev,inode=s.st_ino,uid=s.st_uid)
        directory=self.test.dbpath.parent
        names=['control-upgrade.runner-lock','merge.queue.merge-lock','merge.queue.lock','merge.queue.heads-lock']
        return dict(version=1,kind='canonical-storage-preparation',intent_id='55555555-5555-4555-8555-555555555555',
            repository='YeliangDu/forcitis-one-psa-codex-lab',repository_id=1449415666,
            common_dir=str(self.fixture['common_dir']),common_identity=identity(Path(self.fixture['common_dir'])),
            queue_directory_identity=identity(directory),journal_identity=identity(self.test.dbpath),
            reviewed_v1_sha256=self.fixture['reviewed_v1_sha256'],backup_path=str(directory/'test-owned-v1.backup.sqlite3'),
            expected_user_version=0,expected_confirmed_rows=5,expected_fifo_sha256=hashlib.sha256(b'').hexdigest(),
            expected_heads_sha256=hashlib.sha256(b'').hexdigest(),lock_identities={n:identity(directory/n) if (directory/n).exists() else None for n in names},
            permitted_action='prepare-storage-only')

    def install_private_approvals(self):
        directory=self.test.dbpath.parent
        for name in ('upgrades','approvals'):(directory/name).mkdir(mode=0o700,exist_ok=True)
        (directory/'upgrades').chmod(0o700);(directory/'approvals').chmod(0o700)
        private_json(directory/'upgrades'/(self.payload['approval']['upgrade_id']+'.json'),self.payload['approval'])
        private_json(directory/'approvals/61.json',self.ordinary_approval)
        private_json(self.root/'.codex-lab/session-copy/runtime/roles.json',self.payload['registry'])

    def write_proof_trace(self):
        self.expected_trace=dict(repository_id=1449415666,base=self.test.old,head=self.test.candidate,source_commit=self.test.old,
            old_manifest=self.manifests['old']['digest'],new_manifest=self.manifests['new']['digest'],
            run_id=700,attempt=2,latest_run_id=700,latest_attempt=2,job_id=800,upload_step=4,artifact_id=900,
            zip_sha256=self.payload['candidate_pins']['zip_sha256'],receipt_sha256=self.payload['candidate_pins']['receipt_sha256'],
            lanes={name:'passed' for name in ('light','secret','shadow','frontend','backend')},
            reviews=[dict(id=row['id'],kind=kind,resident=role,head=self.test.candidate,verdict='pass',body_sha256=self.payload['review_hashes'][kind])
                for row,kind,role in zip(self.payload['rows'],['首轮','终审'],['one-design','one-test'])])

    def release_child(self,role,pid):
        # Readback only. The launch wrapper already registered and wrote G.
        receipt=self.release_receipts.get((role,pid))
        need(receipt is not None,'E2E_CHILD_RELEASE_RECEIPT_MISSING')
        proc,server,written=receipt
        need(role in self.host_probe.children and self.host_probe.children[role][0] is proc
             and proc.pid==pid,'E2E_CHILD_RELEASE_NOT_ACTUAL_HOST_HANDLE')
        need(server.peers.get(pid)==role and written==1,
             'E2E_CHILD_RELEASE_NOT_REGISTERED_SINGLE_BYTE')

    def receiver(self,host,recovery):return ReceiverSession(self,host,recovery)


def _diagnostic_token(value,limit=160):
    import re
    if not isinstance(value,str):return None
    value=value[:limit]
    return value if re.fullmatch(r'[A-Za-z0-9_.: /-]*',value) else '<non-code-text-omitted>'


def _emit_failed_receiver_diagnostic(session,known_failure=False):
    # Read-only unittest outcome observation; never changes result or authority.
    try:
        test=session.runtime.test
        outcome=getattr(test,'_outcome',None);result=getattr(outcome,'result',None)
        identity=test.id()
        failed=known_failure or (result is not None and any(item.id()==identity or item.id().startswith(identity+' ')
                   for item,_ in list(result.failures)+list(result.errors)))
        if not failed:return
        children=[]
        for role,(proc,profile) in list(session.host.children.items())[:3]:
            functions=[]
            try:
                with Path(profile).open('rb') as stream:raw=stream.read(65537)
                if len(raw)<=65536:
                    value=json.loads(raw)
                    for call in value.get('calls',[])[-16:]:
                        if isinstance(call,dict):functions.append(_diagnostic_token(call.get('function'),80))
            except Exception:pass
            summary=getattr(proc,'_test_owned_stdout_summary',None)
            children.append(dict(role=_diagnostic_token(role,32),returncode=proc.returncode,
                                 last_functions=functions,stdout_summary=summary))
        diagnostic=dict(kind='cutover-failure-diagnostic-only',test=identity[-240:],
            children=children,
            raw_keys=[_diagnostic_token(row.get('key'),100) for row in session.runtime.ledger.audit[-32:] if isinstance(row,dict)],
            ipc_stages=[dict(role=_diagnostic_token(row.get('role'),32),method=_diagnostic_token(row.get('method'),48),
                             key=_diagnostic_token(row.get('key'),100)) for row in session.server.audit[-24:] if isinstance(row,dict)],
            ipc_error_types=[_diagnostic_token(str(error).split(':',1)[0],64) for error in session.server.errors[-8:]])
        encoded=json.dumps(diagnostic,sort_keys=True,ensure_ascii=True)
        if len(encoded)>12288:
            diagnostic['raw_keys']=diagnostic['raw_keys'][-8:]
            diagnostic['ipc_stages']=diagnostic['ipc_stages'][-8:]
            encoded=json.dumps(diagnostic,sort_keys=True,ensure_ascii=True)
        print(encoded,file=sys.stderr,flush=True)
    except Exception:
        # Diagnostic unavailability cannot introduce a new failure or success.
        pass


def _observe_existing_communicate(proc):
    original=proc.communicate
    def communicate(*args,**kwargs):
        result=original(*args,**kwargs)
        try:
            stdout=result[0]
            if isinstance(stdout,bytes):stdout=stdout[-4096:].decode('utf-8',errors='replace')
            if isinstance(stdout,str):
                for line in reversed(stdout[-4096:].splitlines()):
                    try:value=json.loads(line)
                    except (ValueError,TypeError):continue
                    if isinstance(value,dict):
                        proc._test_owned_stdout_summary={key:_diagnostic_token(value.get(key),160) for key in ('action','reason')}
                        break
        except Exception:pass
        return result
    proc.communicate=communicate


class ReceiverSession:
    def __init__(self,runtime,host,recovery):
        self.runtime,self.host,self.recovery=runtime,host,recovery
        self.reaped=threading.Event();self.saved_launch=None

    def __enter__(self):
        runtime=self.runtime;session=self
        interrupted=runtime.fault in ('lost-response-restart','crash-after-send')
        class Server(ParentReceiver):
            def handle(server,role,request):
                if request.get('method')=='read' and request.get('arguments',{}).get('key')=='ordinary.native':
                    need(role=='successor','E2E_ORDINARY_RAW_WRONG_PROCESS')
                    scope=request['arguments']['scope']
                    value=native_response(runtime,scope)
                    token=json.dumps(scope,sort_keys=True)
                    server.audit.append(dict(role=role,method='ordinary-native-read',scope=scope,sha256=hashlib.sha256(value).hexdigest()))
                    return {'base64':base64.b64encode(value).decode()},True
                if request.get('method')=='ordinary-cas':
                    args=request.get('arguments')
                    need(role=='successor' and type(args)is dict and set(args)=={'intent','expected_base','merge_sha'},
                         'E2E_ORDINARY_THREE_FIELD_CAS_REQUIRED')
                    runtime.ordinary_cas(args,server)
                    return {'requested':True},True
                if request.get('method')=='protected-cas':
                    runtime.validate_raw_before_cas()
                    if interrupted:
                        result=super(Server,server).handle(role,request)
                        session.reaped.set()
                        return result
                return super(Server,server).handle(role,request)
        self.server=Server(runtime.socket,runtime.ledger,runtime.allowed,self.host,
            runtime.payload['approval'],runtime.merge,self.recovery if interrupted else None).start()
        self.runtime.test.addCleanup(_emit_failed_receiver_diagnostic,self)
        self.saved_launch=self.host.launch
        def launch(role,*args,**kwargs):
            gate,release=os.pipe()
            original=subprocess.Popen
            def guarded(argv,*positional,**options):
                if isinstance(argv,list) and any(str(x).endswith('cutover_profile_child.py') for x in argv):
                    wrapped=[sys.executable,'-B',str(HERE/'launch_barrier.py'),str(gate),json.dumps(argv)]
                    options['pass_fds']=tuple(options.get('pass_fds',()))+(gate,)
                    return original(wrapped,*positional,**options)
                return original(argv,*positional,**options)
            try:
                with patch.object(subprocess,'Popen',side_effect=guarded):
                    pid=self.saved_launch(role,*args,**kwargs)
                _observe_existing_communicate(self.host.children[role][0])
                self.server.register_peer(role,pid)
                # Fixed source also checks a private exact-PID ready file.
                # Publish once AFTER actual registration and BEFORE pipe release.
                ready_dir=runtime.root/'child-ready'
                ready_dir.mkdir(mode=0o700,exist_ok=True)
                need(not ready_dir.is_symlink() and ready_dir.resolve(strict=True)==ready_dir
                     and ready_dir.stat().st_uid==os.geteuid()
                     and ready_dir.stat().st_mode & 0o777==0o700,'E2E_CHILD_READY_DIRECTORY_UNSAFE')
                marker=ready_dir/(str(pid)+'.json')
                marker_raw=encoded(dict(pid=pid))
                fd=os.open(marker,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
                with os.fdopen(fd,'wb') as stream:
                    info=os.fstat(stream.fileno())
                    need(info.st_uid==os.geteuid() and info.st_nlink==1 and info.st_mode & 0o777==0o600,
                         'E2E_CHILD_READY_MARKER_UNSAFE')
                    need(stream.write(marker_raw)==len(marker_raw),'E2E_CHILD_READY_SHORT_WRITE')
                    stream.flush();os.fsync(stream.fileno())
                directory_fd=os.open(ready_dir,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
                try:os.fsync(directory_fd)
                finally:os.close(directory_fd)
                need((role,pid) not in runtime.release_receipts,'E2E_CHILD_GATE_ALREADY_RELEASED')
                written=os.write(release,b'G')
                need(written==1,'E2E_CHILD_GATE_SINGLE_BYTE_WRITE_FAILED')
                # Receipt is published ONLY after exact registration + real write.
                runtime.release_receipts[(role,pid)]=(self.host.children[role][0],self.server,written)
                return pid
            finally:
                os.close(gate);os.close(release)
        self.host.launch=launch
        return self

    def register_peer(self,role,pid):
        # Compatibility readback only: launch's pipe barrier already registered
        # the actual OS child before release. Never register or release again.
        need(role in self.host.children and self.host.children[role][0].pid == pid,
             'E2E_DRIVER_PEER_NOT_ACTUAL_HOST_CHILD')
        need(self.server.peers.get(pid) == role,
             'E2E_DRIVER_PEER_NOT_BARRIER_REGISTERED')

    def wait_for_old_reaped(self,timeout):
        import time
        deadline=time.monotonic()+timeout
        while not self.reaped.is_set():
            remaining=deadline-time.monotonic()
            if remaining<=0:
                _emit_failed_receiver_diagnostic(self,known_failure=True)
                need(False,'E2E_REAL_POST_CAS_OLD_REAP_NOT_OBSERVED')
            if self.reaped.wait(min(0.1,remaining)):
                return
            child=self.host.children.get('old')
            # Only a real exited known child with NO CAS effect can fail early.
            # Do not mark a graceful/failed exit as the controlled fault event.
            if child is not None and child[0].poll() is not None and self.host.cas_count==0:
                try:
                    child[0].communicate(timeout=1)
                except Exception:
                    pass  # Diagnostic capture cannot replace the same failure.
                _emit_failed_receiver_diagnostic(self,known_failure=True)
                need(False,'E2E_REAL_POST_CAS_OLD_REAP_NOT_OBSERVED')

    def __exit__(self,kind,value,trace):
        self.host.launch=self.saved_launch
        self.server.close()
        private_json(self.runtime.root/'test-owned-raw-audit.json',dict(reads=self.runtime.ledger.audit,ipc=self.server.audit,errors=self.server.errors))
        if kind is None:
            need(not self.server.errors,'E2E_IPC_RECEIVER_REFUSAL: '+repr(self.server.errors))
        # Cleanup actual children/worktrees remains registered on original test.


def _load_source_module(runtime,phase,name):
    root=runtime.source_root(phase,runtime.test.old if phase=='old' else runtime.merge)
    path=root/'scripts/merge'/(name+'.py')
    expected=runtime.test.git_bytes('show',(runtime.test.old if phase=='old' else runtime.merge)+':scripts/merge/'+name+'.py')
    need(path.read_bytes()==expected,'E2E_PARENT_VALIDATOR_SOURCE_NOT_PINNED')
    before=sys.path[:];sys.path.insert(0,str(path.parent))
    try:
        spec=importlib.util.spec_from_file_location('test_owned_'+phase+'_'+name,path)
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        return module
    finally:sys.path[:]=before


def _validate_raw_before_cas(self):
    # Reparse the exact ledger bytes actually served; no candidate success dict.
    def value(name):
        r=self.reads[name];return json.loads(self.ledger.read(r['key'],r['scope']))
    plan=_load_source_module(self,'old','mq_lab_control_plan')
    receipt=_load_source_module(self,'old','mq_lab_control_receipt')
    baseline={k:value('baseline.'+k) for k in ('repository','current_main','source','run','latest','jobs','artifacts')}
    record=self.reads['baseline_witness'];witness=self.ledger.read(record['key'],record['scope'])
    baseline['witness_raw']=witness;baseline['artifacts'][0].update(raw=witness,raw_sha256=hashlib.sha256(witness).hexdigest())
    need(plan.validate_independent_old_control_baseline(baseline,self.payload['baseline_pins'])['action']=='proposal',
         'E2E_PARENT_BASELINE_RAW_VALIDATION_HELD')
    candidate={k:value('candidate.'+k) for k in ('repository','current_pr','source_evidence','run','latest','jobs','artifacts','skip_cases')}
    record=self.reads['candidate_zip'];zipped=self.ledger.read(record['key'],record['scope'])
    candidate['download']=dict(artifact_id=900,run_id=700,attempt=2,zip_bytes=zipped)
    need(receipt.validate_public_candidate_proof(self.payload['candidate_pins'],candidate)['action']=='verified-readonly',
         'E2E_PARENT_CANDIDATE_RAW_VALIDATION_HELD')
    registry=value('registry')['roles'];need(len({r['id'] for r in registry})==len(registry),'E2E_PARENT_SHARED_REVIEW_ROLE')
    review_rows=value('reviews')
    for row,kind in zip(review_rows,['首轮','终审']):
        need(row['commit_id']==self.test.candidate and row['state'] in ('COMMENTED','APPROVED')
             and hashlib.sha256(row['body'].encode()).hexdigest()==self.payload['review_hashes'][kind],
             'E2E_PARENT_CURRENT_REVIEW_RAW_MISMATCH')
    from cutover_proof_trace import verify
    need(self.proof_trace.is_file() and not self.proof_trace.is_symlink(),'E2E_ACTUAL_CHILD_PROOF_TRACE_MISSING')
    verify(self.proof_trace.read_bytes(),self.expected_trace)
ScenarioRuntime.validate_raw_before_cas=_validate_raw_before_cas


@contextlib.contextmanager
def _isolated_new_source_imports(runtime):
    # Keep this scope active through actual ProductionPorts.verify + CAS checks,
    # not merely import. Never patch ROOT or accept a cached other-root class.
    source_root=runtime.source_root('new',runtime.merge)
    names={Path(path).stem for path in runtime.paths['new'] if path.endswith('.py')}
    for relative in runtime.paths['new']:
        target=source_root/relative
        expected=runtime.test.git_bytes('show',runtime.merge+':'+relative)
        need(target.read_bytes()==expected,'E2E_NEW_IMPORT_CLOSURE_BYTES_MOVED')
    before=dict(sys.modules);old_path=sys.path[:]
    def owned(name,module):
        if name in names:return True
        file=getattr(module,'__file__',None)
        if not isinstance(file,str):return False
        return Path(file).stem in names
    displaced={name:module for name,module in before.items() if owned(name,module)}
    for name in displaced:sys.modules.pop(name,None)
    sys.path[:]=[str(source_root/'scripts/merge'),str(source_root/'scripts/session/codex_lab')]+old_path
    try:
        yield source_root
        # Inspect every project module actually cached during verification.
        for name,module in list(sys.modules.items()):
            if owned(name,module):
                file=getattr(module,'__file__',None)
                need(isinstance(file,str),'E2E_NEW_IMPORT_WITHOUT_EXACT_FILE')
                path=Path(file).resolve(strict=True)
                need(path.is_relative_to(source_root),'E2E_PARENT_NORMAL_IMPORTED_OTHER_ROOT')
                relative=str(path.relative_to(source_root))
                need(relative in runtime.paths['new'],'E2E_PARENT_NORMAL_UNDECLARED_DEPENDENCY')
                need(path.read_bytes()==runtime.test.git_bytes('show',runtime.merge+':'+relative),
                     'E2E_PARENT_NORMAL_IMPORTED_BYTES_MOVED')
    finally:
        for name,module in list(sys.modules.items()):
            if owned(name,module):sys.modules.pop(name,None)
        sys.modules.update(displaced)
        sys.path[:]=old_path


def _ordinary_cas(self,args,server):
    from ordinary_boundary import OrdinaryBoundary,VerifiedNormalVerifier
    intent=dict(number=61,base=self.merge,head=self.ordinary_head,target='main')
    need(args['intent']==intent and args['expected_base']==self.merge,'E2E_ORDINARY_PINNED_INTENT_MOVED')
    merge=args['merge_sha'];need(type(merge)is str and len(merge)==40,'E2E_ORDINARY_MERGE_ID_INVALID')
    expected_tree=self.test.git('merge-tree','--write-tree',self.merge,self.ordinary_head).splitlines()[0]
    need(self.test.git('rev-parse',merge+'^{tree}')==expected_tree,'E2E_ORDINARY_MERGE_TREE_FORGED')
    with _isolated_new_source_imports(self):
        # Load actual ProductionPorts adapter from exact M; its read channel uses
        # direct test-owned ledger reads in the server thread (no recursive socket).
        mod=_load_source_module(self,'new','mq_lab_cutover_ordinary')
        channelmod=sys.modules.get('mq_lab_cutover_channel')
        need(channelmod is not None,'E2E_ACTUAL_ORDINARY_CHANNEL_CLASS_UNBOUND')
        owner=self
        class DirectReadChannel(channelmod.ScratchChannel):
            def __init__(channel):channel.root=owner.root
            def read(channel,key,scope):
                need(key=='ordinary.native','E2E_PARENT_NORMAL_PROOF_UNKNOWN_RAW_KEY')
                value=native_response(owner,scope)
                server.audit.append(dict(role='test-owned-normal-validator',method='ordinary-native-read',scope=scope,
                                         sha256=hashlib.sha256(value).hexdigest()))
                return value
        actual_ports=mod.make_ordinary_ports(self.request,DirectReadChannel())
        source_root=self.root/'.source-profiles/new'
        hashes={path:hashlib.sha256((source_root/path).read_bytes()).hexdigest() for path in self.paths['new']}
        normal=VerifiedNormalVerifier(source_root,hashes,self.ordinary_approval,actual_ports.github,lambda i:False)
        # No fake eligible callback: invoke entire real ProductionPorts.verify,
        # including source, issue/provenance/reviews/latest main health.
        normal.verify=lambda i,a,*unused:actual_ports.verify(i,a)
        boundary=getattr(self,'ordinary_boundary',None)
        if boundary is None:
            boundary=OrdinaryBoundary(self.test,intent,merge,normal);self.ordinary_boundary=boundary
        need(boundary.merge==merge,'E2E_ORDINARY_CHANGED_MERGE_ON_RETRY')
        boundary.cas(intent,args['expected_base'])
        self.ordinary_merge=merge
ScenarioRuntime.ordinary_cas=_ordinary_cas
