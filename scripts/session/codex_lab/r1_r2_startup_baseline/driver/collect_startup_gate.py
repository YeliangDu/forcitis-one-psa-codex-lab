"""Next stage of the same public Broker vertical selection: startup != commands.

Only scratch product Stores/CLI and synthetic prior first-turn evidence are used.
No existing private runtime or actual native/external effects are accessed.
"""
import argparse
import copy
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import signal
import socket
import sqlite3
import stat
import subprocess
import sys
import tempfile
import time
import uuid

SOURCES={'baseline': {'closure_sha256': '12e19610e39e2632ed1688928b1ae3f4be919fee0b4bc92de522f4664ffe2c4d', 'files_sha256': {'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': 'e4a5157b78d278e64ac2345ab7b35a3c22da132603b2b3b8ce5f2563b55445df', 'handoff_retire.py': '8118be38d49376a0138ef88e2ba2373b18e4a871ee02d4bd6553a983af5933b7', 'native_goal.py': '5856c23ca25734ed7df2ef4f737f472229b1c875f344e8796be9115d935d2c89', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': 'fe5617f8bdfaa3d170585c84fe3c57c9ec39b1b954da801634dcf51f2c45dc45', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': '062c69a93055b5bded567311b538865a8a8e5f08ec4b570be8d4dff60cd2e969', 'orphan_terminal.py': '77f8e0adb5b0b2a358d7e3a6e5467143adaf4eb959f9da94e041ddf097fa0177', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '588f4d81a2e17e557abc7390c20dbd92b46797c34a6d99ea5b5e0f62bc176bd7', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': 'fd2fe7bb9b3b19685e21c17aad5319122feffaab33b2d74ca66268a8b44c6928', 'runtime_broker.py': 'db842b7d777d07b9ef34f34079c1eb1f514317c19d503f442abb307f8e8c3639', 'runtime_store.py': '3990105d2eff4dafb40e01810734f065c1f81f9c9dec0e7ab49f6d95af8871db', 'warm_handoff.py': 'ec343e6391e15ec13a2be87c41a9bbbb2e8fdf353f55355d238625312c40bf93', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64', 'exact_scope.py': '9069901de3f64407bb947bf2d5bcba27c7087014a2f37993539a08527dc60a4e'}}, 'candidate': {'closure_sha256': 'b9ea684132b22e3cd0c0097a6edb7c37831d875658885a0453c7c4a22028a3c0', 'files_sha256': {'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': 'e4a5157b78d278e64ac2345ab7b35a3c22da132603b2b3b8ce5f2563b55445df', 'handoff_retire.py': '8118be38d49376a0138ef88e2ba2373b18e4a871ee02d4bd6553a983af5933b7', 'native_goal.py': '5856c23ca25734ed7df2ef4f737f472229b1c875f344e8796be9115d935d2c89', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': 'fe5617f8bdfaa3d170585c84fe3c57c9ec39b1b954da801634dcf51f2c45dc45', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': '062c69a93055b5bded567311b538865a8a8e5f08ec4b570be8d4dff60cd2e969', 'orphan_terminal.py': '77f8e0adb5b0b2a358d7e3a6e5467143adaf4eb959f9da94e041ddf097fa0177', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '149d2e35deaf6265e8f111ada401ddf17ad9a28a0b61628f9bb0eeb01229df4e', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': 'fd2fe7bb9b3b19685e21c17aad5319122feffaab33b2d74ca66268a8b44c6928', 'runtime_broker.py': '4a8cad2cfdd67ca22328c92e798eb8608a8dd2f36c6568894c1b27c80e0fbf7c', 'runtime_store.py': '3990105d2eff4dafb40e01810734f065c1f81f9c9dec0e7ab49f6d95af8871db', 'warm_handoff.py': 'ec343e6391e15ec13a2be87c41a9bbbb2e8fdf353f55355d238625312c40bf93', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64', 'exact_scope.py': '9069901de3f64407bb947bf2d5bcba27c7087014a2f37993539a08527dc60a4e', 'startup_authority.py': '9c44d81ece8d399126542e9753fbb6818ebeaccfd198c2c272b002950b124b23'}}}
FIXTURE_SHA='ca67e5d8e74408c0c87a7f30dbca8f6b2f26b8eec07deaaab3cc0a20f3df5b4f'
OPERATIONS=dict(storage='open-existing-schema-only',migrations='deny',initialization='deny',
                socket='exclusive-runtime-socket',startup_events='bounded-existing-constructor-audit')
class Hold(RuntimeError):pass

def sha(raw):return hashlib.sha256(raw).hexdigest()
def check(ok,label):
    if not ok:raise AssertionError(label)
def canonical(value):return json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False)
def identity(path):
    s=path.lstat()
    return dict(dev=s.st_dev,ino=s.st_ino,uid=s.st_uid,mode=stat.S_IMODE(s.st_mode),kind=stat.S_IFMT(s.st_mode),
                nlink=s.st_nlink,size=s.st_size,mtime_ns=s.st_mtime_ns,ctime_ns=s.st_ctime_ns)
def read_db(path):
    with sqlite3.connect(path.as_uri()+'?mode=ro&immutable=1',uri=True) as db:
        schema=db.execute("select type,name,tbl_name,sql from sqlite_schema where name!='sqlite_sequence' order by type,name,tbl_name").fetchall()
        counts={t:db.execute('select count(*) from '+t).fetchone()[0] for t in ('effects','native_dispatch','native_tool_operations','waits')}
        tasks=db.execute('select revision,body,phase from tasks order by revision').fetchall()
        return dict(schema_sha=sha(canonical([list(r) for r in schema]).encode()),version=db.execute('pragma user_version').fetchone()[0],
                    counts=counts,tasks=tasks,event_count=db.execute('select count(*) from events').fetchone()[0])
def helpers():
    path=Path(__file__).with_name('scope_fixture.py');raw=path.read_bytes()
    if sha(raw)!=FIXTURE_SHA:raise Hold('shared successful fixture bytes differ')
    spec=importlib.util.spec_from_file_location('_prior_scope_fixture',path);m=importlib.util.module_from_spec(spec)
    exec(compile(raw,str(path),'exec'),m.__dict__);return m,path

def run_case(source,variant,label,root,tracer,evidence):
    F,fixture=helpers();case=root/label;case.mkdir(mode=0o700)
    ledger=case/'ledger';ledger.mkdir(mode=0o700);lock=ledger/'broker.lock';lock.write_bytes(b'');lock.chmod(0o600)
    cwd=case/'work';cwd.mkdir(mode=0o700);ep=case/'endpoint';ep.mkdir(mode=0o700);ap=case/'alias';ap.mkdir(mode=0o700)
    selected=SOURCES[variant];source_sha=selected['closure_sha256'];sid=str(uuid.uuid4());auth=dict(client='scratch-owner',generation=1,token=os.urandom(24).hex())
    current=dict(card=1,revision=1,role='one-lead',session_id=sid,physical_cwd=str(cwd),task='synthetic fenced prior task',A=sha(b'A')[:40],B=sha(b'B')[:40],H=sha(b'H')[:40])
    future=dict(current,revision=2,task='independently selected next task',A=sha(b'next-A')[:40],H=sha(b'next-H')[:40])
    env=dict(PATH='/usr/bin:/bin',HOME=str(case),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1')
    def private(path,value):path.write_text(json.dumps(value));path.chmod(0o600);return sha(path.read_bytes())
    with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as native_fixture:
        target=ep/'s';native_fixture.bind(str(target));target.chmod(0o600);alias=ap/'s';alias.symlink_to(target)
        physical=Path('/usr/bin/false').resolve();proxy=case/'proxy';proxy.symlink_to(physical)
        old=dict(native_endpoint=str(alias),native_session=sid,physical_cwd=str(cwd),role='one-lead',proxy_binary=str(proxy),gh_binary='/usr/bin/false',runtime_directory=str(ledger))
        oldpath=case/'installation.json';installed=private(oldpath,old)
        scope=dict(kind='lab-runtime-exact-actions/2',broker_installation_sha256=installed,source_closure_sha256=source_sha,
            actor=dict(kind='coordinator-owner',owner=dict(client=auth['client'],generation=1),role='one-lead',native_session=sid),
            assignment=current,task_phase='fenced',capability_epoch=2,grants=[dict(grant_id='only-next',command='assignment.publish',arguments=dict(assignment=future),target=dict(kind='next-paused-assignment',assignment=future,broker_paused=True))])
        scopepath=case/'commands.json';scopepin=private(scopepath,scope)
        config=dict(schema='lab-private-runtime-binding/1',deployment_label='scratch-startup',source_closure_sha256=source_sha,
            ledger=dict(directory=str(ledger),installation_sha256=installed),role=dict(name='one-lead',physical_cwd=str(cwd)),
            native=dict(expected_registered_session=sid,endpoint=dict(alias=F.record(alias),alias_parent=F.record(ap),target=F.record(target),target_parent=F.record(ep),link_text=str(target)),
                        proxy=dict(path=str(physical),sha256=sha(physical.read_bytes())),engine_epoch_reference=None),
            evidence_references={'broker-installation':dict(kind='authorization',path=str(oldpath),sha256=installed)})
        if label!='positive-no-command-scope':config['evidence_references']['runtime-actions']=dict(kind='authorization',path=str(scopepath),sha256=scopepin)
        binding=case/'binding.json';bindingpin=private(binding,config)
        setup=case/'seed.json';private(setup,dict(source_sha=source_sha,binding_path=str(binding),binding_sha=bindingpin,auth=auth,current=current))
        seed=subprocess.run([sys.executable,'-I','-S','-B',str(fixture),'--seed',str(setup),'--source-root',str(source)],env=env,cwd=case,capture_output=True,timeout=15)
        if seed.returncode:raise Hold('successful prior seed fixture unavailable; no startup assertion reached')
        database=ledger/'runtime.sqlite3'
        if label=='incomplete-schema':
            # Explicitly invalid legacy fixture BEFORE startup baseline/pinning.
            with sqlite3.connect(database) as db:db.execute('drop table checkpoint_wait_control')
        before_db=read_db(database);before_bytes=sha(database.read_bytes());before_lock=identity(lock);before_db_id=identity(database)
        if label not in ('baseline-no-startup','missing-startup'):
            startup=dict(kind='lab-existing-runtime-startup/1',source_closure_sha256=source_sha,ledger_installation_sha256=installed,
                registered_session=sid,role='one-lead',physical_cwd=str(cwd),runtime_directory=str(ledger),
                runtime_identity=identity(ledger),lock_identity=identity(lock),database_identity=identity(database),
                schema_sha256=before_db['schema_sha'],user_version=before_db['version'],
                owner=dict(client=auth['client'],generation=1,paused=True),operations=OPERATIONS)
            if label=='wrong-startup-subject':startup['registered_session']=str(uuid.uuid4())
            path=case/'startup.json';pin=private(path,startup)
            config['evidence_references']['runtime-startup']=dict(kind='authorization',path=str(path),sha256='0'*64 if label=='wrong-startup-pin' else pin)
            bindingpin=private(binding,config)
        busy=None
        if label=='busy-lock':busy=lock.open('rb');fcntl.flock(busy,fcntl.LOCK_EX|fcntl.LOCK_NB)
        trace=case/'startup.trace';stdout=case/'serve.stdout';stderr=case/'serve.stderr'
        args=F.public_args(source,'serve',binding,bindingpin,source_sha)
        command=[tracer,'-f','-qq','-s','512','-e','trace=openat,openat2,execve,execveat,connect,bind,listen','-o',str(trace),*args]
        replies=[];ready=False
        with stdout.open('wb') as output,stderr.open('wb') as errors:
            process=subprocess.Popen(command,cwd=case,env=env,stdout=output,stderr=errors,start_new_session=True)
            try:
                deadline=time.monotonic()+8
                while process.poll() is None and not (ledger/'broker.sock').exists() and time.monotonic()<deadline:time.sleep(.02)
                ready=(ledger/'broker.sock').exists()
                def call(request):
                    path=case/('request-'+str(len(replies))+'.json');private(path,request)
                    callargv=F.public_args(source,'call',binding,bindingpin,source_sha)+['--request',str(path)]
                    actual=subprocess.run(callargv,cwd=case,env=env,capture_output=True,timeout=10)
                    try:value=json.loads(actual.stdout)
                    except ValueError:raise Hold('public call did not produce JSON')
                    replies.append(dict(command=request['command'],argv=callargv,exit=actual.returncode,result=value))
                    return value
                positive=label.startswith('positive-') or variant=='baseline'
                if positive and ready:
                    if variant=='baseline':
                        observed=call(dict(command='inspect',arguments={},auth=auth))
                        check('tasks' in observed and 'effects' in observed,'UNAUTHORIZED_SERVE_NOT_REAL_STORE')
                    else:
                        request=dict(command='assignment.publish',arguments=dict(assignment=copy.deepcopy(future)),auth=auth)
                        if label=='positive-no-command-scope':
                            observed=call(request);check(observed.get('action')=='hold','STARTUP_GRANTED_COMMAND_AUTHORITY')
                        else:
                            bad=copy.deepcopy(request);bad['arguments']['assignment']['A']=sha(b'not-approved')[:40]
                            check(call(bad).get('action')=='hold','STARTUP_BYPASSED_EXACT_TARGET')
                            check(read_db(database)['tasks']==before_db['tasks'],'BAD_TARGET_CHANGED_TASKS')
                            observed=call(request);check(observed.get('action')=='published','EXACT_AUTHORIZED_TARGET_DID_NOT_PUBLISH')
                            check(json.loads(read_db(database)['tasks'][-1][1])==future,'PUBLISHED_WRONG_TARGET')
                elif positive:raise Hold('actual public startup never reached Store/socket')
                elif process.poll() is None and not ready:raise Hold('negative startup did not finish bounded refusal')
            finally:
                if process.poll() is None:
                    os.killpg(process.pid,signal.SIGTERM)
                    try:process.wait(timeout=5)
                    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=5);raise Hold('startup process group did not stop')
                if busy:fcntl.flock(busy,fcntl.LOCK_UN);busy.close()
        if not trace.exists() or trace.stat().st_size>16*1024*1024:raise Hold('bounded startup syscall trace missing')
        raw=trace.read_bytes();lines=raw.decode(errors='replace').splitlines()
        opened=[line for line in lines if str(database) in line and ('openat(' in line or 'openat2(' in line) and ('O_RDWR' in line or 'O_WRONLY' in line)]
        bound=[line for line in lines if 'bind(' in line and 'broker.sock' in line]
        if not any('execve(' in line and ' = 0' in line for line in lines):raise Hold('tracing failed before product entry')
        (evidence/(label+'.syscalls.log')).write_bytes(raw)
        after_db=read_db(database)
        check(identity(lock)==before_lock,'ORIGINAL_STARTUP_LOCK_IDENTITY_CHANGED')
        check(after_db['counts']==before_db['counts'],'STARTUP_CREATED_NEW_EFFECT_OR_NATIVE_WORK')
        check(after_db['schema_sha']==before_db['schema_sha'],'STARTUP_INITIALIZED_OR_MIGRATED_SCHEMA')
        if variant=='baseline':
            if not (ready and opened and bound and after_db['event_count']>before_db['event_count']):raise Hold('old public serve did not prove writable Store startup; not red')
            classification='baseline-actual-unauthorized-startup-reached'
        elif label.startswith('positive-'):
            check(ready and opened and bound,'AUTHORIZED_STARTUP_NOT_ACTUALLY_REACHED')
            if label=='positive-no-command-scope':check(after_db['tasks']==before_db['tasks'],'NO_COMMAND_SCOPE_CHANGED_TASKS')
            classification='authorized-startup-command-boundary-observed'
        else:
            check(not ready and not opened and not bound,'UNAUTHORIZED_STARTUP_OPENED_WRITABLE_STORE_OR_SOCKET')
            check(sha(database.read_bytes())==before_bytes and identity(database)==before_db_id,'REFUSED_STARTUP_CHANGED_LEDGER')
            check(process.returncode==78,'REFUSED_STARTUP_NOT_HOLD')
            try:refusal=json.loads(stdout.read_bytes())
            except ValueError:raise Hold('refusal output lacks actual public JSON')
            check(refusal.get('action')=='hold' and refusal.get('execution_authority') is False,'REFUSED_STARTUP_CLAIMED_AUTHORITY')
            classification='refused-before-writable-Store-and-socket'
        return dict(label=label,classification=classification,source_closure_sha256=source_sha,serve_argv=args,
                    actual_socket_reached=ready,writable_database_open_count=len(opened),socket_bind_count=len(bound),
                    actual_new_effects_or_native_work=False,schema_unchanged=True,original_lock_identity_unchanged=True,
                    before_database_sha256=before_bytes,after_database_sha256=sha(database.read_bytes()),
                    startup_audit_event_delta=after_db['event_count']-before_db['event_count'],
                    writable_database_open_lines=opened,broker_socket_bind_lines=bound,
                    raw_stdout_sha256=sha(stdout.read_bytes()),raw_stderr_sha256=sha(stderr.read_bytes()),
                    replies=replies,raw_trace_sha256=sha(raw))

def main():
    parser=argparse.ArgumentParser(allow_abbrev=False);parser.add_argument('--source-root',required=True,type=Path);parser.add_argument('--variant',required=True,choices=('baseline','candidate'));parser.add_argument('--evidence-dir',required=True,type=Path);parser.add_argument('--parent-netns',required=True);args=parser.parse_args()
    evidence=args.evidence_dir.resolve(strict=True)
    if any(evidence.iterdir()):raise Hold('fresh evidence directory required')
    results=[]
    try:
        if sys.platform!='linux' or os.geteuid()==0 or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':raise Hold('unprivileged hosted Linux required')
        namespace=os.readlink('/proc/self/ns/net')
        if namespace==args.parent_netns or 'NoNewPrivs:'+chr(9)+'1' not in Path('/proc/self/status').read_text():raise Hold('private namespace/no-new-privileges required')
        if any(os.environ.get(k) for k in ('GH_TOKEN','GITHUB_TOKEN','GH_CONFIG_DIR','SSH_AUTH_SOCK','OPENAI_API_KEY')):raise Hold('credential environment refused')
        tracer=next((p for p in ('/usr/bin/strace','/bin/strace') if Path(p).is_file()),None);ip=next((p for p in ('/usr/sbin/ip','/usr/bin/ip') if Path(p).is_file()),None)
        if tracer is None or ip is None:raise Hold('fixed-path strace/ip unavailable')
        links=subprocess.check_output([ip,'-o','link','show'],text=True,env={'PATH':'/usr/bin:/bin'},timeout=5)
        if [line.split(':',2)[1].strip().split('@',1)[0] for line in links.splitlines() if line.strip()]!=['lo']:raise Hold('only loopback allowed')
        source=args.source_root.resolve(strict=True);selected=SOURCES[args.variant]
        if sha((source/'SOURCE-CLOSURE.json').read_bytes())!=selected['closure_sha256']:raise Hold('source closure drift')
        for n,pin in selected['files_sha256'].items():
            if sha((source/n).read_bytes())!=pin:raise Hold('source file drift')
        labels=['baseline-no-startup'] if args.variant=='baseline' else ['missing-startup','wrong-startup-pin','wrong-startup-subject','incomplete-schema','busy-lock','positive-no-command-scope','positive-exact-target']
        with tempfile.TemporaryDirectory(prefix='sg-',dir='/tmp') as temporary:
            root=Path(temporary).resolve();root.chmod(0o700)
            for label in labels:
                results.append(run_case(source,args.variant,label,root,tracer,evidence));(evidence/'observations.json').write_text(json.dumps(results,indent=2)+chr(10))
        if os.readlink('/proc/self/ns/net')!=namespace:raise Hold('namespace changed')
        if args.variant=='baseline':raise AssertionError('STARTUP_WITHOUT_INDEPENDENT_AUTHORITY_OPENED_WRITABLE_STORE')
        verdict=dict(classification='startup-and-command-authorities-remain-independent-selection-passed',overall_vertical_pass=False);code=0
    except AssertionError as error:verdict=dict(classification='reached-startup-behavior-assertion-red',reason=str(error),overall_vertical_pass=False);code=1
    except Exception as error:verdict=dict(classification='capability-HOLD',reason=type(error).__name__+': '+str(error)[:900],overall_vertical_pass=False);code=86
    (evidence/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));print(json.dumps(verdict));return code

if __name__=='__main__':raise SystemExit(main())
