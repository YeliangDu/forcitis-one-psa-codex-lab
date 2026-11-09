"""One exact-scope hosted selection through real public serve/call.

Synthetic fixture construction only; never a substitute authorizer or runtime.
Actual Store publish must be witnessed before any baseline assertion-red claim.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import socket
import sqlite3
import subprocess
import sys
import tempfile
import time
import uuid

SOURCES = {'baseline': {'closure_sha256': '60dd413cea142ad972b1c8acdbf6dd0de442c0eaf9c18fd41e7482a5310ea70b', 'files_sha256': {'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': 'e4a5157b78d278e64ac2345ab7b35a3c22da132603b2b3b8ce5f2563b55445df', 'handoff_retire.py': '8118be38d49376a0138ef88e2ba2373b18e4a871ee02d4bd6553a983af5933b7', 'native_goal.py': '5856c23ca25734ed7df2ef4f737f472229b1c875f344e8796be9115d935d2c89', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': 'fe5617f8bdfaa3d170585c84fe3c57c9ec39b1b954da801634dcf51f2c45dc45', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': '062c69a93055b5bded567311b538865a8a8e5f08ec4b570be8d4dff60cd2e969', 'orphan_terminal.py': '77f8e0adb5b0b2a358d7e3a6e5467143adaf4eb959f9da94e041ddf097fa0177', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '6101afe0e2522988b3c652f91a88b06082506f7cc4f323656ff51b45407449b0', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': 'fd2fe7bb9b3b19685e21c17aad5319122feffaab33b2d74ca66268a8b44c6928', 'runtime_broker.py': 'db842b7d777d07b9ef34f34079c1eb1f514317c19d503f442abb307f8e8c3639', 'runtime_store.py': '3990105d2eff4dafb40e01810734f065c1f81f9c9dec0e7ab49f6d95af8871db', 'warm_handoff.py': 'ec343e6391e15ec13a2be87c41a9bbbb2e8fdf353f55355d238625312c40bf93', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64'}}, 'candidate': {'closure_sha256': '12e19610e39e2632ed1688928b1ae3f4be919fee0b4bc92de522f4664ffe2c4d', 'files_sha256': {'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': 'e4a5157b78d278e64ac2345ab7b35a3c22da132603b2b3b8ce5f2563b55445df', 'handoff_retire.py': '8118be38d49376a0138ef88e2ba2373b18e4a871ee02d4bd6553a983af5933b7', 'native_goal.py': '5856c23ca25734ed7df2ef4f737f472229b1c875f344e8796be9115d935d2c89', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': 'fe5617f8bdfaa3d170585c84fe3c57c9ec39b1b954da801634dcf51f2c45dc45', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': '062c69a93055b5bded567311b538865a8a8e5f08ec4b570be8d4dff60cd2e969', 'orphan_terminal.py': '77f8e0adb5b0b2a358d7e3a6e5467143adaf4eb959f9da94e041ddf097fa0177', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '588f4d81a2e17e557abc7390c20dbd92b46797c34a6d99ea5b5e0f62bc176bd7', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': 'fd2fe7bb9b3b19685e21c17aad5319122feffaab33b2d74ca66268a8b44c6928', 'runtime_broker.py': 'db842b7d777d07b9ef34f34079c1eb1f514317c19d503f442abb307f8e8c3639', 'runtime_store.py': '3990105d2eff4dafb40e01810734f065c1f81f9c9dec0e7ab49f6d95af8871db', 'warm_handoff.py': 'ec343e6391e15ec13a2be87c41a9bbbb2e8fdf353f55355d238625312c40bf93', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64', 'exact_scope.py': '9069901de3f64407bb947bf2d5bcba27c7087014a2f37993539a08527dc60a4e'}}}
SCENARIO_SHA256 = '7c778e9bc57f56bb187b1355131816c7e30f553af9c5f6c997a6c1cd4e602b48'
BOOTSTRAP = 'import sys,runpy;root=sys.argv.pop(1);sys.path.insert(0,root);runpy.run_path(root+"/portable_broker.py",run_name="__main__")'

class Hold(RuntimeError):
    pass

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def canonical(value):
    return json.dumps(value,sort_keys=True,separators=(',',':'))

def record(path):
    s=path.lstat()
    import stat
    return dict(path=str(path),dev=s.st_dev,ino=s.st_ino,uid=s.st_uid,mode=stat.S_IMODE(s.st_mode),kind=stat.S_IFMT(s.st_mode))

def seed(args):
    # Product store creates its real schema; SQL writes below are authorized
    # synthetic fixture setup, not claims of a native authenticated owner.
    sys.path.insert(0,str(args.source_root))
    import portable_broker as entry
    from portable_config import load_private_binding
    bundle=json.loads(args.seed.read_text())
    entry.verify_source(str(args.source_root/'SOURCE-CLOSURE.json'),bundle['source_sha'])
    binding=load_private_binding(str(bundle['binding_path']),bundle['binding_sha'],bundle['source_sha'])
    runtime,cfg,broker_type,optional=entry.build_composition(binding)
    store=runtime.RoleStore(cfg['runtime_directory'],cfg)
    try:
        with store.tx() as db:
            auth=bundle['auth'];current=bundle['current']
            db.execute('update owner set generation=?,client=?,token_sha=?,paused=1 where id=1',
                       (auth['generation'],auth['client'],sha(auth['token'].encode())))
            db.execute('insert into tasks(revision,body,phase,capability_epoch,fence_seq) values(?,?,?,?,?)',
                       (current['revision'],canonical(current),'fenced',2,0))
            db.execute('insert into role_registration(id,effect_id,phase,generation,session_id,first_turn_id,readback_sha256) values(1,?,?,?,?,null,null)',
                       ('f'*48,'stable',auth['generation'],current['session_id']))
    finally:
        store.db.close()
    return 0

def public_args(source,command,binding,binding_sha,source_sha):
    return [sys.executable,'-I','-S','-B','-c',BOOTSTRAP,str(source),command,
            '--source-closure',str(source/'SOURCE-CLOSURE.json'),'--source-closure-sha256',source_sha,
            '--private-binding',str(binding),'--private-binding-sha256',binding_sha]

def database_snapshot(path):
    with sqlite3.connect(path.as_uri()+'?mode=ro&immutable=1',uri=True) as db:
        tasks=db.execute('select revision,body,phase,capability_epoch,fence_seq from tasks order by revision').fetchall()
        counts={t:db.execute('select count(*) from '+t).fetchone()[0] for t in ('effects','native_dispatch','native_tool_operations','waits')}
    return dict(tasks=tasks,counts=counts)

def run_case(source,variant,label,root):
    case=root/label;case.mkdir(mode=0o700)
    ledger=case/'ledger';ledger.mkdir(mode=0o700)
    (ledger/'broker.lock').write_bytes(b'');(ledger/'broker.lock').chmod(0o600)
    work=case/'work';work.mkdir(mode=0o700)
    endpoint=case/'ep';endpoint.mkdir(mode=0o700)
    alias_parent=case/'alias';alias_parent.mkdir(mode=0o700)
    source_sha=SOURCES[variant]['closure_sha256']
    sid=str(uuid.uuid4());auth=dict(client='scratch-owner',generation=1,token=os.urandom(24).hex())
    current=dict(card=1,revision=1,role='one-lead',session_id=sid,physical_cwd=str(work),task='synthetic current task',
                 A=sha(b'current-A')[:40],B=sha(b'current-B')[:40],H=sha(b'current-H')[:40])
    future=dict(current,revision=2,task='independently selected exact next task',A=sha(b'next-A')[:40],H=sha(b'next-H')[:40])
    request=dict(command='assignment.publish',arguments=dict(assignment=copy.deepcopy(future)),auth=copy.deepcopy(auth))
    env=dict(PATH='/usr/bin:/bin',HOME=str(case),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1')
    def private(path,value):
        path.write_text(json.dumps(value));path.chmod(0o600);return sha(path.read_bytes())
    with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as fixture:
        target=endpoint/'s';fixture.bind(str(target));target.chmod(0o600)
        alias=alias_parent/'s';alias.symlink_to(target)
        physical_proxy=Path('/usr/bin/false').resolve(strict=True);proxy_alias=case/'proxy';proxy_alias.symlink_to(physical_proxy)
        legacy=dict(native_endpoint=str(alias),native_session=sid,physical_cwd=str(work),role='one-lead',
                    proxy_binary=str(proxy_alias),gh_binary='/usr/bin/false',runtime_directory=str(ledger))
        old_path=case/'installation.json';installed=private(old_path,legacy)
        if variant=='baseline':
            scope=dict(kind='lab-runtime-actions/1',broker_installation_sha256=installed,source_closure_sha256=source_sha,
                       session_id=sid,role='one-lead',physical_cwd=str(work),owner=dict(client=auth['client'],generation=1),
                       revision=1,card=1,commands=['assignment.publish'])
        else:
            scope=dict(kind='lab-runtime-exact-actions/2',broker_installation_sha256=installed,source_closure_sha256=source_sha,
                       actor=dict(kind='coordinator-owner',owner=dict(client=auth['client'],generation=1),role='one-lead',native_session=sid),
                       assignment=copy.deepcopy(current),task_phase='fenced',capability_epoch=2,
                       grants=[dict(grant_id='only-exact-next',command='assignment.publish',arguments=dict(assignment=copy.deepcopy(future)),
                                    target=dict(kind='next-paused-assignment',assignment=copy.deepcopy(future),broker_paused=True))])
        if label=='current-only-no-target' and variant=='candidate':
            scope=dict(kind='lab-runtime-actions/1',broker_installation_sha256=installed,source_closure_sha256=source_sha,
                       session_id=sid,role='one-lead',physical_cwd=str(work),owner=dict(client=auth['client'],generation=1),revision=1,card=1,commands=['assignment.publish'])
        elif label.startswith('changed-current-'):
            scope['assignment'][label.rsplit('-',1)[1]]=sha(label.encode())[:40]
        elif label.startswith('changed-target-'):
            field=label.rsplit('-',1)[1];scope['grants'][0]['target']['assignment'][field]=3 if field=='revision' else sha(label.encode())[:40]
        elif label=='changed-command-args':request['arguments']['assignment']['task']='not approved'
        elif label=='stale-owner':request['auth']['generation']=0
        elif label=='wildcard':scope['grants'][0]['command']='assignment.*'
        elif label=='missing-target':del scope['grants'][0]['target']
        scope_path=case/'scope.json';scope_sha=private(scope_path,scope)
        cfg=dict(schema='lab-private-runtime-binding/1',deployment_label='scratch-scope',source_closure_sha256=source_sha,
            ledger=dict(directory=str(ledger),installation_sha256=installed),role=dict(name='one-lead',physical_cwd=str(work)),
            native=dict(expected_registered_session=sid,endpoint=dict(alias=record(alias),alias_parent=record(alias_parent),target=record(target),target_parent=record(endpoint),link_text=str(target)),
                        proxy=dict(path=str(physical_proxy),sha256=sha(physical_proxy.read_bytes())),engine_epoch_reference=None),
            evidence_references={'broker-installation':dict(kind='authorization',path=str(old_path),sha256=installed),
                                 'runtime-actions':dict(kind='authorization',path=str(scope_path),sha256='0'*64 if label=='missing-pin' else scope_sha)})
        binding=case/'binding.json';binding_sha=private(binding,cfg)
        seed_path=case/'seed.json';private(seed_path,dict(source_sha=source_sha,binding_path=str(binding),binding_sha=binding_sha,auth=auth,current=current))
        seeded=subprocess.run([sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),'--seed',str(seed_path),'--source-root',str(source)],
                              cwd=case,env=env,capture_output=True,timeout=10)
        if seeded.returncode:raise Hold('actual product schema fixture could not be constructed')
        before=database_snapshot(ledger/'runtime.sqlite3')
        server=subprocess.Popen(public_args(source,'serve',binding,binding_sha,source_sha),cwd=case,env=env,
                                stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
        try:
            end=time.monotonic()+8
            while not (ledger/'broker.sock').exists() and server.poll() is None and time.monotonic()<end:time.sleep(.02)
            if server.poll() is not None or not (ledger/'broker.sock').exists():raise Hold('real public serve did not become ready')
            req_path=case/'request.json';private(req_path,request)
            argv=public_args(source,'call',binding,binding_sha,source_sha)+['--request',str(req_path)]
            response=subprocess.run(argv,cwd=case,env=env,capture_output=True,timeout=10)
            try:result=json.loads(response.stdout)
            except ValueError:raise Hold('public call produced no bounded JSON')
            after=database_snapshot(ledger/'runtime.sqlite3')
            if before['counts']!=after['counts']:raise AssertionError('FAILED_SCOPE_CREATED_EFFECT_OR_NATIVE_STATE')
            published=result.get('action')=='published' and len(after['tasks'])==2 and json.loads(after['tasks'][-1][1])==request['arguments']['assignment']
            return dict(variant=variant,label=label,public_serve_started=True,actual_call_argv=argv,
                        public_call_exit=response.returncode,response=result,stdout_sha256=sha(response.stdout),stderr_sha256=sha(response.stderr),
                        actual_Store_publish_observed=published,tasks_unchanged=before['tasks']==after['tasks'],
                        effects_native_wait_counts_unchanged=True,after_counts=after['counts'])
        finally:
            if server.poll() is None:server.terminate()
            try:server.wait(timeout=5)
            except subprocess.TimeoutExpired:server.kill();server.wait(timeout=5);raise Hold('scratch public serve did not stop')

def main():
    parser=argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--source-root',type=Path,required=True)
    parser.add_argument('--variant',choices=('baseline','candidate'))
    parser.add_argument('--evidence-dir',type=Path)
    parser.add_argument('--parent-netns')
    parser.add_argument('--seed',type=Path)
    args=parser.parse_args()
    if args.seed:return seed(args)
    evidence=args.evidence_dir.resolve(strict=True)
    if any(evidence.iterdir()):raise Hold('fresh evidence directory required')
    results=[]
    try:
        if sys.platform!='linux' or os.geteuid()==0 or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':raise Hold('non-root hosted Linux required')
        if os.readlink('/proc/self/ns/net')==args.parent_netns:raise Hold('private net namespace required')
        namespace=os.readlink('/proc/self/ns/net')
        if 'NoNewPrivs:'+chr(9)+'1' not in Path('/proc/self/status').read_text():raise Hold('no-new-privileges required')
        ip=next((name for name in ('/usr/sbin/ip','/usr/bin/ip') if Path(name).is_file()),None)
        if ip is None:raise Hold('absolute ip executable missing')
        links=subprocess.check_output([ip,'-o','link','show'],env={'PATH':'/usr/bin:/bin'},text=True,timeout=5)
        if [line.split(':',2)[1].strip().split('@',1)[0] for line in links.splitlines() if line.strip()]!=['lo']:raise Hold('only loopback permitted')
        if any(os.environ.get(k) for k in ('GH_TOKEN','GITHUB_TOKEN','GH_CONFIG_DIR','SSH_AUTH_SOCK','OPENAI_API_KEY')):raise Hold('credential-bearing environment refused')
        source=args.source_root.resolve(strict=True);selected=SOURCES[args.variant]
        if sha((source/'SOURCE-CLOSURE.json').read_bytes())!=selected['closure_sha256']:raise Hold('external source freeze differs')
        for name,pin in selected['files_sha256'].items():
            if sha((source/name).read_bytes())!=pin:raise Hold('source bytes differ')
        with tempfile.TemporaryDirectory(prefix='scope-',dir='/tmp') as temporary:
            root=Path(temporary).resolve();root.chmod(0o700)
            labels=['current-only-no-target'] if args.variant=='baseline' else ['matching','current-only-no-target','missing-target','changed-current-A','changed-current-B','changed-current-H','changed-target-A','changed-target-B','changed-target-H','changed-target-revision','changed-command-args','stale-owner','missing-pin','wildcard']
            for label in labels:
                observed=run_case(source,args.variant,label,root);results.append(observed)
                (evidence/'observations.json').write_text(json.dumps(results,indent=2)+chr(10))
                if args.variant=='baseline':
                    if not observed['actual_Store_publish_observed']:raise Hold('baseline never reached actual Store.publish; public compatibility/lock or scope may have held; not assertion-red')
                    raise AssertionError('CURRENT_ONLY_SCOPE_WITHOUT_TYPED_TARGET_ACTUALLY_PUBLISHED')
                if label=='matching' and not observed['actual_Store_publish_observed']:raise Hold('exact positive never reached real Store.publish; later negative results would not prove gate rejection')
                if label!='matching' and (observed['actual_Store_publish_observed'] or not observed['tasks_unchanged']):raise AssertionError('UNAPPROVED_EXACT_TARGET_PUBLISHED:'+label)
        if os.readlink('/proc/self/ns/net')!=namespace:raise Hold('network namespace changed')
        verdict=dict(classification='candidate-reachable-exact-scope-selection-passed',overall_vertical_pass=False)
        code=0
    except AssertionError as error:
        verdict=dict(classification='actual-reached-behavior-assertion-red',reason=str(error),overall_vertical_pass=False);code=1
    except Exception as error:
        verdict=dict(classification='capability-HOLD',reason=type(error).__name__+': '+str(error)[:900],overall_vertical_pass=False);code=86
    (evidence/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));print(json.dumps(verdict));return code

if __name__=='__main__':raise SystemExit(main())
