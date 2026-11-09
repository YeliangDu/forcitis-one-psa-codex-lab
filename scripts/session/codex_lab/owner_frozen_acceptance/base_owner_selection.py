"""Independent reachable owner-transfer selection; no real owner/App Server.

Uses real public serve/call, two real CLI OS processes and real existing Store.
Transport lost-reply and hard crash are explicitly not covered by this matrix.
"""
import argparse
import copy
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import socket
import sqlite3
import stat
import subprocess
import sys
import tempfile
import time
import uuid

PINS={'barrier_cli.py': '486b603bc01479d260ea0b5793af4528f1f257ffb68324ff8d9d0146cf62dcd5', 'witness_core.py': 'd1882891214ba3557c61eb3c5e30902939ebd65422cf7dfe221c9854d4d7779a', 'owned_crash_fixture.py': 'a70cca5aba37acd27a42703339ac318e87e3dab844b0477552d4fe4b769abd28', 'scope_fixture.py': 'ca67e5d8e74408c0c87a7f30dbca8f6b2f26b8eec07deaaab3cc0a20f3df5b4f'}
SOURCE_SHA='5094eb8b0aa25e6dae12b3f658a74a5f865c29c14c9c02e427800378a0fa6287'
SOURCE_FILES={'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': '30e0aef43621e44b32bc6668a518ba1f01c1f224ed979390dc36962fed5a2a6e', 'handoff_retire.py': '72604a10fcd3fae7b860aa8c7f1b8663fc36a4b99d4ef943ee9b033b8e52e574', 'native_goal.py': 'af27b6e5ff5ecb6b035eb667d0e60a7ccf030121d9513f74375da2708d3b5419', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': '099996ae7bf7cc3e454764cb5fa13e1d0cf9880a133123b1e4dcdf21b37f6dd4', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': 'e648f29bb2c655b5d99a2b814da3302cae616ddc6a7e820f46c6ada0700402ae', 'orphan_terminal.py': '1ea4d501e5dd774ce01f10525ab91df1c2a72af25d00718dde65e96d2ee2abd5', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '4d0d4d4b9b67817602b0d440d4007048f3bdb7fa5c68270bd59e7c1f19d6d0e7', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': '9b22dc33e1b307d529ce323466b4c0be809b1630671d641a3e48706705965e66', 'runtime_broker.py': 'f4aa5d346d8383d921cafb66b9fa6e4af9a4073a2e68ce97cec746c258cd6f2f', 'runtime_store.py': '5320f1d5eaef9fe6c21471422b2944b8b0d7273b85c97e2cceb85191fde73dc0', 'warm_handoff.py': '6a69cde20d3900f45c92c87a8bcbcc5a70ad0fea635dbfe3f8be29eaa2f778f0', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64', 'exact_scope.py': 'ee29d8eaaa234655046f8d7a654d1179b506336a5cd08843f11380b253826b9d', 'startup_authority.py': 'd5ca117b82bda983ba1942f3933348a631c9e28b963929acdf10e7b34a0b8ca5', 'owner_transfer.py': '603c88758af59f103cb0709e4902e51baf77a31632cc19492da9b6adb24f6b7f'}
FIXTURE_SHA='b9ea684132b22e3cd0c0097a6edb7c37831d875658885a0453c7c4a22028a3c0'
FIXTURE_FILES={'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': 'e4a5157b78d278e64ac2345ab7b35a3c22da132603b2b3b8ce5f2563b55445df', 'handoff_retire.py': '8118be38d49376a0138ef88e2ba2373b18e4a871ee02d4bd6553a983af5933b7', 'native_goal.py': '5856c23ca25734ed7df2ef4f737f472229b1c875f344e8796be9115d935d2c89', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': 'fe5617f8bdfaa3d170585c84fe3c57c9ec39b1b954da801634dcf51f2c45dc45', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': '062c69a93055b5bded567311b538865a8a8e5f08ec4b570be8d4dff60cd2e969', 'orphan_terminal.py': '77f8e0adb5b0b2a358d7e3a6e5467143adaf4eb959f9da94e041ddf097fa0177', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '149d2e35deaf6265e8f111ada401ddf17ad9a28a0b61628f9bb0eeb01229df4e', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': 'fd2fe7bb9b3b19685e21c17aad5319122feffaab33b2d74ca66268a8b44c6928', 'runtime_broker.py': '4a8cad2cfdd67ca22328c92e798eb8608a8dd2f36c6568894c1b27c80e0fbf7c', 'runtime_store.py': '3990105d2eff4dafb40e01810734f065c1f81f9c9dec0e7ab49f6d95af8871db', 'warm_handoff.py': 'ec343e6391e15ec13a2be87c41a9bbbb2e8fdf353f55355d238625312c40bf93', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64', 'exact_scope.py': '9069901de3f64407bb947bf2d5bcba27c7087014a2f37993539a08527dc60a4e', 'startup_authority.py': '9c44d81ece8d399126542e9753fbb6818ebeaccfd198c2c272b002950b124b23'}
PACKAGE=Path(__file__).resolve().parent
class Hold(RuntimeError):pass
def sha(raw):return hashlib.sha256(raw).hexdigest()
def canonical(v):return json.dumps(v,sort_keys=True,separators=(',',':'),allow_nan=False)
def require(ok,label):
    if not ok:raise AssertionError(label)
def module(name,path,pin):
    if sha(path.read_bytes())!=pin:raise Hold('independent helper pin differs')
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
def identity(path):
    s=path.lstat();return dict(dev=s.st_dev,ino=s.st_ino,uid=s.st_uid,mode=stat.S_IMODE(s.st_mode),kind=stat.S_IFMT(s.st_mode),nlink=s.st_nlink,size=s.st_size,mtime_ns=s.st_mtime_ns,ctime_ns=s.st_ctime_ns)
def private(path,value):path.write_text(json.dumps(value));path.chmod(0o600);return sha(path.read_bytes())

class Case:
    def __init__(self,args,label):
        self.args=args;self.label=label;self.tmp=tempfile.TemporaryDirectory(prefix='lab-owner-',dir=args.runner_temp)
        self.root=Path(self.tmp.name).resolve();self.root.chmod(0o700)
        self.ledger=self.root/'ledger';self.ledger.mkdir(mode=0o700);(self.ledger/'broker.lock').write_bytes(b'');(self.ledger/'broker.lock').chmod(0o600)
        self.work=self.root/'work';self.work.mkdir(mode=0o700)
        endpoint=self.root/'endpoint';endpoint.mkdir(mode=0o700);alias_parent=self.root/'alias';alias_parent.mkdir(mode=0o700)
        self.native_fixture=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);target=endpoint/'s';self.native_fixture.bind(str(target));target.chmod(0o600);alias=alias_parent/'s';alias.symlink_to(target)
        self.sid=str(uuid.uuid4());self.auth=dict(client='scratch-old',generation=1,token=os.urandom(24).hex())
        self.current=dict(card=1,revision=1,role='one-lead',session_id=self.sid,physical_cwd=str(self.work),task='synthetic independent owner assignment',A=sha(b'A')[:40],B=sha(b'B')[:40],H=sha(b'H')[:40])
        self.values={name:dict(capability=os.urandom(32).hex(),token=os.urandom(32).hex()) for name in ('contender-a','contender-b')}
        self.f=module('_owner_shared_scope',PACKAGE/'scope_fixture.py',PINS['scope_fixture.py'])
        proxy=self.root/'proxy';proxy.symlink_to('/usr/bin/false')
        install=dict(native_endpoint=str(alias),native_session=self.sid,physical_cwd=str(self.work),role='one-lead',proxy_binary=str(proxy),gh_binary='/usr/bin/false',runtime_directory=str(self.ledger))
        self.install_path=self.root/'installation.json';self.installed=private(self.install_path,install)
        now=int(time.time());self.grant='transfer-'+uuid.uuid4().hex
        self.permit=dict(kind='lab-owner-transfer/1',grant_id=self.grant,broker_installation_sha256=self.installed,source_closure_sha256=SOURCE_SHA,
            role='one-lead',native_session=self.sid,physical_cwd=str(self.work),previous_owner=dict(client='scratch-old',generation=1),
            assignment=self.current,capability_epoch=2,candidates={name:dict(capability_sha256=sha(v['capability'].encode()),new_owner_token_sha256=sha(v['token'].encode())) for name,v in self.values.items()},
            not_before=now-1,expires_at=now+(12 if label=='expiry-renewal' else 600))
        self.permit_path=self.root/'transfer.json';self.permit_pin=private(self.permit_path,self.permit)
        scope=dict(kind='lab-runtime-exact-actions/2',broker_installation_sha256=self.installed,source_closure_sha256=SOURCE_SHA,
            actor=dict(kind='coordinator-owner',owner=dict(client='scratch-old',generation=1),role='one-lead',native_session=self.sid),
            assignment=self.current,task_phase='active',capability_epoch=1,
            grants=[dict(grant_id='fence-only',command='assignment.revoke',arguments={},target=dict(kind='fence-current-assignment-only',assignment=self.current,capability_epoch=1))])
        scope_path=self.root/'commands.json';scopepin=private(scope_path,scope)
        self.config=dict(schema='lab-private-runtime-binding/1',deployment_label='scratch-owner',source_closure_sha256=SOURCE_SHA,
            ledger=dict(directory=str(self.ledger),installation_sha256=self.installed),role=dict(name='one-lead',physical_cwd=str(self.work)),
            native=dict(expected_registered_session=self.sid,endpoint=dict(alias=self.f.record(alias),alias_parent=self.f.record(alias_parent),target=self.f.record(target),target_parent=self.f.record(endpoint),link_text=str(target)),
                        proxy=dict(path='/usr/bin/false',sha256=sha(Path('/usr/bin/false').read_bytes())),engine_epoch_reference=None),
            evidence_references={'broker-installation':dict(kind='authorization',path=str(self.install_path),sha256=self.installed),
                'owner-transfer':dict(kind='authorization',path=str(self.permit_path),sha256=self.permit_pin),
                'runtime-actions':dict(kind='authorization',path=str(scope_path),sha256=scopepin)})
        old=copy.deepcopy(self.config);old['source_closure_sha256']=FIXTURE_SHA;old_binding=self.root/'fixture-binding.json';old_pin=private(old_binding,old)
        setup=self.root/'seed.json';private(setup,dict(source_sha=FIXTURE_SHA,binding_path=str(old_binding),binding_sha=old_pin,auth=self.auth,current=self.current))
        childenv=dict(PATH='/usr/bin:/bin',HOME=str(self.root),LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1')
        result=subprocess.run([sys.executable,'-I','-S','-B',str(PACKAGE/'scope_fixture.py'),'--seed',str(setup),'--source-root',str(args.fixture_source_root)],cwd=self.root,env=childenv,capture_output=True,timeout=20)
        if result.returncode:raise Hold('prior real product schema fixture unavailable')
        self.db=self.ledger/'runtime.sqlite3'
        with sqlite3.connect(self.db) as db:
            if label not in ('blocked-unresolved-effect','blocked-live-turn','blocked-pending-wait'):
                db.execute("update tasks set phase='active',capability_epoch=1,fence_seq=null where revision=1")
            if label=='blocked-unresolved-effect':
                db.execute("insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,1,1,?,'git-push-cas','{}','unknown',1)",(uuid.uuid4().hex+uuid.uuid4().hex[:16],'synthetic-executable-unknown'))
            elif label=='blocked-live-turn':
                db.execute("insert into turns values(?,1,1,2,'active')",(str(uuid.uuid4()),))
            elif label=='blocked-pending-wait':
                first=db.execute('select turn_id from turns').fetchone()[0]
                db.execute("insert into waits values(?,?,1,1,?,?,?,?,'pending',0)",(sha(b'pending'),uuid.uuid4().hex,self.sid,first,'fixture-pending-call','fixture-pending-connection'))
        self.serial=0;self.binding=None;self.startup_binding()
        core=module('_owner_independent_witness',PACKAGE/'witness_core.py',PINS['witness_core.py'])
        secrets=[self.auth['token']]+[v[k] for v in self.values.values() for k in ('capability','token')]
        self.w=core.Witness(self.root,args.source_root,PACKAGE/'scope_fixture.py',PACKAGE/'owned_crash_fixture.py',secrets)
        try:self.w.start(self.binding)
        except BaseException:
            if self.w.child is not None and self.w.child.poll() is None:
                self.w.child.kill();self.w.child.wait(timeout=8)
            self.native_fixture.close();self.tmp.cleanup();raise
    def startup_binding(self,renewal=None):
        self.serial+=1
        with sqlite3.connect(self.db.as_uri()+'?mode=ro&immutable=1',uri=True) as db:
            owner=db.execute('select client,generation,paused from owner where id=1').fetchone()
            schema=db.execute("select type,name,tbl_name,sql from sqlite_schema where name!='sqlite_sequence' order by type,name,tbl_name").fetchall();version=db.execute('pragma user_version').fetchone()[0]
        startup=dict(kind='lab-existing-runtime-startup/1',source_closure_sha256=SOURCE_SHA,ledger_installation_sha256=self.installed,registered_session=self.sid,
            role='one-lead',physical_cwd=str(self.work),runtime_directory=str(self.ledger),runtime_identity=identity(self.ledger),lock_identity=identity(self.ledger/'broker.lock'),database_identity=identity(self.db),
            schema_sha256=sha(canonical([list(r) for r in schema]).encode()),user_version=version,owner=dict(client=owner[0],generation=owner[1],paused=True),
            operations=dict(storage='open-existing-schema-only',migrations='deny',initialization='deny',socket='exclusive-runtime-socket',startup_events='bounded-existing-constructor-audit'))
        path=self.root/('startup-'+str(self.serial)+'.json');pin=private(path,startup)
        config=copy.deepcopy(self.config);config['evidence_references']['runtime-startup']=dict(kind='authorization',path=str(path),sha256=pin)
        if renewal:config['evidence_references']['owner-transfer-renewal']=renewal
        self.binding=self.root/('binding-'+str(self.serial)+'.json');private(self.binding,config)
    def request(self,command,arguments=None,auth=None):
        self.serial+=1;path=self.root/('request-'+str(self.serial)+'.json');body=dict(command=command,arguments=arguments or {})
        if auth is not None:body['auth']=auth
        private(path,body);return path
    def call(self,command,arguments=None,auth=None):return self.w.call(self.binding,self.request(command,arguments,auth))
    def claim(self,client):
        v=self.values[client];return dict(grant_id=self.grant,client=client,capability=v['capability'],expected_generation=1,new_owner_token=v['token'])
    def status(self,client):return self.w.read_only_status(self.binding,self.request('owner.transfer-status',dict(grant_id=self.grant,client=client,capability=self.values[client]['capability'])))
    def stop(self):
        if self.w.child and self.w.child.poll() is None:self.w.controlled_stop()
    def close(self):
        try:self.stop()
        finally:self.native_fixture.close();self.tmp.cleanup()

def exercise(args,label):
    case=Case(args,label)
    try:
        w=case.w
        if label.startswith('blocked-'):
            before=w.snapshot();value=case.call('owner.transfer-release',dict(grant_id=case.grant),case.auth);after=w.snapshot()
            require(value.get('action')=='hold' and before==after,'BLOCKED_EXECUTION_STATE_RELEASED_OWNER')
            return dict(case=label,outcome='held-logical-state-unchanged',owner_changed=False)
        old=case.call('inspect',auth=case.auth);require(old.get('owner',{}).get('generation')==1,'OLD_AUTH_NOT_REAL')
        before=w.snapshot();require(case.call('owner.transfer-release',dict(grant_id=case.grant),case.auth).get('action')=='hold','RELEASE_BEFORE_FENCE');require(w.snapshot()==before,'PRE_FENCE_HOLD_MUTATED_STATE')
        revoked=case.call('assignment.revoke',{},case.auth);require(revoked['revoking_seq']<revoked['fenced_seq'],'REAL_FENCE_NOT_ORDERED')
        for key,value in [('token','0'*64),('generation',2)]:
            bad=dict(case.auth);bad[key]=value;before=w.snapshot();denied=case.call('owner.transfer-release',dict(grant_id=case.grant),bad);require(denied.get('action')=='hold' and before==w.snapshot(),'WRONG_OLD_AUTH_RELEASED')
        released=case.call('owner.transfer-release',dict(grant_id=case.grant),case.auth)
        if released.get('action')!='released' and time.time()>case.permit['expires_at']:raise Hold('fixture release window elapsed before actual release')
        require(released.get('action')=='released','VALID_FENCED_RELEASE_HELD')
        snapshot=w.snapshot();owner=snapshot['owner'][0]
        require(owner['client']=='scratch-old' and owner['generation']==1 and owner['paused']==1 and owner['token_sha'] is None,'OLD_OWNER_NOT_DURABLY_FENCED')
        require(case.call('inspect',auth=case.auth).get('action')=='hold','OLD_AUTH_VALID_AFTER_RELEASE')
        for mutation in ('unknown-client','wrong-capability','wrong-expected-generation'):
            claim=case.claim('contender-a')
            if mutation=='unknown-client':claim['client']='unapproved'
            elif mutation=='wrong-capability':claim['capability']='0'*64
            else:claim['expected_generation']=2
            before=w.snapshot();denied=case.call('owner.transfer-claim',claim);require(denied.get('action')=='hold' and before==w.snapshot(),'UNAPPROVED_CONTENDER_MUTATED_OWNER')
        case.status('contender-a')
        if label in ('released-controlled-restart','expiry-renewal'):
            if label=='expiry-renewal':
                while time.time()<=case.permit['expires_at']:time.sleep(.05)
                case.status('contender-a');before=w.snapshot();require(case.call('owner.transfer-claim',case.claim('contender-a')).get('action')=='hold' and before==w.snapshot(),'EXPIRED_PERMIT_CLAIMED')
            case.stop()
            renewal=None
            if label=='expiry-renewal':
                now=int(time.time());value=dict(kind='lab-owner-transfer-renewal/1',grant_id=case.grant,original_authorization_sha256=case.permit_pin,original_effect_id=released['effect_id'],
                    broker_installation_sha256=case.installed,source_closure_sha256=SOURCE_SHA,previous_owner=case.permit['previous_owner'],not_before=now-1,expires_at=now+120)
                path=case.root/'renewal.json';pin=private(path,value);renewal=dict(kind='authorization',path=str(path),sha256=pin)
            case.startup_binding(renewal);w.start(case.binding);case.status('contender-a')
        requests=[case.request('owner.transfer-claim',case.claim(client)) for client in ('contender-a','contender-b')]
        observed=w.race(case.binding,requests,PACKAGE/'barrier_cli.py',PINS['barrier_cli.py'])
        winner=observed['winner'];newauth=dict(client=winner,generation=2,token=case.values[winner]['token'])
        require(case.call('inspect',auth=newauth).get('owner',{}).get('generation')==2,'WINNER_PREPROVISIONED_AUTH_NOT_REAL')
        for command,arguments in [('resume',{}),('role.dispatch-current',dict(revision=1)),('assignment.publish',dict(assignment=dict(case.current,revision=2))),('writer',{}),('enqueue',{})]:
            before=w.snapshot();value=case.call(command,arguments,newauth);require(value.get('action')=='hold' and before==w.snapshot(),'OWNER_IDENTITY_BECAME_EXECUTION_AUTHORITY')
        case.status('contender-a');case.status('contender-b')
        final=w.snapshot();transfer=[e for e in final['effects'] if e['kind']=='owner-transfer'];require(len(transfer)==1 and transfer[0]['issue_count']==1,'SECOND_TRANSFER_CREATED')
        return dict(case=label,outcome='actual-release-and-two-CLI-single-CAS-observed',winner=winner,generation=2,paused=True,
                    controlled_restart=label!='same-process-race',independent_renewal=label=='expiry-renewal',claim_reply_fault_injected=False,
                    native_or_external_actions=0,audit=w.audit,history_unknown_archive_sample_present=False)
    finally:case.close()

def main():
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--source-root',required=True,type=Path);p.add_argument('--fixture-source-root',required=True,type=Path);p.add_argument('--runner-temp',required=True,type=Path);p.add_argument('--evidence-dir',required=True,type=Path);p.add_argument('--parent-netns',required=True);args=p.parse_args()
    args.source_root=args.source_root.resolve(strict=True);args.fixture_source_root=args.fixture_source_root.resolve(strict=True);args.runner_temp=args.runner_temp.resolve(strict=True);args.evidence_dir=args.evidence_dir.resolve(strict=True)
    if any(args.evidence_dir.iterdir()):raise Hold('fresh evidence directory required')
    os.environ['RUNNER_TEMP']=str(args.runner_temp);os.environ['HOST_NET']=args.parent_netns
    results=[]
    try:
        for root,pin,files in [(args.source_root,SOURCE_SHA,SOURCE_FILES),(args.fixture_source_root,FIXTURE_SHA,FIXTURE_FILES)]:
            if sha((root/'SOURCE-CLOSURE.json').read_bytes())!=pin:raise Hold('source closure differs')
            for n,d in files.items():
                if sha((root/n).read_bytes())!=d:raise Hold('source byte differs')
        for label in ['same-process-race','released-controlled-restart','expiry-renewal','blocked-unresolved-effect','blocked-live-turn','blocked-pending-wait']:
            results.append(exercise(args,label));(args.evidence_dir/'case-observations.json').write_text(json.dumps(results,indent=2)+chr(10))
        verdict=dict(classification='reachable-synthetic-owner-transfer-selection-passed',covered_cases=6,
                     lost_transport_reply_covered=False,hard_crash_covered=False,nonempty_orphan_history_covered=False,
                     actual_current_owner_affected=False,App_Server_involved=False,overall_vertical_pass=False);code=0
    except AssertionError:verdict=dict(classification='reached-owner-behavior-assertion-failure',covered_cases=len(results),overall_vertical_pass=False);code=1
    except Exception:verdict=dict(classification='capability-or-fixture-HOLD',completed_cases=len(results),overall_vertical_pass=False);code=86
    (args.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));print(json.dumps(verdict));return code
if __name__=='__main__':raise SystemExit(main())
